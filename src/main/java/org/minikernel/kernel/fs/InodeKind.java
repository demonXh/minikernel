package org.minikernel.kernel.fs;

/** Type of a VFS inode. Mirrors {@code S_IFREG} / {@code S_IFDIR} bits in Linux. */
public enum InodeKind {
    REGULAR,
    DIRECTORY
}
