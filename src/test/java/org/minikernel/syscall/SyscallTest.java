package org.minikernel.syscall;

import org.junit.jupiter.api.Test;
import org.minikernel.MiniKernel;
import org.minikernel.hal.PhysicalMemory;
import org.minikernel.user.Libc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SyscallTest {

    private MiniKernel newKernel() {
        return new MiniKernel(32 * PhysicalMemory.PAGE_SIZE, 20);
    }

    @Test
    void getpidReturnsCallerPid() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicInteger pid = new AtomicInteger(-1);
            k.startInit(() -> {
                pid.set(Libc.getpid());
                done.countDown();
                Libc.exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals(1, pid.get());
        } finally {
            k.shutdown();
        }
    }

    @Test
    void writeReturnsBytesWritten() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicLong result = new AtomicLong(-1);
            k.startInit(() -> {
                result.set(Libc.write(Libc.STDOUT, "hi"));
                done.countDown();
                Libc.exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals(2L, result.get());
        } finally {
            k.shutdown();
        }
    }

    @Test
    void writeBadFdReturnsEBADF() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicLong result = new AtomicLong(0);
            k.startInit(() -> {
                result.set(Libc.write(42, "lost"));
                done.countDown();
                Libc.exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals(-(long) Errno.EBADF, result.get());
        } finally {
            k.shutdown();
        }
    }

    @Test
    void forkExitWaitpidRoundtrip() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicInteger reapedPid = new AtomicInteger(-1);
            AtomicInteger reapedCode = new AtomicInteger(-1);
            k.startInit(() -> {
                int child = Libc.fork(() -> Libc.exit(77));
                long packed = Libc.waitpid();
                reapedPid.set((int) (packed >>> 32));
                reapedCode.set((int) packed);
                assertEquals(child, reapedPid.get());
                done.countDown();
                Libc.exit(0);
            });
            assertTrue(done.await(3, TimeUnit.SECONDS));
            assertEquals(77, reapedCode.get());
        } finally {
            k.shutdown();
        }
    }

    @Test
    void waitpidNoChildReturnsECHILD() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicLong result = new AtomicLong(0);
            k.startInit(() -> {
                result.set(Libc.waitpid());
                done.countDown();
                Libc.exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals(-(long) Errno.ECHILD, result.get());
        } finally {
            k.shutdown();
        }
    }

    @Test
    void unknownSyscallReturnsENOSYS() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicLong result = new AtomicLong(0);
            k.startInit(() -> {
                result.set(Trap.syscall(999));
                done.countDown();
                Libc.exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals(-(long) Errno.ENOSYS, result.get());
        } finally {
            k.shutdown();
        }
    }

    @Test
    void brkExtendsHeap() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicLong topAddr = new AtomicLong(-1);
            k.startInit(() -> {
                k.scheduler().current().setMm(k.newProcessMm());
                topAddr.set(Libc.brk(3));
                done.countDown();
                Libc.exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals(0x00600000L + 3L * PhysicalMemory.PAGE_SIZE, topAddr.get());
        } finally {
            k.shutdown();
        }
    }
}
