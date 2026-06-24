package org.minikernel.syscall;

import org.minikernel.MiniKernel;
import org.minikernel.core.log.KLog;
import org.minikernel.kernel.sched.TaskStruct;

/**
 * System call dispatcher: indexed lookup from number to {@link SyscallHandler}.
 *
 * <p>This is the only legitimate gateway into kernel services. User code is
 * expected to reach the kernel exclusively through
 * {@link Trap#syscall(int, long...)}, which traps into the CPU's kernel
 * mode and ultimately invokes {@link #dispatch}.
 */
public final class SyscallTable {

    private final MiniKernel kernel;
    private final SyscallHandler[] table;

    public SyscallTable(MiniKernel kernel) {
        this.kernel = kernel;
        this.table = new SyscallHandler[SyscallNumber.maxNumber() + 1];
    }

    public void register(SyscallNumber n, SyscallHandler h) {
        table[n.number()] = h;
    }

    public long dispatch(int number, TaskStruct caller, long[] args) {
        if (number < 0 || number >= table.length || table[number] == null) {
            KLog.warn("ENOSYS syscall=%d pid=%s", number, caller == null ? "?" : caller.pid());
            return Errno.fail(Errno.ENOSYS);
        }
        SyscallContext ctx = new SyscallContext(kernel, caller, args == null ? new long[0] : args);
        try {
            return table[number].handle(ctx);
        } catch (org.minikernel.kernel.sched.TaskExitException e) {
            // EXIT syscall: rethrow so the carrier thread unwinds.
            throw e;
        } catch (Throwable t) {
            KLog.error("syscall %d (%s) threw: %s", number, safeName(number), t);
            return Errno.fail(Errno.EINVAL);
        }
    }

    private static String safeName(int n) {
        try { return SyscallNumber.of(n).name(); } catch (Exception e) { return "?"; }
    }
}
