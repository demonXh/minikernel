package org.minikernel.hal;

import org.minikernel.core.interrupt.InterruptVector;
import org.minikernel.core.log.KLog;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Periodic clock source that raises {@link InterruptVector#TIMER} on a
 * target CPU at a fixed frequency, mirroring the role of the local APIC
 * timer / HPET on real hardware.
 *
 * <p>The interval is configurable; in iteration 4+ this will drive the
 * scheduler tick that performs preemption.
 */
public final class VirtualClock {

    private final long intervalMillis;
    private final VirtualCpu cpu;
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> task;

    public VirtualClock(VirtualCpu cpu, long intervalMillis) {
        if (intervalMillis <= 0) throw new IllegalArgumentException("intervalMillis must be > 0");
        this.cpu = cpu;
        this.intervalMillis = intervalMillis;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "virtual-clock");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() {
        if (task != null) return;
        KLog.info("VirtualClock: ticking every %d ms", intervalMillis);
        task = executor.scheduleAtFixedRate(this::tick, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        executor.shutdownNow();
        KLog.info("VirtualClock: stopped");
    }

    private void tick() {
        try {
            cpu.trap(InterruptVector.TIMER.code());
        } catch (Throwable t) {
            KLog.error("clock tick handler threw: %s", t);
        }
    }
}
