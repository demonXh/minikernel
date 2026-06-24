package org.minikernel.kernel.fs;

import java.util.Arrays;

/**
 * Per-process file descriptor table. fd is a small non-negative integer
 * index into this array; the contained {@link File} is the kernel object.
 * Linux analogue: {@code struct files_struct.fd_array}.
 */
public final class FdTable {

    public static final int DEFAULT_CAPACITY = 32;

    private File[] slots;

    public FdTable() { this(DEFAULT_CAPACITY); }
    public FdTable(int capacity) { this.slots = new File[capacity]; }

    public synchronized int install(File f) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) { slots[i] = f; return i; }
        }
        // grow
        int oldLen = slots.length;
        slots = Arrays.copyOf(slots, oldLen * 2);
        slots[oldLen] = f;
        return oldLen;
    }

    public synchronized File get(int fd) {
        if (fd < 0 || fd >= slots.length) return null;
        return slots[fd];
    }

    public synchronized File remove(int fd) {
        if (fd < 0 || fd >= slots.length) return null;
        File f = slots[fd];
        slots[fd] = null;
        return f;
    }

    public synchronized int capacity() { return slots.length; }

    public synchronized int openCount() {
        int n = 0;
        for (File f : slots) if (f != null) n++;
        return n;
    }
}
