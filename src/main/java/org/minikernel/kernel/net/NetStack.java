package org.minikernel.kernel.net;

import org.minikernel.core.interrupt.InterruptController;
import org.minikernel.core.log.KLog;
import org.minikernel.kernel.net.loopback.LoopbackNic;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-NIC network stack: one IPv4 address, one MAC, one ARP cache, and a
 * port-keyed table of UDP sockets. Receives raw frames from the NIC
 * (via softirq) and routes UDP datagrams up to the matching socket.
 */
public final class NetStack {

    private final InterruptController interrupts;
    private final LoopbackNic nic;
    private final int hostIp;
    private final ArpLayer arp = new ArpLayer();
    private final ConcurrentMap<Integer, UdpSocket> udpByPort = new ConcurrentHashMap<>();

    public NetStack(InterruptController interrupts, LoopbackNic nic, int hostIp) {
        this.interrupts = interrupts;
        this.nic = nic;
        this.hostIp = hostIp;
        // Pre-load own address into the ARP cache so transmit() short-circuits.
        arp.learn(hostIp, nic.macAddress());
        // The NIC hands raw frames to softirqd; the softirq processes them.
        nic.setRxCallback(frame -> interrupts.raiseSoftIrq(() -> onFrame(frame)));
    }

    public int hostIp() { return hostIp; }
    public LoopbackNic nic() { return nic; }
    public ArpLayer arp() { return arp; }

    public boolean bindUdp(UdpSocket sock, int port) {
        if (udpByPort.containsKey(port)) return false;
        if (!sock.bind(hostIp, port)) return false;
        udpByPort.put(port, sock);
        return true;
    }

    public void unbindUdp(int port) { udpByPort.remove(port); }

    public UdpSocket lookupUdp(int port) { return udpByPort.get(port); }

    /** Send a UDP datagram. Resolves the dst MAC via ARP cache (broadcast if missing). */
    public void sendUdp(int dstIp, int srcPort, int dstPort, byte[] payload) {
        byte[] udp = UdpDatagram.encode(hostIp, dstIp, srcPort, dstPort, payload);
        byte[] ip  = IpPacket.encode(hostIp, dstIp, IpPacket.PROTO_UDP, udp);
        byte[] dstMac = arp.resolve(dstIp);
        if (dstMac == null) dstMac = EthernetFrame.broadcastMac();
        byte[] frame = EthernetFrame.encode(dstMac, nic.macAddress(), EthernetFrame.ETHERTYPE_IPV4, ip);
        nic.transmit(frame);
    }

    /** Top-half RX entry point: invoked indirectly via softirq queue. */
    void onFrame(byte[] frame) {
        try {
            SkBuff skb = SkBuff.of(frame);
            EthernetFrame.decode(skb);
            arp.learn(0, skb.srcMac); // unconditional MAC learning at L2
            if (skb.etherType != EthernetFrame.ETHERTYPE_IPV4) return;
            IpPacket.decode(skb);
            arp.learn(skb.srcIp, skb.srcMac);
            if (skb.dstIp != hostIp) return; // not for us
            if (skb.ipProtocol != IpPacket.PROTO_UDP) return;
            UdpDatagram.decode(skb);
            UdpSocket sock = udpByPort.get(skb.dstPort);
            if (sock == null) {
                KLog.debug("net: no listener on udp port %d (host=%s)", skb.dstPort, Ipv4.format(hostIp));
                return;
            }
            byte[] payload = skb.payload();
            sock.enqueue(new Datagram(skb.srcIp, skb.srcPort, skb.dstIp, skb.dstPort, payload));
        } catch (Exception e) {
            KLog.warn("net: drop malformed frame: %s", e.getMessage());
        }
    }
}
