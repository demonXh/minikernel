package org.minikernel.kernel.fs;

/** Bit flags accepted by {@code open()}, matching common Linux O_* values. */
public final class OpenFlags {
    public static final int O_RDONLY = 0;
    public static final int O_WRONLY = 1;
    public static final int O_RDWR   = 2;
    public static final int O_CREAT  = 0x40;
    public static final int O_TRUNC  = 0x200;
    public static final int O_APPEND = 0x400;
    public static final int O_DIRECTORY = 0x10000;

    private OpenFlags() {}

    public static boolean isWritable(int flags) {
        int access = flags & 3;
        return access == O_WRONLY || access == O_RDWR;
    }

    public static boolean isReadable(int flags) {
        int access = flags & 3;
        return access == O_RDONLY || access == O_RDWR;
    }
}
