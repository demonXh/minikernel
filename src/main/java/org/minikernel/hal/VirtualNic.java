package org.minikernel.hal;

/**
 * Network interface card abstraction; placeholder for iteration 6 (net stack).
 *
 * <p>Operates on raw link-layer frames as opaque byte arrays. Iteration 6
 * will define an Ethernet frame structure and connect two NICs back-to-back
 * to form a virtual loopback link.
 */
public interface VirtualNic {

    /** Push a frame onto the transmit queue. */
    void transmit(byte[] frame);

    /** Pop the next received frame, or {@code null} if the queue is empty. */
    byte[] receive();

    /** MAC address, 6 bytes. */
    byte[] macAddress();
}
