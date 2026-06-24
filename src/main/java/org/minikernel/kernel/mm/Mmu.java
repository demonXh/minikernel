package org.minikernel.kernel.mm;

import org.minikernel.core.log.KLog;
import org.minikernel.hal.PhysicalMemory;

/**
 * Memory Management Unit: virtual-to-physical translation with on-demand
 * page-fault handling.
 *
 * <p>Reads and writes go through {@link #readByte(MmStruct, long)} /
 * {@link #writeByte(MmStruct, long, byte)}. On a PTE miss we look up the
 * faulting address in the process's VMA list:
 * <ul>
 *   <li>If a VMA covers it, we allocate a fresh page frame, zero-fill it,
 *       and install a PTE - that's <b>demand paging</b>.</li>
 *   <li>Otherwise, throw {@link SegfaultException} (analogue of SIGSEGV).</li>
 * </ul>
 */
public final class Mmu {

    private final PhysicalMemory ram;

    public Mmu(PhysicalMemory ram) { this.ram = ram; }

    public byte readByte(MmStruct mm, long vaddr) {
        long phys = ensureMapped(mm, vaddr, false);
        return ram.readByte(phys);
    }

    public void writeByte(MmStruct mm, long vaddr, byte value) {
        long phys = ensureMapped(mm, vaddr, true);
        markDirty(mm, vaddr);
        ram.writeByte(phys, value);
    }

    public void writeBytes(MmStruct mm, long vaddr, byte[] src) {
        for (int i = 0; i < src.length; i++) writeByte(mm, vaddr + i, src[i]);
    }

    public byte[] readBytes(MmStruct mm, long vaddr, int len) {
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) out[i] = readByte(mm, vaddr + i);
        return out;
    }

    /**
     * Translate {@code vaddr} to a physical address, faulting in the page
     * on demand. Returns the physical address (frame*PAGE_SIZE | offset).
     */
    public long ensureMapped(MmStruct mm, long vaddr, boolean forWrite) {
        long phys = mm.pageTable().resolve(vaddr);
        if (phys >= 0) {
            if (forWrite) {
                int pte = mm.pageTable().translate(vaddr);
                if (!PteFlags.has(pte, PteFlags.WRITE)) throw new SegfaultException("write to read-only page at " + Long.toHexString(vaddr));
            }
            return phys;
        }
        // page fault: try demand paging from the VMA
        VmAreaStruct vma = mm.findVma(vaddr);
        if (vma == null) {
            throw new SegfaultException("no VMA for vaddr 0x" + Long.toHexString(vaddr));
        }
        if (forWrite && !PteFlags.has(vma.flags(), PteFlags.WRITE)) {
            throw new SegfaultException("write to non-writable VMA at 0x" + Long.toHexString(vaddr));
        }
        int frame = mm.pageSource().allocFrame();
        if (frame < 0) throw new SegfaultException("out of memory faulting in 0x" + Long.toHexString(vaddr));
        zeroFrame(frame);
        long pageBase = PageTable.pageBase(vaddr);
        mm.pageTable().map(pageBase, frame, vma.flags() | PteFlags.ACCESSED);
        KLog.debug("page fault resolved: vaddr=0x%x -> frame=%d (vma=%s)", vaddr, frame, vma.kind());
        return ((long) frame * PhysicalMemory.PAGE_SIZE) | PageTable.offset(vaddr);
    }

    private void zeroFrame(int frame) {
        long base = ram.frameToAddr(frame);
        byte[] zeros = new byte[PhysicalMemory.PAGE_SIZE];
        ram.writeBytes(base, zeros, 0, zeros.length);
    }

    private void markDirty(MmStruct mm, long vaddr) {
        int pte = mm.pageTable().translate(vaddr);
        if (!PteFlags.has(pte, PteFlags.DIRTY)) {
            int newFlags = PageTable.flagsOf(pte) | PteFlags.DIRTY;
            mm.pageTable().map(PageTable.pageBase(vaddr), PageTable.frameOf(pte), newFlags);
        }
    }
}
