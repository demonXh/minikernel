package org.minikernel;

import org.minikernel.core.interrupt.InterruptController;
import org.minikernel.core.interrupt.InterruptVector;
import org.minikernel.core.log.KLog;
import org.minikernel.core.time.KernelTimer;
import org.minikernel.hal.PhysicalMemory;
import org.minikernel.hal.VirtualClock;
import org.minikernel.hal.VirtualCpu;
import org.minikernel.kernel.mm.BuddyAllocator;
import org.minikernel.kernel.mm.MmStruct;
import org.minikernel.kernel.mm.Mmu;
import org.minikernel.kernel.mm.PteFlags;
import org.minikernel.kernel.mm.VmAreaStruct;
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
    private final BuddyAllocator buddy;
    private final Mmu mmu;

    public MiniKernel(int ramBytes, long tickMillis) {
        this(ramBytes, tickMillis, DEFAULT_MAX_PIDS);
    }

    public MiniKernel(int ramBytes, long tickMillis, int maxPids) {
        this.memory = new PhysicalMemory(ramBytes);
        int totalFrames = ramBytes / PhysicalMemory.PAGE_SIZE;
        if (Integer.bitCount(totalFrames) != 1) {
            throw new IllegalArgumentException("ramBytes / PAGE_SIZE must be a power of 2 (got " + totalFrames + ")");
        }
        int maxOrder = Integer.numberOfTrailingZeros(totalFrames);
        this.buddy = new BuddyAllocator(memory, 0, totalFrames, maxOrder);
        this.mmu = new Mmu(memory);
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
    public BuddyAllocator buddy() { return buddy; }
    public Mmu mmu() { return mmu; }

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

    /** Build a fresh address space for a user task with default text/data/heap/stack VMAs. */
    public MmStruct newProcessMm() {
        MmStruct mm = new MmStruct(buddy);
        int rw = PteFlags.WRITE | PteFlags.USER;
        int rx = PteFlags.USER;
        // Layout (toy): text 0x00400000+4 pages RO, data +4 pages RW, heap +4 pages RW, stack at 0x7FFFC000 4 pages RW
        long PAGE = PhysicalMemory.PAGE_SIZE;
        mm.addVma(new VmAreaStruct(0x00400000L, 0x00400000L + 4 * PAGE, rx, VmAreaStruct.Kind.TEXT));
        mm.addVma(new VmAreaStruct(0x00500000L, 0x00500000L + 4 * PAGE, rw, VmAreaStruct.Kind.DATA));
        mm.addVma(new VmAreaStruct(0x00600000L, 0x00600000L + 4 * PAGE, rw, VmAreaStruct.Kind.HEAP));
        mm.addVma(new VmAreaStruct(0x7FFFC000L, 0x7FFFC000L + 4 * PAGE, rw, VmAreaStruct.Kind.STACK));
        return mm;
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
            if (j % 20 == 0) KLog.info("tick: jiffies=%d run_q=%d tasks=%d free=%dB (cpu-%d)",
                    j, scheduler.runQueueSize(), scheduler.taskCount(), buddy.totalFreeBytes(), c.id());
        });
        interrupts.register(InterruptVector.SHUTDOWN, (vec, c) ->
                KLog.info("shutdown trap on cpu-%d", c.id()));
        interrupts.register(InterruptVector.PAGE_FAULT, (vec, c) -> {
            TaskStruct curr = scheduler.current();
            KLog.warn("page fault on cpu-%d pid=%s (resolved inline by Mmu)",
                    c.id(), curr == null ? "?" : curr.pid());
        });
    }

    public static void main(String[] args) throws InterruptedException {
        MiniKernel kernel = new MiniKernel(64 * PhysicalMemory.PAGE_SIZE, 20);
        kernel.boot();
        Scheduler sched = kernel.scheduler();
        kernel.startInit(() -> {
            KLog.info("init: hello from pid=%d", sched.current().pid());

            // Equip init with its own address space and demand-page through it
            MmStruct mm = kernel.newProcessMm();
            sched.current().setMm(mm);
            long heap = 0x00600000L;
            kernel.mmu().writeBytes(mm, heap, "hello mm!".getBytes());
            byte[] back = kernel.mmu().readBytes(mm, heap, 9);
            KLog.info("init: heap roundtrip = '%s'", new String(back));

            for (int i = 0; i < 3; i++) {
                final int idx = i;
                sched.fork("worker-" + idx, () -> {
                    MmStruct childMm = kernel.newProcessMm();
                    sched.current().setMm(childMm);
                    long stack = 0x7FFFC000L + 16;
                    kernel.mmu().writeByte(childMm, stack, (byte) (0x40 + idx));
                    byte b = kernel.mmu().readByte(childMm, stack);
                    KLog.info("worker-%d (pid=%d) wrote 0x%x to stack",
                            idx, sched.current().pid(), b & 0xff);
                    sched.exit(idx);
                });
            }
            for (int i = 0; i < 3; i++) {
                long packed = sched.waitChild();
                KLog.info("init: reaped pid=%d code=%d", (int) (packed >>> 32), (int) packed);
            }
            KLog.info("init: all workers done");
            sched.exit(0);
        });
        Thread.sleep(2_000);
        kernel.shutdown();
    }
}
