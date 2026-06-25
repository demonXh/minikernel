package org.minikernel.kernel.net;

import org.junit.jupiter.api.Test;
import org.minikernel.MiniKernel;
import org.minikernel.hal.PhysicalMemory;
import org.minikernel.user.Libc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NetSyscallTest {

    private MiniKernel newKernel() {
        return new MiniKernel(32 * PhysicalMemory.PAGE_SIZE, 20);
    }

    @Test
    void socketBindSendtoRecvfromRoundtrip() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> got = new AtomicReference<>("");
            int peerIp = Ipv4.parse("10.0.0.2");

            k.startInit(() -> {
                k.scheduler().current().setNetStack(k.netA());
                Libc.fork(() -> {
                    k.scheduler().current().setNetStack(k.netB());
                    int sd = Libc.socket(Libc.SOCK_UDP);
                    Libc.bind(sd, 7000);
                    String msg = Libc.recvfrom(sd, 64, 2000);
                    got.set(msg);
                    Libc.exit(0);
                });
                Libc.sleep(50);
                int c = Libc.socket(Libc.SOCK_UDP);
                Libc.bind(c, 5000);
                Libc.sendto(c, "ping".getBytes(), peerIp, 7000);
                Libc.waitpid();
                done.countDown();
                Libc.exit(0);
            });
            assertTrue(done.await(3, TimeUnit.SECONDS), "demo did not complete");
            assertEquals("ping", got.get());
        } finally {
            k.shutdown();
        }
    }

    @Test
    void recvfromTimesOutReturnsNegative() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> got = new AtomicReference<>("?");
            k.startInit(() -> {
                k.scheduler().current().setNetStack(k.netA());
                int sd = Libc.socket(Libc.SOCK_UDP);
                Libc.bind(sd, 6000);
                String s = Libc.recvfrom(sd, 64, 50); // nothing will arrive
                got.set(s);
                done.countDown();
                Libc.exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals("", got.get());
        } finally {
            k.shutdown();
        }
    }

    @Test
    void bindWithoutNetStackFails() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicLong rc = new java.util.concurrent.atomic.AtomicLong();
            k.startInit(() -> {
                int sd = Libc.socket(Libc.SOCK_UDP);
                rc.set(Libc.bind(sd, 8000));
                done.countDown();
                Libc.exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertTrue(rc.get() < 0);
        } finally {
            k.shutdown();
        }
    }
}
