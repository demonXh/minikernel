package org.minikernel.kernel.sched;

import org.junit.jupiter.api.Test;
import org.minikernel.MiniKernel;
import org.minikernel.hal.PhysicalMemory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerTest {

    private MiniKernel newKernel() {
        return new MiniKernel(16 * PhysicalMemory.PAGE_SIZE, 20);
    }

    @Test
    void bootstrapCreatesIdleAndInit() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicInteger initPid = new AtomicInteger(-1);
            k.startInit(() -> {
                initPid.set(k.scheduler().current().pid());
                done.countDown();
                k.scheduler().exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS), "init never ran");
            assertEquals(PidAllocator.INIT_PID, initPid.get());
            assertEquals("swapper", k.scheduler().idle().name());
        } finally {
            k.shutdown();
        }
    }

    @Test
    void multipleTasksAllRunRoundRobin() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(3);
            List<Integer> order = Collections.synchronizedList(new ArrayList<>());
            k.startInit(() -> {
                for (int i = 0; i < 3; i++) {
                    final int idx = i;
                    k.scheduler().fork("w" + idx, () -> {
                        for (int n = 0; n < 4; n++) {
                            order.add(idx);
                            k.scheduler().yieldCpu();
                        }
                        done.countDown();
                        k.scheduler().exit(0);
                    });
                }
                for (int i = 0; i < 3; i++) k.scheduler().waitChild();
                k.scheduler().exit(0);
            });
            assertTrue(done.await(3, TimeUnit.SECONDS), "workers did not finish");
            assertEquals(12, order.size());
            // After round-robin, each task should have logged at least once.
            assertTrue(order.contains(0));
            assertTrue(order.contains(1));
            assertTrue(order.contains(2));
        } finally {
            k.shutdown();
        }
    }

    @Test
    void forkExitWaitReturnsExitCode() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicInteger reapedCode = new AtomicInteger(-1);
            AtomicInteger reapedPid = new AtomicInteger(-1);
            k.startInit(() -> {
                TaskStruct child = k.scheduler().fork("child", () -> k.scheduler().exit(42));
                long packed = k.scheduler().waitChild();
                reapedPid.set((int) (packed >>> 32));
                reapedCode.set((int) packed);
                assertEquals(child.pid(), reapedPid.get());
                done.countDown();
                k.scheduler().exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals(42, reapedCode.get());
            assertTrue(reapedPid.get() >= PidAllocator.INIT_PID + 1);
        } finally {
            k.shutdown();
        }
    }

    @Test
    void waitWithNoChildrenReturnsMinusOne() throws InterruptedException {
        MiniKernel k = newKernel();
        k.boot();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicInteger result = new AtomicInteger(0);
            k.startInit(() -> {
                long r = k.scheduler().waitChild();
                result.set((int) r);
                done.countDown();
                k.scheduler().exit(0);
            });
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals(-1, result.get());
        } finally {
            k.shutdown();
        }
    }
}
