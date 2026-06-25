package org.minikernel.syscall;

import org.minikernel.core.log.KLog;
import org.minikernel.kernel.fs.Dentry;
import org.minikernel.kernel.fs.FdTable;
import org.minikernel.kernel.fs.File;
import org.minikernel.kernel.fs.VfsCore;
import org.minikernel.kernel.mm.MmStruct;
import org.minikernel.kernel.mm.Mmu;
import org.minikernel.kernel.mm.SegfaultException;
import org.minikernel.kernel.net.Datagram;
import org.minikernel.kernel.net.NetStack;
import org.minikernel.kernel.net.SocketTable;
import org.minikernel.kernel.net.SocketType;
import org.minikernel.kernel.net.UdpSocket;
import org.minikernel.kernel.sched.Scheduler;
import org.minikernel.kernel.sched.TaskStruct;

/**
 * Default implementations of the kernel's system calls. Wired into a
 * {@link SyscallTable} by {@link #install(SyscallTable)}. For paths and
 * small strings, user code parks raw bytes at a fixed kernel-scratch frame
 * (see {@code user.WriteBuffer}) and passes that physical address as vaddr.
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
        table.register(SyscallNumber.OPEN,    Syscalls::sysOpen);
        table.register(SyscallNumber.CLOSE,   Syscalls::sysClose);
        table.register(SyscallNumber.LSEEK,   Syscalls::sysLseek);
        table.register(SyscallNumber.MKDIR,   Syscalls::sysMkdir);
        table.register(SyscallNumber.UNLINK,  Syscalls::sysUnlink);
        table.register(SyscallNumber.SOCKET,  Syscalls::sysSocket);
        table.register(SyscallNumber.BIND,    Syscalls::sysBind);
        table.register(SyscallNumber.SENDTO,  Syscalls::sysSendto);
        table.register(SyscallNumber.RECVFROM,Syscalls::sysRecvfrom);
    }

    static long sysRead(SyscallContext ctx) {
        int fd = ctx.argInt(0);
        long bufVaddr = ctx.arg(1);
        int len = ctx.argInt(2);
        if (len < 0) return Errno.fail(Errno.EINVAL);
        if (len == 0) return 0L;
        TaskStruct t = ctx.caller();
        if (t == null) return Errno.fail(Errno.EFAULT);
        File f = t.fdTable().get(fd);
        if (f == null) return Errno.fail(Errno.EBADF);
        byte[] buf = new byte[len];
        int n;
        try { n = f.read(buf, 0, len); }
        catch (IllegalStateException e) { return Errno.fail(Errno.EBADF); }
        if (n <= 0) return 0L;
        try { writeUserBuffer(ctx, bufVaddr, buf, n); }
        catch (SegfaultException e) { return Errno.fail(Errno.EFAULT); }
        return n;
    }

    static long sysWrite(SyscallContext ctx) {
        int fd = ctx.argInt(0);
        long bufVaddr = ctx.arg(1);
        int len = ctx.argInt(2);
        if (len < 0) return Errno.fail(Errno.EINVAL);
        if (len == 0) return 0L;
        TaskStruct task = ctx.caller();
        if (task == null) return Errno.fail(Errno.EFAULT);
        byte[] payload;
        try { payload = readUserBuffer(ctx, bufVaddr, len); }
        catch (SegfaultException e) { return Errno.fail(Errno.EFAULT); }
        if (fd == 1 || fd == 2) {
            KLog.info("[fd=%d pid=%d] %s", fd, task.pid(), new String(payload));
            return len;
        }
        File f = task.fdTable().get(fd);
        if (f == null) return Errno.fail(Errno.EBADF);
        try { return f.write(payload, 0, len); }
        catch (IllegalStateException e) { return Errno.fail(Errno.EBADF); }
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
        TaskStruct t = ctx.caller();
        if (t != null) {
            FdTable tbl = t.fdTable();
            for (int fd = 0; fd < tbl.capacity(); fd++) {
                File f = tbl.remove(fd);
                if (f != null) f.release();
            }
        }
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

    static long sysOpen(SyscallContext ctx) {
        long pathVaddr = ctx.arg(0);
        int flags = ctx.argInt(1);
        TaskStruct t = ctx.caller();
        if (t == null) return Errno.fail(Errno.EFAULT);
        String path;
        try { path = readUserString(ctx, pathVaddr); }
        catch (SegfaultException e) { return Errno.fail(Errno.EFAULT); }
        VfsCore vfs = ctx.kernel().vfs();
        File f = vfs.open(path, flags);
        if (f == null) return Errno.fail(Errno.ENOENT);
        return t.fdTable().install(f);
    }

    static long sysClose(SyscallContext ctx) {
        int fd = ctx.argInt(0);
        TaskStruct t = ctx.caller();
        if (t == null) return Errno.fail(Errno.EFAULT);
        File f = t.fdTable().remove(fd);
        if (f == null) return Errno.fail(Errno.EBADF);
        f.release();
        return 0L;
    }

    static long sysLseek(SyscallContext ctx) {
        int fd = ctx.argInt(0);
        long offset = ctx.arg(1);
        int whence = ctx.argInt(2);
        TaskStruct t = ctx.caller();
        if (t == null) return Errno.fail(Errno.EFAULT);
        File f = t.fdTable().get(fd);
        if (f == null) return Errno.fail(Errno.EBADF);
        long newOff;
        switch (whence) {
            case 0: newOff = offset; break;
            case 1: newOff = f.offset() + offset; break;
            case 2: newOff = f.inode().size() + offset; break;
            default: return Errno.fail(Errno.EINVAL);
        }
        if (newOff < 0) return Errno.fail(Errno.EINVAL);
        f.setOffset(newOff);
        return newOff;
    }

    static long sysMkdir(SyscallContext ctx) {
        long pathVaddr = ctx.arg(0);
        int mode = ctx.argInt(1);
        String path;
        try { path = readUserString(ctx, pathVaddr); }
        catch (SegfaultException e) { return Errno.fail(Errno.EFAULT); }
        Dentry d = ctx.kernel().vfs().mkdir(path, mode);
        return d == null ? Errno.fail(Errno.EINVAL) : 0L;
    }

    static long sysUnlink(SyscallContext ctx) {
        long pathVaddr = ctx.arg(0);
        String path;
        try { path = readUserString(ctx, pathVaddr); }
        catch (SegfaultException e) { return Errno.fail(Errno.EFAULT); }
        return ctx.kernel().vfs().unlink(path) ? 0L : Errno.fail(Errno.ENOENT);
    }

    /* ---------------- networking ---------------- */

    static long sysSocket(SyscallContext ctx) {
        int type = ctx.argInt(0); // 0=UDP, 1=TCP (TCP unimplemented for now)
        TaskStruct t = ctx.caller();
        if (t == null) return Errno.fail(Errno.EFAULT);
        if (type != SocketType.UDP.ordinal()) return Errno.fail(Errno.EINVAL);
        UdpSocket s = new UdpSocket();
        return t.sockTable().install(s);
    }

    static long sysBind(SyscallContext ctx) {
        int handle = ctx.argInt(0);
        int port = ctx.argInt(1);
        TaskStruct t = ctx.caller();
        if (t == null) return Errno.fail(Errno.EFAULT);
        UdpSocket s = t.sockTable().get(handle);
        if (s == null) return Errno.fail(Errno.EBADF);
        NetStack ns = t.netStack();
        if (ns == null) return Errno.fail(Errno.EINVAL);
        return ns.bindUdp(s, port) ? 0L : Errno.fail(Errno.EINVAL);
    }

    static long sysSendto(SyscallContext ctx) {
        int handle = ctx.argInt(0);
        long bufVaddr = ctx.arg(1);
        int len = ctx.argInt(2);
        int dstIp = ctx.argInt(3);
        int dstPort = ctx.argInt(4);
        TaskStruct t = ctx.caller();
        if (t == null) return Errno.fail(Errno.EFAULT);
        UdpSocket s = t.sockTable().get(handle);
        if (s == null) return Errno.fail(Errno.EBADF);
        NetStack ns = t.netStack();
        if (ns == null) return Errno.fail(Errno.EINVAL);
        // Auto-bind to an ephemeral port if not already bound.
        if (!s.isBound()) ns.bindUdp(s, allocEphemeralPort(ns));
        byte[] payload;
        try { payload = readUserBuffer(ctx, bufVaddr, len); }
        catch (SegfaultException e) { return Errno.fail(Errno.EFAULT); }
        ns.sendUdp(dstIp, s.localPort(), dstPort, payload);
        return len;
    }

    static long sysRecvfrom(SyscallContext ctx) {
        int handle = ctx.argInt(0);
        long bufVaddr = ctx.arg(1);
        int len = ctx.argInt(2);
        long timeoutMs = ctx.arg(3);
        TaskStruct t = ctx.caller();
        if (t == null) return Errno.fail(Errno.EFAULT);
        UdpSocket s = t.sockTable().get(handle);
        if (s == null) return Errno.fail(Errno.EBADF);
        // Cooperative wait: poll the queue while yielding the CPU so other
        // tasks (notably the sender, possibly on the same single-CPU model)
        // can run and softirqd can deliver the packet.
        long deadline = (timeoutMs < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + timeoutMs;
        Datagram d = s.tryReceive();
        while (d == null && System.currentTimeMillis() < deadline) {
            ctx.kernel().scheduler().yieldCpu();
            try { Thread.sleep(1); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            d = s.tryReceive();
        }
        if (d == null) return Errno.fail(Errno.EINTR);
        int n = Math.min(len, d.payload.length);
        try { writeUserBuffer(ctx, bufVaddr, d.payload, n); }
        catch (SegfaultException e) { return Errno.fail(Errno.EFAULT); }
        LastDatagram.set(d.srcIp, d.srcPort);
        return n;
    }

    private static int allocEphemeralPort(NetStack ns) {
        for (int p = 49152; p < 65535; p++) {
            if (ns.lookupUdp(p) == null) return p;
        }
        return 0;
    }

    // ----- helpers for copying buffers across the user/kernel boundary -----

    private static byte[] readUserBuffer(SyscallContext ctx, long vaddr, int len) {
        TaskStruct t = ctx.caller();
        MmStruct mm = t == null ? null : t.mm();
        if (mm != null && vaddr >= 0x10000L) {
            return ctx.kernel().mmu().readBytes(mm, vaddr, len);
        }
        byte[] out = new byte[len];
        ctx.kernel().memory().readBytes(vaddr, out, 0, len);
        return out;
    }

    private static void writeUserBuffer(SyscallContext ctx, long vaddr, byte[] src, int len) {
        TaskStruct t = ctx.caller();
        MmStruct mm = t == null ? null : t.mm();
        if (mm != null && vaddr >= 0x10000L) {
            for (int i = 0; i < len; i++) ctx.kernel().mmu().writeByte(mm, vaddr + i, src[i]);
            return;
        }
        ctx.kernel().memory().writeBytes(vaddr, src, 0, len);
    }

    private static String readUserString(SyscallContext ctx, long vaddr) {
        // C-string: read up to a NUL terminator or 4 KiB, whichever comes first.
        TaskStruct t = ctx.caller();
        MmStruct mm = t == null ? null : t.mm();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < 4096; i++) {
            byte b;
            if (mm != null && vaddr >= 0x10000L) b = ctx.kernel().mmu().readByte(mm, vaddr + i);
            else b = ctx.kernel().memory().readByte(vaddr + i);
            if (b == 0) break;
            out.write(b & 0xFF);
        }
        return out.toString();
    }
}
