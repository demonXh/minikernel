package org.minikernel.kernel.fs;

import org.junit.jupiter.api.Test;
import org.minikernel.kernel.fs.ramfs.RamFs;

import static org.junit.jupiter.api.Assertions.*;

class FdTableTest {

    @Test
    void installAssignsIncreasingFds() {
        FdTable t = new FdTable(4);
        VfsCore vfs = new VfsCore();
        vfs.mountRoot(new RamFs());
        File a = vfs.open("/a", OpenFlags.O_CREAT | OpenFlags.O_RDWR);
        File b = vfs.open("/b", OpenFlags.O_CREAT | OpenFlags.O_RDWR);
        assertEquals(0, t.install(a));
        assertEquals(1, t.install(b));
        assertEquals(2, t.openCount());
    }

    @Test
    void removeFreesSlot() {
        FdTable t = new FdTable(4);
        VfsCore vfs = new VfsCore();
        vfs.mountRoot(new RamFs());
        File a = vfs.open("/a", OpenFlags.O_CREAT | OpenFlags.O_RDWR);
        int fd = t.install(a);
        assertSame(a, t.remove(fd));
        assertNull(t.get(fd));
    }

    @Test
    void growsWhenFull() {
        FdTable t = new FdTable(2);
        VfsCore vfs = new VfsCore();
        vfs.mountRoot(new RamFs());
        int fd1 = t.install(vfs.open("/1", OpenFlags.O_CREAT | OpenFlags.O_RDWR));
        int fd2 = t.install(vfs.open("/2", OpenFlags.O_CREAT | OpenFlags.O_RDWR));
        int fd3 = t.install(vfs.open("/3", OpenFlags.O_CREAT | OpenFlags.O_RDWR));
        assertEquals(2, fd3);
        assertTrue(t.capacity() >= 3);
    }
}
