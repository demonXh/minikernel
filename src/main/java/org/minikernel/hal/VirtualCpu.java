package org.minikernel.hal;

import org.minikernel.core.interrupt.InterruptController;
import org.minikernel.core.interrupt.InterruptVector;
import org.minikernel.core.log.KLog;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A virtual CPU core, modelled by a single JVM thread that runs an endless
 * fetch-execute loop until halted.
 *
 * <p>For iteration 1 we do NOT implement an instruction set: the loop simply
 * idles ("HLT") and yields to interrupts. The privilege level field is the
 * Java analogue of x86's CPL: it flips to {@link Mode#KERNEL} on interrupt
 * entry and is restored on return. Future iterations will plug a scheduler
 * into the interrupt path so that timer ticks can preempt the running task.
 */
public final class VirtualCpu {

    public enum Mode { USER, KERNEL }

    private final int cpuId;
    private final Registers regs = new Registers();
    private volatile Mode mode = Mode.KERNEL;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final InterruptController interrupts;
    private Thread thread;

    public VirtualCpu(int cpuId, InterruptController interrupts) {
        this.cpuId = cpuId;
        this.interrupts = interrupts;
    }

    public int id() { return cpuId; }
    public Registers registers() { return regs; }
    public Mode mode() { return mode; }
    public boolean isRunning() { return running.get(); }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) return;
        thread = new Thread(this::run, "cpu-" + cpuId);
        thread.setDaemon(true);
        thread.start();
        KLog.info("cpu-%d started", cpuId);
    }

    public synchronized void halt() {
        if (!running.compareAndSet(true, false)) return;
        if (thread != null) thread.interrupt();
        KLog.info("cpu-%d halted", cpuId);
    }

    /**
     * Trap entry. Switches to kernel mode, dispatches the interrupt, then
     * restores the previous mode. Called from the timer thread (or, later,
     * from user-mode code via syscall).
     */
    public void trap(int vector) {
        Mode previous = mode;
        mode = Mode.KERNEL;
        try {
            interrupts.dispatch(vector, this);
        } finally {
            mode = previous;
        }
    }

    private void run() {
        while (running.get()) {
            try {
                // Idle: in a real CPU this would be the HLT instruction
                // waiting for the next interrupt. We sleep briefly to avoid
                // busy-spinning the host JVM.
                Thread.sleep(10);
            } catch (InterruptedException e) {
                if (!running.get()) break;
                Thread.currentThread().interrupt();
            }
        }
        // On halt deliver a synthetic shutdown interrupt for cleanup hooks.
        interrupts.dispatch(InterruptVector.SHUTDOWN.code(), this);
    }
}
