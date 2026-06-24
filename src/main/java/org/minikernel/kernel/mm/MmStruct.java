package org.minikernel.kernel.mm;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-process memory descriptor, the Java analogue of {@code struct mm_struct}.
 *
 * <p>Owns the {@link PageTable} and a list of {@link VmAreaStruct}s. The
 * actual physical frames backing user pages come from the kernel's
 * {@link BuddyAllocator}; this descriptor only records the mapping.
 */
public final class MmStruct {

    private final PageTable pageTable = new PageTable();
    private final List<VmAreaStruct> vmas = new ArrayList<>();
    private final BuddyAllocator pageSource;

    public MmStruct(BuddyAllocator pageSource) {
        this.pageSource = pageSource;
    }

    public PageTable pageTable() { return pageTable; }
    public List<VmAreaStruct> vmas() { return vmas; }
    public BuddyAllocator pageSource() { return pageSource; }

    public synchronized void addVma(VmAreaStruct vma) {
        for (VmAreaStruct existing : vmas) {
            if (overlaps(existing, vma)) {
                throw new IllegalArgumentException("VMA overlaps existing: " + existing + " vs " + vma);
            }
        }
        vmas.add(vma);
    }

    public synchronized VmAreaStruct findVma(long vaddr) {
        for (VmAreaStruct v : vmas) if (v.contains(vaddr)) return v;
        return null;
    }

    private static boolean overlaps(VmAreaStruct a, VmAreaStruct b) {
        return a.start() < b.end() && b.start() < a.end();
    }
}
