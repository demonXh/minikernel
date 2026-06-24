package org.minikernel.kernel.sched;

import org.minikernel.core.ds.Bitmap;

/**
 * PID allocator backed by a {@link Bitmap}.
 *
 * <p>PID 0 is reserved for the swapper / idle task, exactly like Linux. The
 * first user-allocatable PID is 1 (the init task). Reuse policy is
 * first-fit, matching the behavior of {@code alloc_pid()} on a fresh boot.
 */
public final class PidAllocator {

    public static final int IDLE_PID = 0;
    public static final int INIT_PID = 1;

    private final Bitmap bitmap;

    public PidAllocator(int maxPids) {
        if (maxPids <= INIT_PID) throw new IllegalArgumentException("maxPids must be > " + INIT_PID);
        this.bitmap = new Bitmap(maxPids);
        this.bitmap.set(IDLE_PID);
    }

    public synchronized int alloc() {
        int pid = bitmap.allocFirstFree();
        if (pid < 0) throw new IllegalStateException("out of PIDs");
        return pid;
    }

    public synchronized void release(int pid) {
        if (pid == IDLE_PID) throw new IllegalArgumentException("cannot release IDLE pid");
        bitmap.clear(pid);
    }

    public synchronized boolean inUse(int pid) { return bitmap.get(pid); }
}
