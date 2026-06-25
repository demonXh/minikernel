package org.minikernel.kernel.net;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Kernel-side state of a UDP socket. Holds the binding (local ip+port) and
 * a receive queue of inbound datagrams. Mirrors the role of
 * {@code struct sock} + the UDP-specific receive queue in Linux.
 */
public final class UdpSocket {

    private int localIp;       // 0 = ANY
    private int localPort;     // 0 = unbound
    private boolean bound;
    private final LinkedBlockingQueue<Datagram> rxQueue = new LinkedBlockingQueue<>();

    public synchronized boolean bind(int ip, int port) {
        if (bound) return false;
        if (port <= 0 || port > 65535) return false;
        this.localIp = ip;
        this.localPort = port;
        this.bound = true;
        return true;
    }

    public synchronized boolean isBound() { return bound; }
    public synchronized int localIp() { return localIp; }
    public synchronized int localPort() { return localPort; }

    /** Called by NetStack when a datagram destined for this port arrives. */
    public void enqueue(Datagram d) { rxQueue.offer(d); }

    /** Non-blocking poll; returns null if no datagram is available. */
    public Datagram tryReceive() { return rxQueue.poll(); }

    /** Blocking receive (poll up to {@code timeoutMs}; <0 means forever). */
    public Datagram receive(long timeoutMs) {
        try {
            if (timeoutMs < 0) return rxQueue.take();
            return rxQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public int queueSize() { return rxQueue.size(); }
}
