package org.minikernel.kernel.fs.ramfs;

import org.minikernel.kernel.fs.Inode;
import org.minikernel.kernel.fs.InodeKind;

/**
 * Directory inode for ramfs. Holds no data of its own; the dentry tree
 * carries the actual child mapping. Read/write are illegal operations.
 */
public final class RamDirInode extends Inode {

    public RamDirInode(long ino, int mode) {
        super(ino, InodeKind.DIRECTORY, mode);
    }

    @Override
    public int read(long offset, byte[] dst, int dstOff, int len) {
        throw new UnsupportedOperationException("cannot read() a directory");
    }

    @Override
    public int write(long offset, byte[] src, int srcOff, int len) {
        throw new UnsupportedOperationException("cannot write() a directory");
    }
}
