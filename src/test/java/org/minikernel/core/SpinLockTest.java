package org.minikernel.core;

import org.junit.jupiter.api.Test;
import org.minikernel.core.sync.SpinLock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SpinLockTest {

    @Test
    void basicLockUnlock() {
        SpinLock lock = new SpinLock();
        assertFalse(lock.isLocked());
        lock.lock();
        assertTrue(lock.isLocked());
        lock.unlock();
        assertFalse(lock.isLocked());
    }

    @Test
    void tryLockFailsWhenHeld() {
        SpinLock lock = new SpinLock();
        lock.lock();
        try {
            assertFalse(lock.tryLock());
        } finally {
            lock.unlock();
        }
        assertTrue(lock.tryLock());
        lock.unlock();
    }

    @Test
    void doubleUnlockRejected() {
        SpinLock lock = new SpinLock();
        lock.lock();
        lock.unlock();
        assertThrows(IllegalStateException.class, lock::unlock);
    }

    @Test
    void mutualExclusionUnderContention() throws InterruptedException {
        SpinLock lock = new SpinLock();
        AtomicInteger counter = new AtomicInteger();
        int threads = 8;
        int iters = 5_000;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                for (int j = 0; j < iters; j++) {
                    lock.lock();
                    try { counter.set(counter.get() + 1); } finally { lock.unlock(); }
                }
                done.countDown();
            }).start();
        }
        start.countDown();
        done.await();
        assertEquals(threads * iters, counter.get());
    }
}
