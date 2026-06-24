package org.minikernel.core.time;

import org.minikernel.core.log.KLog;

import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kernel-side software timer service.
 *
 * <p>Holds a min-heap of pending callbacks keyed by absolute jiffy count.
 * Driven externally by the timer-interrupt handler, which must call
 * {@link #onTick()} once per tick. This mirrors the Linux per-CPU timer
 * wheel (simplified to a single global heap for clarity).
 */
public final class KernelTimer {

    /** Monotonic tick counter (jiffies). */
    private final AtomicLong jiffies = new AtomicLong(0);

    private static final class Entry implements Comparable<Entry> {
        final long deadline;
        final Runnable action;
        Entry(long deadline, Runnable action) { this.deadline = deadline; this.action = action; }
        @Override public int compareTo(Entry o) { return Long.compare(this.deadline, o.deadline); }
    }

    private final PriorityQueue<Entry> heap = new PriorityQueue<>();

    public long jiffies() { return jiffies.get(); }

    /** Schedule {@code action} to fire after {@code ticksFromNow} ticks. */
    public synchronized void scheduleIn(long ticksFromNow, Runnable action) {
        if (ticksFromNow < 0) throw new IllegalArgumentException("ticksFromNow must be >= 0");
        heap.offer(new Entry(jiffies.get() + ticksFromNow, action));
    }

    /** Called by the timer ISR. Advances jiffies and fires due callbacks. */
    public void onTick() {
        long now = jiffies.incrementAndGet();
        while (true) {
            Entry head;
            synchronized (this) {
                head = heap.peek();
                if (head == null || head.deadline > now) break;
                heap.poll();
            }
            try { head.action.run(); } catch (Throwable t) { KLog.error("kernel timer action threw: %s", t); }
        }
    }

    public synchronized int pending() { return heap.size(); }
}
