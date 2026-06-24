package org.minikernel.kernel.mm;

import org.minikernel.core.log.KLog;
import org.minikernel.hal.PhysicalMemory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Slab allocator: caches fixed-size objects within whole pages obtained
 * from a {@link BuddyAllocator}. Equivalent in spirit to a Linux
 * {@code kmem_cache}.
 *
 * <p>Each slab covers exactly one page; the first usable slots return an
 * <em>address inside that page</em>, in bytes. Free slots are tracked by
 * a per-slab freelist of offsets. When all slabs are full, a fresh page
 * is requested from the buddy allocator.
 */
public final class SlabAllocator {

    private final BuddyAllocator buddy;
    private final int objectSize;
    private final List<Slab> slabs = new ArrayList<>();

    private static final class Slab {
        final int frame;
        final long baseAddr;
        final Deque<Long> freeOffsets;
        int inUse;
        Slab(int frame, long baseAddr, int count) {
            this.frame = frame;
            this.baseAddr = baseAddr;
            this.freeOffsets = new ArrayDeque<>(count);
            for (int i = 0; i < count; i++) freeOffsets.push((long) i);
        }
    }

    public SlabAllocator(BuddyAllocator buddy, int objectSize) {
        if (objectSize <= 0 || objectSize > PhysicalMemory.PAGE_SIZE) {
            throw new IllegalArgumentException("objectSize must be in (0," + PhysicalMemory.PAGE_SIZE + "]");
        }
        this.buddy = buddy;
        this.objectSize = objectSize;
        KLog.info("SlabAllocator: objectSize=%d perPage=%d", objectSize, PhysicalMemory.PAGE_SIZE / objectSize);
    }

    public int objectSize() { return objectSize; }

    /** Allocate one object; returns its physical address, or -1 on OOM. */
    public synchronized long alloc() {
        for (Slab s : slabs) {
            if (!s.freeOffsets.isEmpty()) return takeFrom(s);
        }
        int frame = buddy.allocFrame();
        if (frame < 0) return -1L;
        long base = buddy.memory().frameToAddr(frame);
        Slab s = new Slab(frame, base, PhysicalMemory.PAGE_SIZE / objectSize);
        slabs.add(s);
        return takeFrom(s);
    }

    /** Free an object previously returned by alloc(). */
    public synchronized void free(long addr) {
        for (Slab s : slabs) {
            if (addr >= s.baseAddr && addr < s.baseAddr + PhysicalMemory.PAGE_SIZE) {
                long offset = (addr - s.baseAddr) / objectSize;
                if ((addr - s.baseAddr) % objectSize != 0) throw new IllegalArgumentException("misaligned free " + addr);
                s.freeOffsets.push(offset);
                s.inUse--;
                if (s.inUse == 0 && slabs.size() > 1) {
                    slabs.remove(s);
                    buddy.freeFrame(s.frame);
                }
                return;
            }
        }
        throw new IllegalArgumentException("free of address " + addr + " not from this slab");
    }

    public synchronized int slabCount() { return slabs.size(); }

    public synchronized int inUseCount() {
        int n = 0;
        for (Slab s : slabs) n += s.inUse;
        return n;
    }

    private long takeFrom(Slab s) {
        long offset = s.freeOffsets.pop();
        s.inUse++;
        return s.baseAddr + offset * objectSize;
    }
}
