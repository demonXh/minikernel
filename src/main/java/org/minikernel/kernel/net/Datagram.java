package org.minikernel.kernel.net;

import java.util.Objects;

/** Datagram delivered from the protocol stack to a UDP socket. */
public final class Datagram {

    public final int srcIp;
    public final int srcPort;
    public final int dstIp;
    public final int dstPort;
    public final byte[] payload;

    public Datagram(int srcIp, int srcPort, int dstIp, int dstPort, byte[] payload) {
        this.srcIp = srcIp;
        this.srcPort = srcPort;
        this.dstIp = dstIp;
        this.dstPort = dstPort;
        this.payload = payload;
    }

    @Override
    public String toString() {
        return String.format("Datagram{%s:%d -> %s:%d, %d bytes}",
                Ipv4.format(srcIp), srcPort, Ipv4.format(dstIp), dstPort, payload.length);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Datagram)) return false;
        Datagram d = (Datagram) o;
        return srcIp == d.srcIp && srcPort == d.srcPort && dstIp == d.dstIp && dstPort == d.dstPort
                && java.util.Arrays.equals(payload, d.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(srcIp, srcPort, dstIp, dstPort, java.util.Arrays.hashCode(payload));
    }
}
