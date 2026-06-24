package org.minikernel.kernel.fs.ramfs;

import org.minikernel.kernel.fs.FileSystem;
import org.minikernel.kernel.fs.Inode;
import org.minikernel.kernel.fs.SuperBlock;

import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory filesystem driver. Inodes are pure Java objects backed by
 * heap byte arrays. There is no persistence and no quota; ideal as the
 * root filesystem for our toy kernel, matching the role ramfs plays as
 * the {@code rootfs} on a real Linux boot.
 */
public final class RamFs implements FileSystem {

    private final AtomicLong nextIno = new AtomicLong(1);

    @Override
    public String name() { return "ramfs"; }

    @Override
    public SuperBlock mount() {
        Inode root = new RamDirInode(nextIno.getAndIncrement(), 0755);
        return new SuperBlock(this, root);
    }

    /** Factory for new regular-file inodes used by VfsCore on create. */
    public RamRegularInode newRegular(int mode) {
        return new RamRegularInode(nextIno.getAndIncrement(), mode);
    }

    /** Factory for new directory inodes used by VfsCore on mkdir. */
    public RamDirInode newDirectory(int mode) {
        return new RamDirInode(nextIno.getAndIncrement(), mode);
    }
}
