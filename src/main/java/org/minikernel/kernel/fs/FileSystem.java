package org.minikernel.kernel.fs;

/**
 * Filesystem driver: knows how to materialise a {@link SuperBlock} from
 * some backing store (RAM, disk, network, ...). Linux analogue:
 * {@code struct file_system_type}.
 */
public interface FileSystem {

    /** Human-readable name, e.g. {@code "ramfs"}. */
    String name();

    /** Build and return a fresh superblock. Called by {@code VfsCore.mount}. */
    SuperBlock mount();
}
