package org.minikernel.kernel.fs;

import org.minikernel.core.log.KLog;
import org.minikernel.kernel.fs.ramfs.RamDirInode;
import org.minikernel.kernel.fs.ramfs.RamFs;
import org.minikernel.kernel.fs.ramfs.RamRegularInode;

/**
 * VFS frontend: mount table (single root for now) and path-name resolution.
 *
 * <p>Provides the kernel-facing API used by file-related syscalls:
 * {@link #lookup(String)}, {@link #open(String, int)}, {@link #mkdir(String, int)}
 * and {@link #unlink(String)}. Resolution is purely absolute (paths must
 * start with {@code /}); per-process CWD will arrive in a later iteration.
 */
public final class VfsCore {

    private SuperBlock rootSb;
    private Dentry rootDentry;

    /** Mount the root filesystem. Must be called once at boot. */
    public void mountRoot(FileSystem fs) {
        if (rootSb != null) throw new IllegalStateException("root already mounted");
        rootSb = fs.mount();
        rootDentry = new Dentry("", null, rootSb.rootInode());
        rootSb.setRootDentry(rootDentry);
        KLog.info("VFS: mounted %s as /", fs.name());
    }

    public Dentry root() { return rootDentry; }
    public SuperBlock rootSuperBlock() { return rootSb; }

    /** Resolve an absolute path to a Dentry, or null if any component is missing. */
    public synchronized Dentry lookup(String path) {
        if (rootDentry == null) throw new IllegalStateException("no root mounted");
        if (path == null || !path.startsWith("/")) return null;
        if (path.equals("/")) return rootDentry;
        String[] parts = path.split("/");
        Dentry d = rootDentry;
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!d.isDir()) return null;
            d = d.lookup(p);
            if (d == null) return null;
        }
        return d;
    }

    /**
     * Open a file. If {@code O_CREAT} is in flags and the target does not
     * exist, a regular file inode is created in its parent directory.
     */
    public synchronized File open(String path, int flags) {
        Dentry d = lookup(path);
        if (d == null) {
            if ((flags & OpenFlags.O_CREAT) == 0) return null;
            d = createRegular(path, 0644);
            if (d == null) return null;
        } else if ((flags & OpenFlags.O_DIRECTORY) != 0 && !d.isDir()) {
            return null;
        }
        if ((flags & OpenFlags.O_TRUNC) != 0 && d.inode().kind() == InodeKind.REGULAR) {
            d.inode().truncate(0);
        }
        return new File(d, flags);
    }

    public synchronized Dentry mkdir(String path, int mode) {
        Dentry parent = parentOf(path);
        if (parent == null || !parent.isDir()) return null;
        String name = basename(path);
        if (parent.lookup(name) != null) return null;
        RamFs ramfs = (RamFs) rootSb.fileSystem();
        RamDirInode inode = ramfs.newDirectory(mode);
        Dentry child = new Dentry(name, parent, inode);
        parent.addChild(child);
        return child;
    }

    /** Remove a regular-file entry; returns true on success. */
    public synchronized boolean unlink(String path) {
        Dentry d = lookup(path);
        if (d == null || d.isDir()) return false;
        Dentry parent = d.parent();
        if (parent == null) return false;
        return parent.children().remove(d.name(), d);
    }

    private Dentry createRegular(String path, int mode) {
        Dentry parent = parentOf(path);
        if (parent == null || !parent.isDir()) return null;
        String name = basename(path);
        if (parent.lookup(name) != null) return null;
        RamFs ramfs = (RamFs) rootSb.fileSystem();
        RamRegularInode inode = ramfs.newRegular(mode);
        Dentry child = new Dentry(name, parent, inode);
        parent.addChild(child);
        return child;
    }

    private Dentry parentOf(String path) {
        int slash = path.lastIndexOf('/');
        if (slash < 0) return null;
        String parentPath = slash == 0 ? "/" : path.substring(0, slash);
        return lookup(parentPath);
    }

    private static String basename(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
