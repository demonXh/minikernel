package org.minikernel.hal;

import org.minikernel.core.log.KLog;

import java.util.BitSet;

/**
 * Simulated physical memory, page-frame oriented.
 *
 * <p>Backed by a single {@code byte[]} arena divided into fixed-size 4 KiB
 * page frames. Exposes byte/word read-write and page-frame allocation. The
 * allocator is a trivial first-fit bitmap; the buddy system will replace it
 * in iteration 3 (memory management).
 *
 * <p>Linux counterparts: physical RAM, {@code struct page}, and a (much
 * simpler) version of {@code alloc_pages}/{@code free_pages}.
 */
public final class PhysicalMemory {

    public static final int PAGE_SIZE = 4096;

    private final byte[] ram;
    private final int totalFrames;
    private final BitSet frameBitmap;

    public PhysicalMemory(int sizeBytes) {
        if (sizeBytes <= 0 || sizeBytes % PAGE_SIZE != 0) {
            throw new IllegalArgumentException("size must be a positive multiple of " + PAGE_SIZE);
        }
        this.ram = new byte[sizeBytes];
        this.totalFrames = sizeBytes / PAGE_SIZE;
        this.frameBitmap = new BitSet(totalFrames);
        KLog.info("PhysicalMemory: %d bytes, %d page frames", sizeBytes, totalFrames);
    }

    public int totalFrames() { return totalFrames; }

    public int freeFrames() { return totalFrames - frameBitmap.cardinality(); }

    public synchronized int allocFrame() {
        int idx = frameBitmap.nextClearBit(0);
        if (idx >= totalFrames) return -1;
        frameBitmap.set(idx);
        return idx;
    }

    public synchronized void freeFrame(int frameIndex) {
        checkFrame(frameIndex);
        if (!frameBitmap.get(frameIndex)) {
            throw new IllegalStateException("double free of frame " + frameIndex);
        }
        frameBitmap.clear(frameIndex);
    }

    public byte readByte(long addr) {
        checkAddr(addr, 1);
        return ram[(int) addr];
    }

    public void writeByte(long addr, byte value) {
        checkAddr(addr, 1);
        ram[(int) addr] = value;
    }

    public void readBytes(long addr, byte[] dst, int offset, int len) {
        checkAddr(addr, len);
        System.arraycopy(ram, (int) addr, dst, offset, len);
    }

    public void writeBytes(long addr, byte[] src, int offset, int len) {
        checkAddr(addr, len);
        System.arraycopy(src, offset, ram, (int) addr, len);
    }

    public long frameToAddr(int frameIndex) {
        checkFrame(frameIndex);
        return (long) frameIndex * PAGE_SIZE;
    }

    private void checkAddr(long addr, int len) {
        if (addr < 0 || addr + len > ram.length) {
            throw new IndexOutOfBoundsException("addr=" + addr + " len=" + len + " ramSize=" + ram.length);
        }
    }

    private void checkFrame(int frameIndex) {
        if (frameIndex < 0 || frameIndex >= totalFrames) {
            throw new IndexOutOfBoundsException("frame " + frameIndex + " out of range [0," + totalFrames + ')');
        }
    }
}
