package org.minikernel.kernel.net.loopback;

import org.minikernel.hal.VirtualNic;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * In-memory virtual NIC.
 *
 * <p>Transmit pushes a frame to an optional <em>peer's</em> receive queue
 * (set via {@link #setPeer(LoopbackNic)}); receive simply drains this NIC's
 * own queue. A {@link #setRxCallback(Consumer)} hook lets the network
 * stack be notified when a frame arrives, mirroring the IRQ a real NIC
 * would raise for {@code NETWORK_RX}.
 */
public final class LoopbackNic implements VirtualNic {

    private final String name;
    private final byte[] mac;
    private final ConcurrentLinkedQueue<byte[]> rxQueue = new ConcurrentLinkedQueue<>();
    private LoopbackNic peer;
    private Consumer<byte[]> rxCallback;

    public LoopbackNic(String name, byte[] mac) {
        if (mac.length != 6) throw new IllegalArgumentException("MAC must be 6 bytes");
        this.name = name;
        this.mac = mac.clone();
    }

    public String name() { return name; }

    public void setPeer(LoopbackNic peer) { this.peer = peer; }

    public void setRxCallback(Consumer<byte[]> cb) { this.rxCallback = cb; }

    @Override
    public void transmit(byte[] frame) {
        if (peer == null) {
            // No peer: loop back to self (single-host configuration).
            enqueueRx(frame);
        } else {
            peer.enqueueRx(frame);
        }
    }

    @Override
    public byte[] receive() {
        return rxQueue.poll();
    }

    @Override
    public byte[] macAddress() { return mac.clone(); }

    public int rxQueueSize() { return rxQueue.size(); }

    void enqueueRx(byte[] frame) {
        rxQueue.offer(frame);
        Consumer<byte[]> cb = rxCallback;
        if (cb != null) cb.accept(frame);
    }
}
