package org.minikernel.syscall;

import org.minikernel.MiniKernel;
import org.minikernel.kernel.sched.TaskStruct;

/**
 * Context handed to a {@link SyscallHandler}. Bundles the kernel handle,
 * the calling task, and the up-to-six syscall arguments. Analogous to the
 * {@code struct pt_regs} a Linux syscall handler receives.
 */
public final class SyscallContext {

    private final MiniKernel kernel;
    private final TaskStruct caller;
    private final long[] args;

    public SyscallContext(MiniKernel kernel, TaskStruct caller, long[] args) {
        this.kernel = kernel;
        this.caller = caller;
        this.args = args;
    }

    public MiniKernel kernel() { return kernel; }
    public TaskStruct caller() { return caller; }

    public long arg(int i) { return i < args.length ? args[i] : 0L; }
    public int argInt(int i) { return (int) arg(i); }
}
