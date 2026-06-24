package org.minikernel.kernel.mm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageTableTest {

    @Test
    void mapAndTranslate() {
        PageTable pt = new PageTable();
        long vaddr = 0x12345678L;
        pt.map(PageTable.pageBase(vaddr), 7, PteFlags.WRITE | PteFlags.USER);
        int pte = pt.translate(vaddr);
        assertTrue(PteFlags.has(pte, PteFlags.PRESENT));
        assertTrue(PteFlags.has(pte, PteFlags.WRITE));
        assertEquals(7, PageTable.frameOf(pte));
    }

    @Test
    void unmapClears() {
        PageTable pt = new PageTable();
        pt.map(0x1000L, 3, PteFlags.WRITE);
        assertNotEquals(0, pt.translate(0x1000L));
        pt.unmap(0x1000L);
        assertEquals(0, pt.translate(0x1000L));
    }

    @Test
    void unmappedReturnsZero() {
        PageTable pt = new PageTable();
        assertEquals(0, pt.translate(0xDEADBEEFL));
        assertEquals(-1L, pt.resolve(0xDEADBEEFL));
    }

    @Test
    void resolveComputesPhysical() {
        PageTable pt = new PageTable();
        pt.map(0x2000L, 5, PteFlags.WRITE);
        long phys = pt.resolve(0x2000L + 0x123);
        assertEquals(5L * 4096 + 0x123, phys);
    }
}
