package org.minikernel.kernel.net;

import org.junit.jupiter.api.Test;
import org.minikernel.core.interrupt.InterruptController;
import org.minikernel.kernel.net.loopback.LoopbackNic;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NetStackTest {

    private NetStack[] buildPair() {
        InterruptController irq = new InterruptController();
        LoopbackNic a = new LoopbackNic("a", new byte[]{1,1,1,1,1,1});
        LoopbackNic b = new LoopbackNic("b", new byte[]{2,2,2,2,2,2});
        a.setPeer(b);
        b.setPeer(a);
        NetStack nsA = new NetStack(irq, a, Ipv4.parse("10.0.0.1"));
        NetStack nsB = new NetStack(irq, b, Ipv4.parse("10.0.0.2"));
        nsA.arp().learn(nsB.hostIp(), b.macAddress());
        nsB.arp().learn(nsA.hostIp(), a.macAddress());
        return new NetStack[]{nsA, nsB};
    }

    @Test
    void udpDatagramArrivesAtBoundSocket() {
        NetStack[] pair = buildPair();
        NetStack nsA = pair[0], nsB = pair[1];
        UdpSocket server = new UdpSocket();
        assertTrue(nsB.bindUdp(server, 7000));
        UdpSocket client = new UdpSocket();
        assertTrue(nsA.bindUdp(client, 5000));

        nsA.sendUdp(nsB.hostIp(), 5000, 7000, "hi".getBytes());

        Datagram d = server.receive(2000);
        assertNotNull(d, "no datagram delivered");
        assertArrayEquals("hi".getBytes(), d.payload);
        assertEquals(nsA.hostIp(), d.srcIp);
        assertEquals(5000, d.srcPort);
        assertEquals(7000, d.dstPort);
    }

    @Test
    void unboundPortDropsPacket() {
        NetStack[] pair = buildPair();
        NetStack nsA = pair[0], nsB = pair[1];
        UdpSocket lone = new UdpSocket();
        assertTrue(nsB.bindUdp(lone, 7001));
        nsA.sendUdp(nsB.hostIp(), 5000, 9999, "drop".getBytes());
        Datagram d = lone.receive(100);
        assertNull(d);
    }

    @Test
    void duplicateBindRejected() {
        NetStack[] pair = buildPair();
        UdpSocket s1 = new UdpSocket();
        UdpSocket s2 = new UdpSocket();
        assertTrue(pair[0].bindUdp(s1, 5000));
        assertFalse(pair[0].bindUdp(s2, 5000));
    }

    @Test
    void arpLearnsFromIncomingFrames() throws InterruptedException {
        NetStack[] pair = buildPair();
        NetStack nsA = pair[0], nsB = pair[1];
        UdpSocket s = new UdpSocket();
        nsB.bindUdp(s, 7002);
        nsA.sendUdp(nsB.hostIp(), 1234, 7002, "x".getBytes());
        // Allow softirqd to run.
        Datagram d = s.receive(1000);
        assertNotNull(d);
        TimeUnit.MILLISECONDS.sleep(10);
        assertNotNull(nsB.arp().resolve(nsA.hostIp()), "B should know A after first packet");
    }
}
