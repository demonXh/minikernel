package org.minikernel.core.ds;

import java.util.BitSet;

/**
 * Fixed-size bitmap wrapper. Used by the page-frame allocator and, later,
 * inode/PID allocators. This is a thin facade over {@link BitSet} that
 * exposes only the operations a kernel typically needs and enforces bounds.
 */
public final class Bitmap {

    private final int size;
    private final BitSet bits;

    public Bitmap(int size) {
        if (size <= 0) throw new IllegalArgumentException("size must be > 0");
        this.size = size;
        this.bits = new BitSet(size);
    }

    public int size() { return size; }

    public void set(int index) {
        checkIndex(index);
        bits.set(index);
    }

    public void clear(int index) {
        checkIndex(index);
        bits.clear(index);
    }

    public boolean get(int index) {
        checkIndex(index);
        return bits.get(index);
    }

    /** Find the first cleared bit, set it, and return its index, or -1 if full. */
    public int allocFirstFree() {
        int i = bits.nextClearBit(0);
        if (i >= size) return -1;
        bits.set(i);
        return i;
    }

    public int cardinality() { return bits.cardinality(); }

    private void checkIndex(int i) {
        if (i < 0 || i >= size) throw new IndexOutOfBoundsException("index " + i + " out of [0," + size + ")");
    }
}
