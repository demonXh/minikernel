package org.minikernel.kernel.fs;

/**
 * VFS inode: the on-disk identity of a file. Sub-classes provide the
 * concrete data storage (in RAM, on disk, etc.). Linux analogue:
 * {@code struct inode} + {@code inode_operations}.
 */
public abstract class Inode {

    private final long ino;
    private final InodeKind kind;
    protected long size;
    protected int mode;            // permission bits (rwxrwxrwx)
    protected long ctimeMillis;
    protected long mtimeMillis;

    protected Inode(long ino, InodeKind kind, int mode) {
        this.ino = ino;
        this.kind = kind;
        this.mode = mode;
        long now = System.currentTimeMillis();
        this.ctimeMillis = now;
        this.mtimeMillis = now;
    }

    public long ino() { return ino; }
    public InodeKind kind() { return kind; }
    public long size() { return size; }
    public int mode() { return mode; }
    public long ctime() { return ctimeMillis; }
    public long mtime() { return mtimeMillis; }

    /** Read up to {@code dst.length} bytes starting at {@code offset}. Returns bytes read, or 0 at EOF. */
    public abstract int read(long offset, byte[] dst, int dstOff, int len);

    /** Write {@code len} bytes from {@code src} starting at {@code offset}. Returns bytes written. */
    public abstract int write(long offset, byte[] src, int srcOff, int len);

    /** Truncate or extend to the given size; default no-op for directories. */
    public void truncate(long newSize) {
        throw new UnsupportedOperationException("truncate not supported on " + kind);
    }
}
