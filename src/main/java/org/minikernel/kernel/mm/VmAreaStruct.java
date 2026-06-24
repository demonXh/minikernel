package org.minikernel.kernel.mm;

/**
 * Virtual Memory Area: a contiguous run of pages within a process address
 * space with uniform permissions. Equivalent to {@code struct vm_area_struct}.
 *
 * <p>Pages within a VMA may or may not be present in the page table; missing
 * pages trigger a page fault that the kernel resolves via demand paging.
 */
public final class VmAreaStruct {

    public enum Kind { TEXT, DATA, HEAP, STACK, MMAP }

    private final long start;     // virtual address, page-aligned
    private final long end;       // exclusive, page-aligned
    private final int flags;      // PteFlags subset (excluding PRESENT)
    private final Kind kind;

    public VmAreaStruct(long start, long end, int flags, Kind kind) {
        if ((start & PageTable.OFFSET_MASK) != 0 || (end & PageTable.OFFSET_MASK) != 0) {
            throw new IllegalArgumentException("VMA bounds must be page-aligned");
        }
        if (end <= start) throw new IllegalArgumentException("end must be > start");
        this.start = start;
        this.end = end;
        this.flags = flags;
        this.kind = kind;
    }

    public long start() { return start; }
    public long end()   { return end; }
    public int flags()  { return flags; }
    public Kind kind()  { return kind; }

    public boolean contains(long vaddr) {
        return vaddr >= start && vaddr < end;
    }

    @Override
    public String toString() {
        return String.format("VMA{kind=%s [0x%x,0x%x) flags=0x%x}", kind, start, end, flags);
    }
}
