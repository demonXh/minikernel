package org.minikernel.kernel.fs.ramfs;

import org.minikernel.kernel.fs.Inode;
import org.minikernel.kernel.fs.InodeKind;

import java.util.Arrays;

/**
 * Regular-file inode backed by an in-memory growable byte buffer. Suitable
 * for ramfs / tmpfs-style semantics.
 */
public final class RamRegularInode extends Inode {

    private byte[] data = new byte[0];

    public RamRegularInode(long ino, int mode) {
        super(ino, InodeKind.REGULAR, mode);
    }

    @Override
    public synchronized int read(long offset, byte[] dst, int dstOff, int len) {
        if (offset >= data.length) return 0;
        int available = (int) Math.min(len, data.length - offset);
        System.arraycopy(data, (int) offset, dst, dstOff, available);
        return available;
    }

    @Override
    public synchronized int write(long offset, byte[] src, int srcOff, int len) {
        long required = offset + len;
        if (required > data.length) {
            data = Arrays.copyOf(data, (int) required);
        }
        System.arraycopy(src, srcOff, data, (int) offset, len);
        size = data.length;
        mtimeMillis = System.currentTimeMillis();
        return len;
    }

    @Override
    public synchronized void truncate(long newSize) {
        data = Arrays.copyOf(data, (int) newSize);
        size = data.length;
        mtimeMillis = System.currentTimeMillis();
    }
}
