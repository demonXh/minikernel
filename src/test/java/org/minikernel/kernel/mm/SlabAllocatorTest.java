package org.minikernel.kernel.mm;

import org.junit.jupiter.api.Test;
import org.minikernel.hal.PhysicalMemory;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SlabAllocatorTest {

    private BuddyAllocator newBuddy(int order) {
        int frames = 1 << order;
        PhysicalMemory mem = new PhysicalMemory(frames * PhysicalMemory.PAGE_SIZE);
        return new BuddyAllocator(mem, 0, frames, order);
    }

    @Test
    void packsObjectsInOnePage() {
        SlabAllocator s = new SlabAllocator(newBuddy(2), 128);
        Set<Long> addrs = new HashSet<>();
        int perPage = PhysicalMemory.PAGE_SIZE / 128;
        for (int i = 0; i < perPage; i++) {
            long a = s.alloc();
            assertTrue(a >= 0);
            assertTrue(addrs.add(a));
        }
        assertEquals(1, s.slabCount());
    }

    @Test
    void allocatesNewPageWhenSlabFull() {
        SlabAllocator s = new SlabAllocator(newBuddy(2), PhysicalMemory.PAGE_SIZE / 2);
        s.alloc();
        s.alloc();
        // page is now full
        s.alloc();
        assertEquals(2, s.slabCount());
    }

    @Test
    void freeReleasesSlot() {
        SlabAllocator s = new SlabAllocator(newBuddy(2), 256);
        long a = s.alloc();
        long b = s.alloc();
        assertEquals(2, s.inUseCount());
        s.free(a);
        assertEquals(1, s.inUseCount());
        long c = s.alloc();
        assertTrue(c >= 0);
        s.free(b);
        s.free(c);
    }

    @Test
    void rejectsAlienAddress() {
        SlabAllocator s = new SlabAllocator(newBuddy(2), 64);
        s.alloc();
        assertThrows(IllegalArgumentException.class, () -> s.free(999_999L));
    }
}
