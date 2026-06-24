package org.minikernel.kernel.sched;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PidAllocatorTest {

    @Test
    void idlePidIsReserved() {
        PidAllocator a = new PidAllocator(8);
        assertTrue(a.inUse(PidAllocator.IDLE_PID));
        assertEquals(PidAllocator.INIT_PID, a.alloc());
    }

    @Test
    void releasingIdleRejected() {
        PidAllocator a = new PidAllocator(8);
        assertThrows(IllegalArgumentException.class, () -> a.release(PidAllocator.IDLE_PID));
    }

    @Test
    void exhaustionThrows() {
        PidAllocator a = new PidAllocator(3);
        a.alloc(); a.alloc();
        assertThrows(IllegalStateException.class, a::alloc);
    }

    @Test
    void releaseAllowsReuse() {
        PidAllocator a = new PidAllocator(3);
        int p1 = a.alloc();
        int p2 = a.alloc();
        a.release(p1);
        assertEquals(p1, a.alloc());
        a.release(p2);
    }
}
