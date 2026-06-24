package org.minikernel.core.sync;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Wait queue: a place for tasks to park themselves while waiting on an
 * event. Equivalent to Linux's {@code wait_queue_head_t}.
 *
 * <p>In iteration 1 we don't have task objects yet, so this stores opaque
 * waiters as {@code Object}. Iteration 2 will refine it to enqueue
 * {@code TaskStruct} references and wake them by changing their state.
 */
public final class WaitQueue {

    private final Deque<Object> waiters = new ArrayDeque<>();

    public synchronized void enqueue(Object waiter) {
        waiters.addLast(waiter);
    }

    public synchronized Object wakeOne() {
        return waiters.pollFirst();
    }

    public synchronized int wakeAll() {
        int n = waiters.size();
        waiters.clear();
        return n;
    }

    public synchronized int size() { return waiters.size(); }
}
