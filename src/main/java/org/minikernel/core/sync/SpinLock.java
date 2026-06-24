package org.minikernel.core.sync;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test-and-set spinlock, equivalent to Linux {@code spinlock_t} on a single
 * cache line. Holders busy-wait until the lock is free.
 *
 * <p>Do NOT use this to protect long critical sections; prefer
 * {@link Semaphore} or future kernel mutexes for that. Intended for short,
 * non-sleepable code paths such as interrupt-context bookkeeping.
 */
public final class SpinLock {

    private final AtomicBoolean locked = new AtomicBoolean(false);

    public void lock() {
        while (!locked.compareAndSet(false, true)) {
            Thread.onSpinWait();
        }
    }

    public boolean tryLock() {
        return locked.compareAndSet(false, true);
    }

    public void unlock() {
        if (!locked.compareAndSet(true, false)) {
            throw new IllegalStateException("unlock of an unlocked SpinLock");
        }
    }

    public boolean isLocked() { return locked.get(); }
}
