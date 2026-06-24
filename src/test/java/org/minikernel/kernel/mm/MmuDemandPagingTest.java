package org.minikernel.kernel.mm;

import org.junit.jupiter.api.Test;
import org.minikernel.hal.PhysicalMemory;

import static org.junit.jupiter.api.Assertions.*;

class MmuDemandPagingTest {

    private static final int PAGE = PhysicalMemory.PAGE_SIZE;

    private BuddyAllocator newBuddy() {
        PhysicalMemory mem = new PhysicalMemory(16 * PAGE);
        return new BuddyAllocator(mem, 0, 16, 4);
    }

    @Test
    void writeThenReadThroughDemandPaging() {
        BuddyAllocator b = newBuddy();
        Mmu mmu = new Mmu(b.memory());
        MmStruct mm = new MmStruct(b);
        mm.addVma(new VmAreaStruct(0x10000L, 0x10000L + 4 * PAGE,
                PteFlags.WRITE | PteFlags.USER, VmAreaStruct.Kind.HEAP));

        mmu.writeBytes(mm, 0x10100L, "kernel".getBytes());
        byte[] back = mmu.readBytes(mm, 0x10100L, 6);
        assertArrayEquals("kernel".getBytes(), back);

        // PTE should now be present
        int pte = mm.pageTable().translate(0x10100L);
        assertTrue(PteFlags.has(pte, PteFlags.PRESENT));
        assertTrue(PteFlags.has(pte, PteFlags.DIRTY));
    }

    @Test
    void segfaultOutsideVma() {
        BuddyAllocator b = newBuddy();
        Mmu mmu = new Mmu(b.memory());
        MmStruct mm = new MmStruct(b);
        mm.addVma(new VmAreaStruct(0x10000L, 0x11000L, PteFlags.WRITE, VmAreaStruct.Kind.HEAP));
        assertThrows(SegfaultException.class, () -> mmu.readByte(mm, 0x20000L));
    }

    @Test
    void writeToReadOnlyVmaSegfaults() {
        BuddyAllocator b = newBuddy();
        Mmu mmu = new Mmu(b.memory());
        MmStruct mm = new MmStruct(b);
        mm.addVma(new VmAreaStruct(0x10000L, 0x11000L, PteFlags.USER, VmAreaStruct.Kind.TEXT));
        assertThrows(SegfaultException.class, () -> mmu.writeByte(mm, 0x10000L, (byte) 1));
    }

    @Test
    void readZeroFillsNewPage() {
        BuddyAllocator b = newBuddy();
        Mmu mmu = new Mmu(b.memory());
        MmStruct mm = new MmStruct(b);
        mm.addVma(new VmAreaStruct(0x10000L, 0x11000L, PteFlags.WRITE, VmAreaStruct.Kind.HEAP));
        assertEquals((byte) 0, mmu.readByte(mm, 0x10042L));
    }

    @Test
    void overlappingVmaRejected() {
        BuddyAllocator b = newBuddy();
        MmStruct mm = new MmStruct(b);
        mm.addVma(new VmAreaStruct(0x10000L, 0x12000L, PteFlags.WRITE, VmAreaStruct.Kind.HEAP));
        assertThrows(IllegalArgumentException.class,
                () -> mm.addVma(new VmAreaStruct(0x11000L, 0x13000L, PteFlags.WRITE, VmAreaStruct.Kind.HEAP)));
    }
}
