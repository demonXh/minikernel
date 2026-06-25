package org.minikernel.kernel.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeaderEncodingTest {

    @Test
    void ipv4ParseAndFormat() {
        int ip = Ipv4.parse("192.168.1.100");
        assertEquals("192.168.1.100", Ipv4.format(ip));
        assertEquals(ip, Ipv4.pack(192, 168, 1, 100));
    }

    @Test
    void ethernetRoundtrip() {
        byte[] src = new byte[]{1,2,3,4,5,6};
        byte[] dst = new byte[]{9,8,7,6,5,4};
        byte[] payload = "hi".getBytes();
        byte[] frame = EthernetFrame.encode(dst, src, EthernetFrame.ETHERTYPE_IPV4, payload);
        SkBuff skb = SkBuff.of(frame);
        EthernetFrame.decode(skb);
        assertArrayEquals(dst, skb.dstMac);
        assertArrayEquals(src, skb.srcMac);
        assertEquals(EthernetFrame.ETHERTYPE_IPV4, skb.etherType);
        assertArrayEquals(payload, skb.payload());
    }

    @Test
    void ipPacketRoundtrip() {
        int src = Ipv4.parse("10.0.0.1");
        int dst = Ipv4.parse("10.0.0.2");
        byte[] payload = "hello".getBytes();
        byte[] pkt = IpPacket.encode(src, dst, IpPacket.PROTO_UDP, payload);
        SkBuff skb = SkBuff.of(pkt);
        IpPacket.decode(skb);
        assertEquals(src, skb.srcIp);
        assertEquals(dst, skb.dstIp);
        assertEquals(IpPacket.PROTO_UDP, skb.ipProtocol);
        assertArrayEquals(payload, skb.payload());
    }

    @Test
    void udpRoundtrip() {
        byte[] pkt = UdpDatagram.encode(0x0A000001, 0x0A000002, 5000, 7000, "ping".getBytes());
        SkBuff skb = SkBuff.of(pkt);
        UdpDatagram.decode(skb);
        assertEquals(5000, skb.srcPort);
        assertEquals(7000, skb.dstPort);
        assertArrayEquals("ping".getBytes(), skb.payload());
    }

    @Test
    void ipChecksumIsZeroOnValidPacket() {
        byte[] pkt = IpPacket.encode(0x0A000001, 0x0A000002, IpPacket.PROTO_UDP, new byte[]{1,2,3,4});
        int verify = IpPacket.checksum(pkt, 0, IpPacket.HEADER_LEN);
        assertEquals(0, verify);
    }
}
