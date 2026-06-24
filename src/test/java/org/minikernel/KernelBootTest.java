package org.minikernel;

import org.junit.jupiter.api.Test;
import org.minikernel.core.interrupt.InterruptVector;
import org.minikernel.hal.PhysicalMemory;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test: boot the kernel, let the clock tick a few times, then halt.
 */
class KernelBootTest {

    @Test
    void bootsAndReceivesTimerInterrupts() throws InterruptedException {
        MiniKernel kernel = new MiniKernel(16 * PhysicalMemory.PAGE_SIZE, 20);
        AtomicInteger ticks = new AtomicInteger();
        kernel.interrupts().register(InterruptVector.TIMER, (vec, cpu) -> ticks.incrementAndGet());

        kernel.boot();
        try {
            Thread.sleep(300);
        } finally {
            kernel.shutdown();
        }
        assertTrue(ticks.get() >= 5, "expected at least 5 ticks, got " + ticks.get());
    }

    @Test
    void cpuTransitionsToHalted() throws InterruptedException {
        MiniKernel kernel = new MiniKernel(8 * PhysicalMemory.PAGE_SIZE, 50);
        kernel.boot();
        assertTrue(kernel.cpu().isRunning());
        kernel.shutdown();
        assertFalse(kernel.cpu().isRunning());
    }
}
