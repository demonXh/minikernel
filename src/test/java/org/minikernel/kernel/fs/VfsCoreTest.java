package org.minikernel.kernel.fs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.minikernel.kernel.fs.ramfs.RamFs;

import static org.junit.jupiter.api.Assertions.*;

class VfsCoreTest {

    private VfsCore vfs;

    @BeforeEach
    void setUp() {
        vfs = new VfsCore();
        vfs.mountRoot(new RamFs());
    }

    @Test
    void rootResolves() {
        Dentry r = vfs.lookup("/");
        assertNotNull(r);
        assertTrue(r.isDir());
        assertEquals("/", r.absolutePath());
    }

    @Test
    void openCreatesRegularFile() {
        File f = vfs.open("/foo.txt", OpenFlags.O_CREAT | OpenFlags.O_RDWR);
        assertNotNull(f);
        assertEquals(InodeKind.REGULAR, f.inode().kind());
        assertSame(f.dentry(), vfs.lookup("/foo.txt"));
    }

    @Test
    void openWithoutCreatFailsForMissing() {
        assertNull(vfs.open("/missing.txt", OpenFlags.O_RDONLY));
    }

    @Test
    void mkdirAndNestedFile() {
        assertNotNull(vfs.mkdir("/etc", 0755));
        File f = vfs.open("/etc/config", OpenFlags.O_CREAT | OpenFlags.O_WRONLY);
        assertNotNull(f);
        assertSame(f.dentry().parent(), vfs.lookup("/etc"));
    }

    @Test
    void writeReadRoundTrip() {
        File f = vfs.open("/data", OpenFlags.O_CREAT | OpenFlags.O_RDWR);
        byte[] payload = "minikernel rocks".getBytes();
        assertEquals(payload.length, f.write(payload, 0, payload.length));
        f.setOffset(0);
        byte[] back = new byte[payload.length];
        assertEquals(payload.length, f.read(back, 0, back.length));
        assertArrayEquals(payload, back);
    }

    @Test
    void truncateFlag() {
        File f = vfs.open("/t", OpenFlags.O_CREAT | OpenFlags.O_RDWR);
        f.write(new byte[]{1, 2, 3, 4, 5}, 0, 5);
        File f2 = vfs.open("/t", OpenFlags.O_RDWR | OpenFlags.O_TRUNC);
        assertEquals(0, f2.inode().size());
    }

    @Test
    void appendFlagSeeksToEnd() {
        File f1 = vfs.open("/a", OpenFlags.O_CREAT | OpenFlags.O_RDWR);
        f1.write("abc".getBytes(), 0, 3);
        File f2 = vfs.open("/a", OpenFlags.O_RDWR | OpenFlags.O_APPEND);
        f2.write("DEF".getBytes(), 0, 3);
        File r = vfs.open("/a", OpenFlags.O_RDONLY);
        byte[] all = new byte[16];
        int n = r.read(all, 0, all.length);
        assertEquals(6, n);
        assertEquals("abcDEF", new String(all, 0, n));
    }

    @Test
    void unlinkRemovesFile() {
        vfs.open("/gone", OpenFlags.O_CREAT | OpenFlags.O_WRONLY);
        assertTrue(vfs.unlink("/gone"));
        assertNull(vfs.lookup("/gone"));
        assertFalse(vfs.unlink("/gone"));
    }

    @Test
    void unlinkRefusesDirectory() {
        vfs.mkdir("/d", 0755);
        assertFalse(vfs.unlink("/d"));
    }

    @Test
    void duplicateMkdirRejected() {
        assertNotNull(vfs.mkdir("/x", 0755));
        assertNull(vfs.mkdir("/x", 0755));
    }
}
