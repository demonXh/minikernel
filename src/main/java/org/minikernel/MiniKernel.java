package org.minikernel;

import org.minikernel.core.interrupt.InterruptController;
import org.minikernel.core.interrupt.InterruptVector;
import org.minikernel.core.log.KLog;
import org.minikernel.core.time.KernelTimer;
import org.minikernel.hal.PhysicalMemory;
import org.minikernel.hal.VirtualClock;
import org.minikernel.hal.VirtualCpu;

/**
 * Boot entry point of the mini kernel.
 *
 * <p>Wires the virtual hardware to the kernel core, installs default
 * interrupt handlers, then starts the CPU and lets the system run for a
 * short interval before initiating an orderly shutdown. This is the Java
 * equivalent of {@code start_kernel()}.
 */
public final class MiniKernel {

    private final InterruptController interrupts = new InterruptController();
    private final KernelTimer kernelTimer = new KernelTimer();
    private final PhysicalMemory memory;
    private final VirtualCpu cpu;
    private final VirtualClock clock;

    public MiniKernel(int ramBytes, long tickMillis) {
        this.memory = new PhysicalMemory(ramBytes);
        this.cpu = new VirtualCpu(0, interrupts);
        this.clock = new VirtualClock(cpu, tickMillis);
        installDefaultHandlers();
    }

    public InterruptController interrupts() { return interrupts; }
    public KernelTimer kernelTimer() { return kernelTimer; }
    public PhysicalMemory memory() { return memory; }
    public VirtualCpu cpu() { return cpu; }

    public void boot() {
        KLog.info("==== MiniKernel booting ====");
        cpu.start();
        clock.start();
        KLog.info("==== MiniKernel ready ====");
    }

    public void shutdown() {
        KLog.info("==== MiniKernel shutting down ====");
        clock.stop();
        cpu.halt();
        interrupts.shutdown();
        KLog.info("==== MiniKernel halted ====");
    }

    private void installDefaultHandlers() {
        interrupts.register(InterruptVector.TIMER, (vec, c) -> {
            kernelTimer.onTick();
            long j = kernelTimer.jiffies();
            if (j % 10 == 0) KLog.info("tick: jiffies=%d (cpu-%d)", j, c.id());
        });
        interrupts.register(InterruptVector.SHUTDOWN, (vec, c) ->
                KLog.info("shutdown trap on cpu-%d", c.id()));
        interrupts.register(InterruptVector.PAGE_FAULT, (vec, c) ->
                KLog.warn("page fault on cpu-%d (no handler installed yet)", c.id()));
    }

    public static void main(String[] args) throws InterruptedException {
        MiniKernel kernel = new MiniKernel(64 * PhysicalMemory.PAGE_SIZE, 50);
        kernel.boot();
        Thread.sleep(1_000);
        kernel.shutdown();
    }
}
