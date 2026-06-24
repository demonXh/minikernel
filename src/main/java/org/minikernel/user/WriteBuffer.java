package org.minikernel.user;

import org.minikernel.MiniKernel;

/**
 * Tiny user-side scratch buffer used by {@link Libc#write(int, String)} to
 * marshal Java strings into physical memory the kernel can read. In a real
 * kernel the user's stack would hold the buffer and the syscall would pass
 * its virtual address; here we cheat and use a fixed kernel-reserved page
 * because not all user tasks have set up an MM yet.
 */
public final class WriteBuffer {

    /** Physical-memory offset where outgoing syscall payloads are placed. */
    public static final long SCRATCH_ADDR = 0;

    private static volatile MiniKernel kernel;
    private static final Object LOCK = new Object();

    private WriteBuffer() {}

    public static void bind(MiniKernel k) { kernel = k; }

    public static long put(byte[] bytes) {
        MiniKernel k = kernel;
        if (k == null) throw new IllegalStateException("WriteBuffer is not bound");
        synchronized (LOCK) {
            k.memory().writeBytes(SCRATCH_ADDR, bytes, 0, bytes.length);
        }
        return SCRATCH_ADDR;
    }

    /** Put a NUL-terminated copy of {@code s} into the scratch buffer. */
    public static long putCString(String s) {
        byte[] raw = s.getBytes();
        byte[] withNul = new byte[raw.length + 1];
        System.arraycopy(raw, 0, withNul, 0, raw.length);
        return put(withNul);
    }

    /** Read {@code len} bytes back from the scratch buffer as a String. */
    public static String fetch(int len) {
        MiniKernel k = kernel;
        if (k == null) throw new IllegalStateException("WriteBuffer is not bound");
        byte[] dst = new byte[len];
        synchronized (LOCK) {
            k.memory().readBytes(SCRATCH_ADDR, dst, 0, len);
        }
        return new String(dst);
    }
}
