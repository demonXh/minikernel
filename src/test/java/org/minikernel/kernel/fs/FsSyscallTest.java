package org.minikernel.kernel.fs;

import org.junit.jupiter.api.Test;
import org.minikernel.MiniKernel;
import org.minikernel.hal.PhysicalMemory;
import org.minikernel.user.Libc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FsSyscallTest {

    private MiniKernel newKernel() {
        return new MiniKernel(32 * PhysicalMemory.PAGE_SIZE, 20);
    }

    @Test
    void openWriteReadRoundtrip() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> content = new AtomicReference<>();
            k.startInit(() -> {
                int fd = (int) Libc.open("/file", OpenFlags.O_CREAT | OpenFlags.O_RDWR);
                assertTrue(fd >= 0);
                long w = Libc.writeFd(fd, "syscall-fs");
                assertEquals(10, w);
                Libc.lseek(fd, 0, 0);
                content.set(Libc.readAll(fd, 32));
                Libc.close(fd);
                done.countDown();
                Libc.exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals("syscall-fs", content.get());
        } finally {
            k.shutdown();
        }
    }

    @Test
    void openMissingReturnsENOENT() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicLong rc = new AtomicLong();
            k.startInit(() -> {
                rc.set(Libc.open("/no-such-file", OpenFlags.O_RDONLY));
                done.countDown();
                Libc.exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertTrue(rc.get() < 0);
        } finally {
            k.shutdown();
        }
    }

    @Test
    void mkdirThenNestedOpen() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicLong rc = new AtomicLong();
            k.startInit(() -> {
                rc.set(Libc.mkdir("/d", 0755));
                int fd = (int) Libc.open("/d/inner", OpenFlags.O_CREAT | OpenFlags.O_RDWR);
                Libc.writeFd(fd, "ok");
                Libc.close(fd);
                done.countDown();
                Libc.exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals(0L, rc.get());
            assertNotNull(k.vfs().lookup("/d/inner"));
        } finally {
            k.shutdown();
        }
    }

    @Test
    void closeBadFdReturnsEBADF() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicLong rc = new AtomicLong();
            k.startInit(() -> {
                rc.set(Libc.close(123));
                done.countDown();
                Libc.exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertTrue(rc.get() < 0);
        } finally {
            k.shutdown();
        }
    }

    @Test
    void unlinkRemovesFile() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            k.startInit(() -> {
                int fd = (int) Libc.open("/del", OpenFlags.O_CREAT | OpenFlags.O_RDWR);
                Libc.close(fd);
                long rc = Libc.unlink("/del");
                assertEquals(0L, rc);
                long rc2 = Libc.unlink("/del");
                assertTrue(rc2 < 0);
                done.countDown();
                Libc.exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertNull(k.vfs().lookup("/del"));
        } finally {
            k.shutdown();
        }
    }
}
