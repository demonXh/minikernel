package org.minikernel.core.sync;

/**
 * Counting semaphore with kernel-style {@code down}/{@code up} naming.
 *
 * <p>Backed by {@link java.util.concurrent.Semaphore} but exposes only the
 * subset of operations a Linux driver would expect, so call sites stay
 * idiomatic.
 */
public final class Semaphore {

    private final java.util.concurrent.Semaphore impl;

    public Semaphore(int permits) {
        this.impl = new java.util.concurrent.Semaphore(permits);
    }

    /** Acquire one permit, blocking if none are available. */
    public void down() throws InterruptedException {
        impl.acquire();
    }

    /** Acquire one permit without blocking. */
    public boolean tryDown() {
        return impl.tryAcquire();
    }

    /** Release one permit. */
    public void up() {
        impl.release();
    }

    public int available() { return impl.availablePermits(); }
}
