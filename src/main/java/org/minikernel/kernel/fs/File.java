package org.minikernel.kernel.fs;

/**
 * Open file description: the kernel-side state of an opened file as seen
 * by user space through a file descriptor. Multiple file descriptors (e.g.
 * across {@code dup}) can share a single {@code File}. Linux analogue:
 * {@code struct file}.
 */
public final class File {

    private final Dentry dentry;
    private final int flags;
    private long offset;
    private int refCount = 1;

    public File(Dentry dentry, int flags) {
        this.dentry = dentry;
        this.flags = flags;
        if ((flags & OpenFlags.O_APPEND) != 0) this.offset = dentry.inode().size();
    }

    public Dentry dentry() { return dentry; }
    public Inode inode() { return dentry.inode(); }
    public int flags() { return flags; }
    public long offset() { return offset; }
    public void setOffset(long o) { this.offset = o; }

    public synchronized int retain() { return ++refCount; }
    public synchronized int release() { return --refCount; }
    public synchronized int refCount() { return refCount; }

    public int read(byte[] dst, int dstOff, int len) {
        if (!OpenFlags.isReadable(flags)) throw new IllegalStateException("file not readable");
        int n = inode().read(offset, dst, dstOff, len);
        offset += n;
        return n;
    }

    public int write(byte[] src, int srcOff, int len) {
        if (!OpenFlags.isWritable(flags)) throw new IllegalStateException("file not writable");
        if ((flags & OpenFlags.O_APPEND) != 0) offset = inode().size();
        int n = inode().write(offset, src, srcOff, len);
        offset += n;
        return n;
    }
}
