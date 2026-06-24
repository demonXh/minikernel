package org.minikernel.kernel.sched;

import org.minikernel.core.log.KLog;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cooperative round-robin scheduler.
 *
 * <p>Java cannot truly preempt a thread mid-instruction safely, so we model
 * the scheduler as a gate-driven cooperative design:
 * <ol>
 *   <li>Every task's carrier thread starts blocked on its
 *       {@link TaskStruct#gate} semaphore. The scheduler grants the CPU by
 *       releasing one permit on the chosen task's gate.</li>
 *   <li>A running task must periodically call {@link #condResched()} or
 *       {@link #yieldCpu()}; at those points the timer-IRQ-set
 *       needResched flag is observed and a context switch may happen.</li>
 *   <li>The "context switch" is: release next.gate, then have the current
 *       carrier block on current.gate.acquire().</li>
 * </ol>
 *
 * <p>An idle task (PID 0, "swapper") guarantees there is always something
 * runnable, matching Linux's design.
 */
public final class Scheduler {

    private final PidAllocator pids;
    private final RunQueue rq = new RunQueue();
    private final Map<Integer, TaskStruct> tasks = new ConcurrentHashMap<>();
    private final AtomicReference<TaskStruct> current = new AtomicReference<>();
    private TaskStruct idle;
    private final ConcurrentLinkedQueue<TaskStruct> waiters = new ConcurrentLinkedQueue<>();
    private final Object waitLock = new Object();
    private static final ThreadLocal<TaskStruct> CURRENT_TL = new ThreadLocal<>();

    public Scheduler(int maxPids) {
        this.pids = new PidAllocator(maxPids);
    }

    public TaskStruct bootstrap(Runnable initBody) {
        if (idle != null) throw new IllegalStateException("scheduler already bootstrapped");
        idle = new TaskStruct(PidAllocator.IDLE_PID, "swapper", null, this::idleLoop);
        tasks.put(idle.pid(), idle);
        startCarrier(idle);
        idle.setState(TaskState.RUNNING);
        current.set(idle);
        idle.gate.release();
        return spawn("init", null, initBody);
    }

    public TaskStruct spawn(String name, TaskStruct parent, Runnable body) {
        TaskStruct p = parent != null ? parent : current.get();
        int pid = pids.alloc();
        TaskStruct t = new TaskStruct(pid, name, p, body);
        if (p != null) p.children().add(t);
        tasks.put(pid, t);
        startCarrier(t);
        t.setState(TaskState.READY);
        rq.enqueue(t);
        KLog.debug("spawn pid=%d name=%s parent=%s", pid, name, p == null ? "-" : String.valueOf(p.pid()));
        return t;
    }

    public TaskStruct fork(String childName, Runnable childBody) {
        return spawn(childName, null, childBody);
    }

    public void condResched() {
        TaskStruct curr = current.get();
        if (curr != null && curr.needResched.getAndSet(false)) {
            schedule(false);
        }
    }

    public void yieldCpu() {
        schedule(false);
    }

    public void block() {
        TaskStruct curr = current.get();
        if (curr == null) return;
        curr.setState(TaskState.BLOCKED);
        schedule(true);
    }

    public synchronized void wake(TaskStruct t) {
        if (t.state() != TaskState.BLOCKED) return;
        t.setState(TaskState.READY);
        rq.enqueue(t);
    }

    public void exit(int code) {
        TaskStruct curr = current.get();
        if (curr == null || curr == idle) throw new IllegalStateException("cannot exit idle/null");
        curr.setExitCode(code);
        synchronized (waitLock) {
            curr.setState(TaskState.ZOMBIE);
            for (Iterator<TaskStruct> it = waiters.iterator(); it.hasNext(); ) {
                TaskStruct p = it.next();
                if (p == curr.parent()) {
                    it.remove();
                    if (p.state() == TaskState.BLOCKED) {
                        p.setState(TaskState.READY);
                        rq.enqueue(p);
                    }
                    break;
                }
            }
        }
        KLog.debug("exit pid=%d code=%d", curr.pid(), code);
        schedule(true);
        throw new TaskExitException();
    }

    public long waitChild() {
        TaskStruct curr = current.get();
        if (curr == null) throw new IllegalStateException("no current task");
        while (true) {
            synchronized (waitLock) {
                TaskStruct zombie = findZombieChild(curr);
                if (zombie != null) {
                    long packed = ((long) zombie.pid() << 32) | (zombie.exitCode() & 0xFFFFFFFFL);
                    reap(zombie);
                    return packed;
                }
                if (curr.children().isEmpty()) return -1L;
                boolean allDead = true;
                for (TaskStruct c : curr.children()) if (c.state() != TaskState.DEAD) { allDead = false; break; }
                if (allDead) return -1L;
                waiters.offer(curr);
                curr.setState(TaskState.BLOCKED);
            }
            schedule(true);
        }
    }

    private TaskStruct findZombieChild(TaskStruct p) {
        for (TaskStruct c : p.children()) {
            if (c.state() == TaskState.ZOMBIE) return c;
        }
        return null;
    }

    private void reap(TaskStruct z) {
        z.setState(TaskState.DEAD);
        tasks.remove(z.pid());
        pids.release(z.pid());
        if (z.carrier() != null) z.carrier().interrupt();
        KLog.debug("reap pid=%d", z.pid());
    }

    public void onTimerTick() {
        TaskStruct curr = current.get();
        if (curr != null) {
            curr.ticksOnCpu++;
            if (curr != idle) curr.needResched.set(true);
        }
    }

    public TaskStruct current() { return current.get(); }
    public TaskStruct idle() { return idle; }
    public TaskStruct findByPid(int pid) { return tasks.get(pid); }
    public int runQueueSize() { return rq.size(); }
    public int taskCount() { return tasks.size(); }
    public static TaskStruct currentOnThread() { return CURRENT_TL.get(); }

    private void schedule(boolean dontRequeueCurrent) {
        TaskStruct curr;
        TaskStruct next;
        synchronized (this) {
            curr = current.get();
            next = rq.pickNext();
            if (next == null) next = idle;
            if (next == curr) return;
            if (!dontRequeueCurrent && curr != null && curr != idle && curr.state() == TaskState.RUNNING) {
                curr.setState(TaskState.READY);
                rq.enqueue(curr);
            }
            next.setState(TaskState.RUNNING);
            current.set(next);
        }
        next.gate.release();
        if (curr != null) {
            try {
                curr.gate.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void startCarrier(TaskStruct t) {
        Thread carrier = new Thread(() -> runCarrier(t), "task-" + t.pid() + "-" + t.name());
        carrier.setDaemon(true);
        t.setCarrier(carrier);
        carrier.start();
    }

    private void runCarrier(TaskStruct t) {
        CURRENT_TL.set(t);
        try {
            t.gate.acquire();
        } catch (InterruptedException e) {
            return;
        }
        if (t.state() == TaskState.DEAD) return;
        try {
            t.body().run();
            if (t != idle && t.state() == TaskState.RUNNING) {
                exit(0);
            }
        } catch (TaskExitException e) {
            // expected: exit() throws this to unwind the carrier stack
        } catch (Throwable err) {
            KLog.error("task pid=%d crashed: %s", t.pid(), err);
            if (t != idle) {
                try { exit(-1); } catch (Throwable ignored) {}
            }
        } finally {
            CURRENT_TL.remove();
        }
    }

    private void idleLoop() {
        while (true) {
            yieldCpu();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
