package org.minikernel.kernel.sched;

import org.minikernel.core.ds.ListHead;
import org.minikernel.hal.Registers;
import org.minikernel.kernel.mm.MmStruct;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Task Control Block, the Java analogue of Linux's {@code struct task_struct}.
 *
 * <p>Each task owns a carrier {@link Thread} that runs its body, plus a
 * 0-permit "gate" semaphore used to model context switching: a task can
 * only execute when its gate has a permit; switching out means draining
 * its gate, switching in means releasing one. This is the closest we can
 * get to "park the CPU here" without touching unsafe APIs like
 * {@code Thread.suspend()}.
 *
 * <p>Beyond scheduling state, the PCB carries:
 * <ul>
 *   <li>a snapshot of {@link Registers} (kept for inspection; the body runs
 *       as Java code rather than emulated instructions)</li>
 *   <li>a parent / children tree for {@code fork}/{@code wait}</li>
 *   <li>an exit-code slot and a "reaped" flag for ZOMBIE handling</li>
 * </ul>
 */
public final class TaskStruct {

    private final int pid;
    private final String name;
    private volatile TaskState state;
    private volatile int exitCode = 0;

    private final TaskStruct parent;
    private final List<TaskStruct> children = new ArrayList<>();

    /** Saved register context, updated on every voluntary yield. */
    private final Registers regs = new Registers();

    /** The Java code that this task executes; analogous to user-mode binary. */
    private final Runnable body;

    /** Carrier thread; populated when the task is dispatched. */
    private Thread carrier;

    /** Gate semaphore: holds 1 permit iff this task is currently entitled to run. */
    final Semaphore gate = new Semaphore(0);

    /** Set by timer IRQ to request a reschedule at the next yield point. */
    final AtomicBoolean needResched = new AtomicBoolean(false);

    /** Embedded link node for the run-queue and other intrusive lists. */
    public final ListHead<TaskStruct> runNode = new ListHead<>(this);

    /** Accumulated CPU time slices (just a counter, for stats). */
    long ticksOnCpu = 0;

    /** Memory descriptor; null for kernel threads (idle, etc.). */
    private MmStruct mm;

    public TaskStruct(int pid, String name, TaskStruct parent, Runnable body) {
        this.pid = pid;
        this.name = name;
        this.parent = parent;
        this.body = body;
        this.state = TaskState.NEW;
    }

    public int pid() { return pid; }
    public String name() { return name; }
    public TaskState state() { return state; }
    public void setState(TaskState s) { this.state = s; }
    public TaskStruct parent() { return parent; }
    public List<TaskStruct> children() { return children; }
    public Registers registers() { return regs; }
    public Runnable body() { return body; }
    public Thread carrier() { return carrier; }
    public void setCarrier(Thread t) { this.carrier = t; }
    public int exitCode() { return exitCode; }
    public void setExitCode(int code) { this.exitCode = code; }
    public long ticksOnCpu() { return ticksOnCpu; }

    public MmStruct mm() { return mm; }
    public void setMm(MmStruct mm) { this.mm = mm; }

    @Override
    public String toString() {
        return "Task{pid=" + pid + ", name=" + name + ", state=" + state + '}';
    }
}
