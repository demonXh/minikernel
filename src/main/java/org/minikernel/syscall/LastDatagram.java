package org.minikernel.syscall;

/**
 * Thread-local pair holding the source address of the most recent
 * RECVFROM result. A real Linux syscall fills a {@code sockaddr*} the
 * caller passes in; since we don't model pointer arguments richly, we
 * stash the last source address on the calling thread and let libc fetch
 * it via {@link #lastSrcIp()} / {@link #lastSrcPort()} after recvfrom.
 */
public final class LastDatagram {

    private static final ThreadLocal<int[]> TL = ThreadLocal.withInitial(() -> new int[2]);

    private LastDatagram() {}

    public static void set(int srcIp, int srcPort) {
        int[] slot = TL.get();
        slot[0] = srcIp;
        slot[1] = srcPort;
    }

    public static int lastSrcIp() { return TL.get()[0]; }
    public static int lastSrcPort() { return TL.get()[1]; }
}
