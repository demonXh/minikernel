package org.minikernel.kernel.mm;

import org.minikernel.core.log.KLog;
import org.minikernel.hal.PhysicalMemory;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;

/**
 * Buddy-system page-frame allocator.
 *
 * <p>Manages a contiguous region of {@link PhysicalMemory} page frames in
 * power-of-two blocks. Each order {@code k} maintains a free list of blocks
 * of {@code 2^k} contiguous frames. Allocating order {@code k}:
 * <ol>
 *   <li>Pop a block from order {@code k}'s list; or</li>
 *   <li>Recursively split a higher-order block into two buddies, returning
 *       the left half and pushing the right half to order {@code k}.</li>
 * </ol>
 * Freeing merges adjacent buddies that are both free.
 *
 * <p>This mirrors the role of {@code alloc_pages(order)} / {@code free_pages}
 * in Linux's page allocator. The underlying physical memory is unchanged;
 * we only track which frames are owned by whom.
 */
public final class BuddyAllocator {

    private final PhysicalMemory memory;
    private final int baseFrame;
    private final int totalFrames;
    private final int maxOrder;
    private final Deque<Integer>[] freeLists;
    /** free[order] bitmap: bit i set iff block of order=order starting at frame baseFrame+i*(1<<order) is free. */
    private final BitSet[] freeBitmap;
    /** size[frame] = order of the block whose head is this frame (only valid for allocated heads). */
    private final int[] blockOrder;

    @SuppressWarnings("unchecked")
    public BuddyAllocator(PhysicalMemory memory, int baseFrame, int totalFrames, int maxOrder) {
        if (totalFrames <= 0 || maxOrder < 0) throw new IllegalArgumentException();
        if (totalFrames != (1 << maxOrder)) {
            throw new IllegalArgumentException("totalFrames must equal 2^maxOrder (got " + totalFrames + ", maxOrder=" + maxOrder + ")");
        }
        this.memory = memory;
        this.baseFrame = baseFrame;
        this.totalFrames = totalFrames;
        this.maxOrder = maxOrder;
        this.freeLists = (Deque<Integer>[]) new Deque[maxOrder + 1];
        this.freeBitmap = new BitSet[maxOrder + 1];
        for (int o = 0; o <= maxOrder; o++) {
            freeLists[o] = new ArrayDeque<>();
            freeBitmap[o] = new BitSet(totalFrames >> o);
        }
        this.blockOrder = new int[totalFrames];
        // initial: one big block covering everything at maxOrder.
        freeLists[maxOrder].push(baseFrame);
        freeBitmap[maxOrder].set(0);
        KLog.info("BuddyAllocator: base=%d frames=%d maxOrder=%d", baseFrame, totalFrames, maxOrder);
    }

    public PhysicalMemory memory() { return memory; }
    public int maxOrder() { return maxOrder; }

    /** Allocate a block of 2^order contiguous frames; returns the head frame index, or -1 on OOM. */
    public synchronized int allocBlock(int order) {
        if (order < 0 || order > maxOrder) throw new IllegalArgumentException("bad order " + order);
        int o = order;
        while (o <= maxOrder && freeLists[o].isEmpty()) o++;
        if (o > maxOrder) return -1;
        int frame = freeLists[o].pop();
        freeBitmap[o].clear(indexOf(frame, o));
        while (o > order) {
            o--;
            int buddy = frame + (1 << o);
            freeLists[o].push(buddy);
            freeBitmap[o].set(indexOf(buddy, o));
        }
        blockOrder[frame - baseFrame] = order;
        return frame;
    }

    /** Free a previously-allocated block; order must match the one returned by allocBlock. */
    public synchronized void freeBlock(int frame, int order) {
        if (order < 0 || order > maxOrder) throw new IllegalArgumentException("bad order " + order);
        if (frame < baseFrame || frame >= baseFrame + totalFrames) {
            throw new IndexOutOfBoundsException("frame out of range");
        }
        int o = order;
        int f = frame;
        while (o < maxOrder) {
            int buddy = buddyOf(f, o);
            int idx = indexOf(buddy, o);
            if (!freeBitmap[o].get(idx)) break;
            freeBitmap[o].clear(idx);
            freeLists[o].remove(buddy);
            f = Math.min(f, buddy);
            o++;
        }
        freeLists[o].push(f);
        freeBitmap[o].set(indexOf(f, o));
    }

    /** Single-page convenience. */
    public int allocFrame() { return allocBlock(0); }
    public void freeFrame(int frame) { freeBlock(frame, 0); }

    public synchronized int freeCount(int order) { return freeLists[order].size(); }

    public synchronized long totalFreeBytes() {
        long total = 0;
        for (int o = 0; o <= maxOrder; o++) total += (long) freeLists[o].size() * (1L << o) * PhysicalMemory.PAGE_SIZE;
        return total;
    }

    private int indexOf(int frame, int order) {
        return (frame - baseFrame) >> order;
    }

    private int buddyOf(int frame, int order) {
        int offset = frame - baseFrame;
        int buddyOffset = offset ^ (1 << order);
        return baseFrame + buddyOffset;
    }
}
