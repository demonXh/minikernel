package org.minikernel.hal;

/**
 * Block device abstraction; placeholder for iteration 5 (file system).
 *
 * <p>The contract mirrors the Linux block layer's request interface: read
 * or write a fixed-size block identified by its zero-based index. The unit
 * size is exposed via {@link #blockSize()} so callers can compute offsets.
 */
public interface VirtualDisk {

    int blockSize();

    int blockCount();

    void readBlock(int blockIndex, byte[] dst);

    void writeBlock(int blockIndex, byte[] src);
}
