package org.minikernel.kernel.sched;

/**
 * Lifecycle states of a task, mirroring Linux task states.
 *
 * <ul>
 *   <li>{@link #NEW}      - created but not yet on any run queue</li>
 *   <li>{@link #READY}    - on the run queue, waiting for the CPU (TASK_RUNNING in Linux when not on CPU)</li>
 *   <li>{@link #RUNNING}  - currently executing on a CPU</li>
 *   <li>{@link #BLOCKED}  - sleeping on a wait queue (TASK_INTERRUPTIBLE)</li>
 *   <li>{@link #ZOMBIE}   - exited; awaiting reap by parent's wait()</li>
 *   <li>{@link #DEAD}     - fully reaped, slot recyclable</li>
 * </ul>
 */
public enum TaskState {
    NEW,
    READY,
    RUNNING,
    BLOCKED,
    ZOMBIE,
    DEAD
}
