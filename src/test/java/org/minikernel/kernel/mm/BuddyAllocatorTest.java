package org.minikernel.kernel.mm;

import org.junit.jupiter.api.Test;
import org.minikernel.hal.PhysicalMemory;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuddyAllocatorTest {

    private BuddyAllocator newBuddy(int order) {
        int frames = 1 << order;
        PhysicalMemory mem = new PhysicalMemory(frames * PhysicalMemory.PAGE_SIZE);
        return new BuddyAllocator(mem, 0, frames, order);
    }

    @Test
    void allocAndFreeOneFrame() {
        BuddyAllocator b = newBuddy(4); // 16 frames
        int f = b.allocFrame();
        assertEquals(0, f);
        b.freeFrame(f);
        assertEquals(1, b.freeCount(4));
    }

    @Test
    void splitsHighOrderToServeLow() {
        BuddyAllocator b = newBuddy(4);
        int f0 = b.allocBlock(0);
        int f1 = b.allocBlock(0);
        int f2 = b.allocBlock(0);
        assertNotEquals(f0, f1);
        assertNotEquals(f1, f2);
        assertNotEquals(f0, f2);
    }

    @Test
    void buddyMergesOnFree() {
        BuddyAllocator b = newBuddy(3); // 8 frames
        int a = b.allocBlock(0);
        int c = b.allocBlock(0);
        b.freeFrame(a);
        b.freeFrame(c);
        // after both freed, should be back to one order-3 block.
        assertEquals(1, b.freeCount(3));
    }

    @Test
    void uniqueAllocationsUntilExhaustion() {
        BuddyAllocator b = newBuddy(3); // 8 frames
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            int f = b.allocFrame();
            assertTrue(f >= 0, "alloc " + i + " failed");
            assertTrue(seen.add(f), "duplicate frame " + f);
        }
        assertEquals(-1, b.allocFrame());
    }

    @Test
    void higherOrderAllocation() {
        BuddyAllocator b = newBuddy(4); // 16 frames
        int blk = b.allocBlock(2); // 4 frames
        assertEquals(0, blk);
        int blk2 = b.allocBlock(2);
        assertEquals(4, blk2);
        b.freeBlock(blk, 2);
        b.freeBlock(blk2, 2);
        assertEquals(1, b.freeCount(4));
    }
}
