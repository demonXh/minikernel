package org.minikernel;

import org.minikernel.core.interrupt.InterruptController;
import org.minikernel.core.interrupt.InterruptVector;
import org.minikernel.core.log.KLog;
import org.minikernel.core.time.KernelTimer;
import org.minikernel.hal.PhysicalMemory;
import org.minikernel.hal.VirtualClock;
import org.minikernel.hal.VirtualCpu;
import org.minikernel.kernel.fs.VfsCore;
import org.minikernel.kernel.fs.ramfs.RamFs;
import org.minikernel.kernel.mm.BuddyAllocator;
import org.minikernel.kernel.mm.MmStruct;
import org.minikernel.kernel.mm.Mmu;
import org.minikernel.kernel.mm.PteFlags;
import org.minikernel.kernel.mm.VmAreaStruct;
import org.minikernel.kernel.net.Ipv4;
import org.minikernel.kernel.net.NetStack;
import org.minikernel.kernel.net.loopback.LoopbackNic;
import org.minikernel.kernel.sched.Scheduler;
import org.minikernel.kernel.sched.TaskStruct;
import org.minikernel.syscall.Syscalls;
import org.minikernel.syscall.SyscallTable;
import org.minikernel.syscall.Trap;
import org.minikernel.user.Libc;
import org.minikernel.user.WriteBuffer;

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
    private final SyscallTable syscalls;
    private final VfsCore vfs;
    private final NetStack netA;
    private final NetStack netB;

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
        // Reserve frame 0 as kernel scratch (used by user/WriteBuffer for syscall payloads).
        int scratch = buddy.allocFrame();
        if (scratch != 0) {
            throw new IllegalStateException("expected frame 0 for scratch, got " + scratch);
        }
        this.mmu = new Mmu(memory);
        this.cpu = new VirtualCpu(0, interrupts);
        this.clock = new VirtualClock(cpu, tickMillis);
        this.scheduler = new Scheduler(maxPids);
        this.syscalls = new SyscallTable(this);
        Syscalls.install(syscalls);
        this.vfs = new VfsCore();
        vfs.mountRoot(new RamFs());

        // Two virtual hosts on a bridged link.
        LoopbackNic nicA = new LoopbackNic("eth0",
                new byte[]{(byte)0x52,0,0,0,0,1});
        LoopbackNic nicB = new LoopbackNic("eth1",
                new byte[]{(byte)0x52,0,0,0,0,2});
        nicA.setPeer(nicB);
        nicB.setPeer(nicA);
        this.netA = new NetStack(interrupts, nicA, Ipv4.parse("10.0.0.1"));
        this.netB = new NetStack(interrupts, nicB, Ipv4.parse("10.0.0.2"));
        // Pre-load the peer's MAC into each ARP cache so the demo doesn't need ARP.
        netA.arp().learn(netB.hostIp(), nicB.macAddress());
        netB.arp().learn(netA.hostIp(), nicA.macAddress());

        Trap.bind(this);
        WriteBuffer.bind(this);
        installDefaultHandlers();
    }

    public InterruptController interrupts() { return interrupts; }
    public KernelTimer kernelTimer() { return kernelTimer; }
    public PhysicalMemory memory() { return memory; }
    public VirtualCpu cpu() { return cpu; }
    public Scheduler scheduler() { return scheduler; }
    public BuddyAllocator buddy() { return buddy; }
    public Mmu mmu() { return mmu; }
    public SyscallTable syscalls() { return syscalls; }
    public VfsCore vfs() { return vfs; }
    public NetStack netA() { return netA; }
    public NetStack netB() { return netB; }

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
        interrupts.register(InterruptVector.SYSCALL, (vec, c) -> Trap.onSyscallTrap(this, syscalls));
    }

    public static void main(String[] args) throws InterruptedException {
        MiniKernel kernel = new MiniKernel(64 * PhysicalMemory.PAGE_SIZE, 20);
        kernel.boot();
        int peerIp = Ipv4.parse("10.0.0.2");
        int peerPort = 7000;

        kernel.startInit(() -> {
            kernel.scheduler().current().setMm(kernel.newProcessMm());
            kernel.scheduler().current().setNetStack(kernel.netA());

            Libc.write(Libc.STDOUT, "init: net demo, pid=" + Libc.getpid());

            // Fork a child running on hostB: bind UDP 7000, recv, reply
            int childPid = Libc.fork(() -> {
                kernel.scheduler().current().setNetStack(kernel.netB());
                int sd = Libc.socket(Libc.SOCK_UDP);
                Libc.bind(sd, 7000);
                Libc.write(Libc.STDOUT, "server: bound :7000");
                String msg = Libc.recvfrom(sd, 256, 1000);
                int senderIp = Libc.lastSenderIp();
                int senderPort = Libc.lastSenderPort();
                Libc.write(Libc.STDOUT, "server: got '" + msg + "' from "
                        + Ipv4.format(senderIp) + ":" + senderPort);
                Libc.sendto(sd, ("pong:" + msg).getBytes(), senderIp, senderPort);
                Libc.exit(0);
            });

            Libc.sleep(50); // let server bind
            int clientSd = Libc.socket(Libc.SOCK_UDP);
            Libc.bind(clientSd, 5000);
            Libc.sendto(clientSd, "hello-net".getBytes(), peerIp, peerPort);
            Libc.write(Libc.STDOUT, "client: sent hello-net to 10.0.0.2:7000");
            String reply = Libc.recvfrom(clientSd, 256, 1000);
            Libc.write(Libc.STDOUT, "client: got reply '" + reply + "'");

            Libc.waitpid();
            Libc.write(Libc.STDOUT, "init: child pid=" + childPid + " done, bye");
            Libc.exit(0);
        });
        Thread.sleep(2_000);
        kernel.shutdown();
    }
}
