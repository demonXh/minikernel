package org.minikernel.kernel.fs;

/**
 * Superblock: the in-core descriptor of a mounted filesystem. Holds the
 * root inode (and root dentry once mounted) and the owning filesystem
 * driver. Linux analogue: {@code struct super_block}.
 */
public final class SuperBlock {

    private final FileSystem fileSystem;
    private final Inode rootInode;
    private Dentry rootDentry;

    public SuperBlock(FileSystem fs, Inode rootInode) {
        this.fileSystem = fs;
        this.rootInode = rootInode;
    }

    public FileSystem fileSystem() { return fileSystem; }
    public Inode rootInode() { return rootInode; }
    public Dentry rootDentry() { return rootDentry; }

    /** Called by VfsCore at mount time to materialise the root dentry. */
    void setRootDentry(Dentry root) { this.rootDentry = root; }
}
