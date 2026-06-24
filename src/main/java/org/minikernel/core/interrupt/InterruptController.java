package org.minikernel.core.interrupt;

import org.minikernel.core.log.KLog;
import org.minikernel.hal.VirtualCpu;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Interrupt dispatcher.
 *
 * <p>Holds a fixed-size table indexed by vector number and routes hardware
 * traps to their registered top-half handler. A separate softirq queue is
 * drained by a dedicated thread, mirroring Linux's {@code ksoftirqd}.
 */
public final class InterruptController {

    private final InterruptHandler[] table;
    private final ConcurrentLinkedQueue<Runnable> softirqs = new ConcurrentLinkedQueue<>();
    private final ExecutorService softirqd;
    private volatile boolean running = true;

    public InterruptController() {
        this.table = new InterruptHandler[InterruptVector.maxCode() + 1];
        this.softirqd = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ksoftirqd");
            t.setDaemon(true);
            return t;
        });
        softirqd.submit(this::softirqLoop);
    }

    public void register(InterruptVector vector, InterruptHandler handler) {
        register(vector.code(), handler);
    }

    public void register(int vector, InterruptHandler handler) {
        checkVector(vector);
        table[vector] = handler;
    }

    public void dispatch(int vector, VirtualCpu cpu) {
        checkVector(vector);
        InterruptHandler h = table[vector];
        if (h == null) {
            KLog.warn("unhandled IRQ %d on cpu-%d", vector, cpu.id());
            return;
        }
        try {
            h.handle(vector, cpu);
        } catch (Throwable t) {
            KLog.error("IRQ %d handler threw: %s", vector, t);
        }
    }

    /** Defer work to the softirq thread (bottom half). */
    public void raiseSoftIrq(Runnable work) {
        if (work != null) softirqs.offer(work);
    }

    public void shutdown() {
        running = false;
        softirqd.shutdownNow();
    }

    private void softirqLoop() {
        while (running) {
            Runnable r = softirqs.poll();
            if (r == null) {
                try { Thread.sleep(1); } catch (InterruptedException e) { return; }
                continue;
            }
            try { r.run(); } catch (Throwable t) { KLog.error("softirq threw: %s", t); }
        }
    }

    private void checkVector(int v) {
        if (v < 0 || v >= table.length) {
            throw new IndexOutOfBoundsException("vector " + v + " out of range");
        }
    }
}
