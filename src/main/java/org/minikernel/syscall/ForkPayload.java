package org.minikernel.syscall;

/**
 * Thread-local payload used to pass a {@link Runnable} body across the
 * fork syscall boundary. Since the toy model has no instruction stream to
 * "copy" like Linux does for fork(), we let user code stash the child's
 * entry function here right before issuing the FORK trap.
 */
public final class ForkPayload {

    private static final ThreadLocal<Runnable> TL = new ThreadLocal<>();

    private ForkPayload() {}

    public static void stage(Runnable body) { TL.set(body); }

    public static Runnable consume() {
        Runnable r = TL.get();
        TL.remove();
        return r;
    }
}
