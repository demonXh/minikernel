package org.minikernel.syscall;

import org.minikernel.MiniKernel;
import org.minikernel.core.interrupt.InterruptVector;
import org.minikernel.hal.VirtualCpu;
import org.minikernel.kernel.sched.Scheduler;
import org.minikernel.kernel.sched.TaskStruct;

/**
 * The user/kernel-mode gateway. Issuing a syscall in this mini-kernel means:
 *
 * <ol>
 *   <li>User code calls {@link #syscall(int, long...)} from its carrier
 *       thread, while the CPU is logically in {@link VirtualCpu.Mode#USER}.</li>
 *   <li>This class flips the CPU to kernel mode (via {@code cpu.trap(SYSCALL)})
 *       and the registered handler inside {@link SyscallTable} runs.</li>
 *   <li>On return, the previous mode is restored.</li>
 * </ol>
 *
 * <p>The {@code SYSCALL} interrupt vector is reused as the trap number;
 * call arguments are passed via a thread-local mailbox, since this is Java
 * and we don't have hardware registers to encode them.
 */
public final class Trap {

    private static final ThreadLocal<int[]> TL_NUM = ThreadLocal.withInitial(() -> new int[1]);
    private static final ThreadLocal<long[]> TL_ARGS = ThreadLocal.withInitial(() -> new long[6]);
    private static final ThreadLocal<long[]> TL_RET = ThreadLocal.withInitial(() -> new long[1]);

    private static volatile MiniKernel boundKernel;

    private Trap() {}

    /** Called once at boot to attach a kernel to {@link Trap}. */
    public static void bind(MiniKernel kernel) {
        boundKernel = kernel;
    }

    /**
     * User-space facing API. Equivalent to inline {@code syscall} on Linux.
     * The varargs are widened to long. Returns the syscall's long result.
     */
    public static long syscall(int number, long... args) {
        MiniKernel k = boundKernel;
        if (k == null) throw new IllegalStateException("Trap is not bound to a kernel");

        int[] numSlot = TL_NUM.get();
        long[] argSlot = TL_ARGS.get();
        long[] retSlot = TL_RET.get();
        numSlot[0] = number;
        int n = Math.min(args.length, argSlot.length);
        for (int i = 0; i < n; i++) argSlot[i] = args[i];
        for (int i = n; i < argSlot.length; i++) argSlot[i] = 0L;

        // Trap: switch CPU to kernel mode and dispatch.
        k.cpu().trap(InterruptVector.SYSCALL.code());

        return retSlot[0];
    }

    /**
     * Internal handler bound to {@link InterruptVector#SYSCALL}; invoked by
     * the CPU's {@code trap()} after it has flipped to kernel mode.
     */
    public static void onSyscallTrap(MiniKernel kernel, SyscallTable table) {
        int num = TL_NUM.get()[0];
        long[] args = TL_ARGS.get();
        TaskStruct caller = Scheduler.currentOnThread();
        long ret = table.dispatch(num, caller, args);
        TL_RET.get()[0] = ret;
    }
}
