package org.minikernel.core.interrupt;

/**
 * Well-known interrupt / exception vectors.
 *
 * <p>Values are arbitrary small integers; only their identity matters. We
 * use an enum (rather than naked int constants) so the dispatch table can
 * be size-checked at boot and the names appear in stack traces.
 */
public enum InterruptVector {

    TIMER(0),
    SYSCALL(1),
    PAGE_FAULT(2),
    DISK_IO(3),
    NETWORK_RX(4),
    SHUTDOWN(255);

    private final int code;

    InterruptVector(int code) { this.code = code; }

    public int code() { return code; }

    public static int maxCode() { return SHUTDOWN.code; }
}
