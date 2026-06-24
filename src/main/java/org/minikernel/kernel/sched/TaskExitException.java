package org.minikernel.kernel.sched;

/**
 * Sentinel thrown by {@link Scheduler#exit(int)} when a task's carrier
 * thread resumes after being marked ZOMBIE / DEAD. Its sole purpose is to
 * unwind the user-mode call stack all the way up to {@code runCarrier},
 * which catches it and ends the carrier thread.
 *
 * <p>Intermediate frames (syscall dispatcher, interrupt controller, etc.)
 * must <b>rethrow</b> this exception rather than swallow it.
 */
public final class TaskExitException extends RuntimeException {

    public TaskExitException() { super("task exiting"); }
}
