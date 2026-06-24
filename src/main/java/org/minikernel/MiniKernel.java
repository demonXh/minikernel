package org.minikernel;

import org.minikernel.core.interrupt.InterruptController;
import org.minikernel.core.interrupt.InterruptVector;
import org.minikernel.core.log.KLog;
import org.minikernel.core.time.KernelTimer;
import org.minikernel.hal.PhysicalMemory;
import org.minikernel.hal.VirtualClock;
import org.minikernel.hal.VirtualCpu;
import org.minikernel.kernel.sched.Scheduler;
import org.minikernel.kernel.sched.TaskStruct;

/**
 * Boot entry point of the mini kernel.
 *
 * <p>Wires the virtual hardware to the kernel core, installs default
 * interrupt handlers, bootstraps the scheduler with an idle + init task,
 * then starts the CPU. This is the Java equivalent of {@code start_kernel()}.
 */
public final class MiniKernel {

    private static final int DEFAULT_MAX_PIDS = 1024;

    private final InterruptController interrupts = new InterruptController();
    private final KernelTimer kernelTimer = new KernelTimer();
    private final PhysicalMemory memory;
    private final VirtualCpu cpu;
    private final VirtualClock clock;
    private final Scheduler scheduler;

    public MiniKernel(int ramBytes, long tickMillis) {
        this(ramBytes, tickMillis, DEFAULT_MAX_PIDS);
    }

    public MiniKernel(int ramBytes, long tickMillis, int maxPids) {
        this.memory = new PhysicalMemory(ramBytes);
        this.cpu = new VirtualCpu(0, interrupts);
        this.clock = new VirtualClock(cpu, tickMillis);
        this.scheduler = new Scheduler(maxPids);
        installDefaultHandlers();
    }

    public InterruptController interrupts() { return interrupts; }
    public KernelTimer kernelTimer() { return kernelTimer; }
    public PhysicalMemory memory() { return memory; }
    public VirtualCpu cpu() { return cpu; }
    public Scheduler scheduler() { return scheduler; }

    public void boot() {
        KLog.info("==== MiniKernel booting ====");
        cpu.start();
        clock.start();
        KLog.info("==== MiniKernel ready ====");
    }

    /** Boot the scheduler with the given init task body. Returns the init TaskStruct. */
    public TaskStruct startInit(Runnable initBody) {
        return scheduler.bootstrap(initBody);
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
            scheduler.onTimerTick();
            long j = kernelTimer.jiffies();
            if (j % 20 == 0) KLog.info("tick: jiffies=%d run_q=%d tasks=%d (cpu-%d)",
                    j, scheduler.runQueueSize(), scheduler.taskCount(), c.id());
        });
        interrupts.register(InterruptVector.SHUTDOWN, (vec, c) ->
                KLog.info("shutdown trap on cpu-%d", c.id()));
        interrupts.register(InterruptVector.PAGE_FAULT, (vec, c) ->
                KLog.warn("page fault on cpu-%d (no handler installed yet)", c.id()));
    }

    public static void main(String[] args) throws InterruptedException {
        MiniKernel kernel = new MiniKernel(64 * PhysicalMemory.PAGE_SIZE, 20);
        kernel.boot();
        Scheduler sched = kernel.scheduler();
        kernel.startInit(() -> {
            KLog.info("init: hello from pid=%d", sched.current().pid());
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                sched.fork("worker-" + idx, () -> {
                    KLog.info("worker-%d running on pid=%d", idx, sched.current().pid());
                    for (int k = 0; k < 5; k++) {
                        KLog.info("  worker-%d tick %d", idx, k);
                        sched.condResched();
                    }
                    sched.exit(idx);
                });
            }
            for (int i = 0; i < 3; i++) {
                long packed = sched.waitChild();
                int pid = (int) (packed >>> 32);
                int code = (int) packed;
                KLog.info("init: reaped pid=%d code=%d", pid, code);
            }
            KLog.info("init: all workers done");
            sched.exit(0);
        });
        Thread.sleep(2_000);
        kernel.shutdown();
    }
}
