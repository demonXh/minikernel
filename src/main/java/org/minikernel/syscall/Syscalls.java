package org.minikernel.syscall;

import org.minikernel.MiniKernel;
import org.minikernel.core.log.KLog;
import org.minikernel.kernel.mm.MmStruct;
import org.minikernel.kernel.mm.Mmu;
import org.minikernel.kernel.mm.SegfaultException;
import org.minikernel.kernel.sched.Scheduler;
import org.minikernel.kernel.sched.TaskStruct;

/**
 * Default implementations of the kernel's system calls.
 *
 * <p>Each method is wired into a {@link SyscallTable} by
 * {@link #install(SyscallTable)}; the table-driven design lets us add or
 * override syscalls without touching the dispatcher.
 *
 * <p>Argument conventions (Linux-style: longs, errno on failure):
 * <pre>
 *   READ    (fd, vaddr_buf, len)      - not implemented until iteration 5
 *   WRITE   (fd, vaddr_buf, len)      - fd 1/2 -> console via KLog
 *   GETPID  ()                        - returns caller pid
 *   FORK    (vaddr_entry?)            - child runs same body as parent (toy)
 *   EXIT    (code)                    - never returns
 *   WAITPID (any)                     - returns packed pid|code or -ECHILD
 *   BRK     (delta_pages)             - extends heap area, returns new top
 *   SLEEP   (millis)                  - busy yield until elapsed
 *   YIELD   ()                        - voluntarily give up CPU
 * </pre>
 */
public final class Syscalls {

    private Syscalls() {}

    public static void install(SyscallTable table) {
        table.register(SyscallNumber.READ,    Syscalls::sysRead);
        table.register(SyscallNumber.WRITE,   Syscalls::sysWrite);
        table.register(SyscallNumber.GETPID,  Syscalls::sysGetpid);
        table.register(SyscallNumber.FORK,    Syscalls::sysFork);
        table.register(SyscallNumber.EXIT,    Syscalls::sysExit);
        table.register(SyscallNumber.WAITPID, Syscalls::sysWaitpid);
        table.register(SyscallNumber.BRK,     Syscalls::sysBrk);
        table.register(SyscallNumber.SLEEP,   Syscalls::sysSleep);
        table.register(SyscallNumber.YIELD,   Syscalls::sysYield);
    }

    static long sysRead(SyscallContext ctx) {
        return Errno.fail(Errno.ENOSYS);
    }

    static long sysWrite(SyscallContext ctx) {
        int fd = ctx.argInt(0);
        long bufVaddr = ctx.arg(1);
        int len = ctx.argInt(2);
        if (fd != 1 && fd != 2) return Errno.fail(Errno.EBADF);
        if (len < 0) return Errno.fail(Errno.EINVAL);
        if (len == 0) return 0L;
        TaskStruct task = ctx.caller();
        if (task == null) return Errno.fail(Errno.EFAULT);
        MmStruct mm = task.mm();
        try {
            byte[] payload;
            if (mm != null && bufVaddr >= 0x10000L) {
                Mmu mmu = ctx.kernel().mmu();
                payload = mmu.readBytes(mm, bufVaddr, len);
            } else {
                // No MM yet: treat bufVaddr as a host-side byte[] reference index.
                // For our toy model we read directly from physical memory.
                payload = new byte[len];
                ctx.kernel().memory().readBytes(bufVaddr, payload, 0, len);
            }
            KLog.info("[fd=%d pid=%d] %s", fd, task.pid(), new String(payload));
            return len;
        } catch (SegfaultException e) {
            return Errno.fail(Errno.EFAULT);
        }
    }

    static long sysGetpid(SyscallContext ctx) {
        TaskStruct t = ctx.caller();
        return t == null ? Errno.fail(Errno.EPERM) : t.pid();
    }

    /**
     * Fork semantics in this toy kernel: a child task is spawned which
     * re-runs the body provided by the parent at fork time. We pass that
     * body via a thread-local because we have no proper trampoline.
     */
    static long sysFork(SyscallContext ctx) {
        Runnable childBody = ForkPayload.consume();
        if (childBody == null) return Errno.fail(Errno.EINVAL);
        TaskStruct caller = ctx.caller();
        Scheduler sched = ctx.kernel().scheduler();
        TaskStruct child = sched.spawn("child-of-" + (caller == null ? "?" : caller.pid()), caller, childBody);
        // Equip child with its own MM (zero-filled VMA layout).
        child.setMm(ctx.kernel().newProcessMm());
        return child.pid();
    }

    static long sysExit(SyscallContext ctx) {
        int code = ctx.argInt(0);
        ctx.kernel().scheduler().exit(code);
        return 0L; // unreachable
    }

    static long sysWaitpid(SyscallContext ctx) {
        long packed = ctx.kernel().scheduler().waitChild();
        if (packed == -1L) return Errno.fail(Errno.ECHILD);
        return packed;
    }

    /**
     * Simplified brk: argument is the number of additional pages to fault
     * in starting at the heap base. Returns the highest mapped vaddr+1,
     * or -ENOMEM on failure.
     */
    static long sysBrk(SyscallContext ctx) {
        int extraPages = ctx.argInt(0);
        TaskStruct t = ctx.caller();
        if (t == null || t.mm() == null) return Errno.fail(Errno.EFAULT);
        long heapBase = 0x00600000L;
        Mmu mmu = ctx.kernel().mmu();
        try {
            long top = heapBase;
            for (int i = 0; i < extraPages; i++) {
                // touch first byte of each page to force demand-paging
                mmu.writeByte(t.mm(), heapBase + (long) i * 4096, (byte) 0);
                top = heapBase + ((long) i + 1) * 4096;
            }
            return top;
        } catch (SegfaultException e) {
            return Errno.fail(Errno.ENOMEM);
        }
    }

    static long sysSleep(SyscallContext ctx) {
        long ms = ctx.arg(0);
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            ctx.kernel().scheduler().yieldCpu();
            try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return 0L;
    }

    static long sysYield(SyscallContext ctx) {
        ctx.kernel().scheduler().yieldCpu();
        return 0L;
    }
}
