package org.minikernel.kernel.mm;

import org.minikernel.hal.PhysicalMemory;

/**
 * Two-level page table.
 *
 * <p>Address layout for a 32-bit virtual address:
 * <pre>
 *  31              22 21             12 11                0
 *  +-----------------+-----------------+-------------------+
 *  |   VPN_HI (10)   |   VPN_LO (10)   |   PAGE_OFFSET (12) |
 *  +-----------------+-----------------+-------------------+
 * </pre>
 *
 * <p>Each PTE is an int: high 20 bits hold the physical frame index, low
 * 12 bits hold flags from {@link PteFlags}. PTE value 0 means "not mapped".
 *
 * <p>Linux counterpart: pgd / pmd / pte tables (here collapsed to two
 * levels for clarity).
 */
public final class PageTable {

    public static final int VPN_BITS_PER_LEVEL = 10;
    public static final int ENTRIES_PER_TABLE = 1 << VPN_BITS_PER_LEVEL;
    public static final int OFFSET_BITS = 12;
    public static final int OFFSET_MASK = (1 << OFFSET_BITS) - 1;

    private final int[][] dir = new int[ENTRIES_PER_TABLE][];

    public int translate(long virtualAddr) {
        int hi = (int) ((virtualAddr >>> (OFFSET_BITS + VPN_BITS_PER_LEVEL)) & (ENTRIES_PER_TABLE - 1));
        int lo = (int) ((virtualAddr >>> OFFSET_BITS) & (ENTRIES_PER_TABLE - 1));
        int[] inner = dir[hi];
        if (inner == null) return 0;
        return inner[lo];
    }

    public void map(long virtualAddr, int frameIndex, int flags) {
        int hi = (int) ((virtualAddr >>> (OFFSET_BITS + VPN_BITS_PER_LEVEL)) & (ENTRIES_PER_TABLE - 1));
        int lo = (int) ((virtualAddr >>> OFFSET_BITS) & (ENTRIES_PER_TABLE - 1));
        int[] inner = dir[hi];
        if (inner == null) {
            inner = new int[ENTRIES_PER_TABLE];
            dir[hi] = inner;
        }
        inner[lo] = (frameIndex << OFFSET_BITS) | (flags & OFFSET_MASK) | PteFlags.PRESENT;
    }

    public void unmap(long virtualAddr) {
        int hi = (int) ((virtualAddr >>> (OFFSET_BITS + VPN_BITS_PER_LEVEL)) & (ENTRIES_PER_TABLE - 1));
        int lo = (int) ((virtualAddr >>> OFFSET_BITS) & (ENTRIES_PER_TABLE - 1));
        int[] inner = dir[hi];
        if (inner != null) inner[lo] = 0;
    }

    public static int frameOf(int pte) { return pte >>> OFFSET_BITS; }
    public static int flagsOf(int pte) { return pte & OFFSET_MASK; }
    public static long pageBase(long vaddr) { return vaddr & ~((long) OFFSET_MASK); }
    public static long offset(long vaddr) { return vaddr & OFFSET_MASK; }

    /** Compose a physical address from a frame index and an in-page offset. */
    public long resolve(long virtualAddr) {
        int pte = translate(virtualAddr);
        if (!PteFlags.has(pte, PteFlags.PRESENT)) return -1L;
        long phys = ((long) frameOf(pte) * PhysicalMemory.PAGE_SIZE) | offset(virtualAddr);
        return phys;
    }
}
