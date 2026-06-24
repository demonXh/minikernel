package org.minikernel.kernel.fs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Directory entry: a (name -> inode) binding within a parent directory.
 * Mirrors {@code struct dentry} in Linux.
 *
 * <p>For simplicity each {@code Dentry} carries the name, a reference to
 * the parent dentry, and (for directories) a child map of names to
 * dentries. The dentry tree forms the name-space view atop the inode
 * graph.
 */
public final class Dentry {

    private final String name;
    private final Dentry parent;
    private final Inode inode;
    /** Children map for directory dentries; null for regular files. */
    private final Map<String, Dentry> children;

    public Dentry(String name, Dentry parent, Inode inode) {
        this.name = name;
        this.parent = parent;
        this.inode = inode;
        this.children = inode.kind() == InodeKind.DIRECTORY ? new LinkedHashMap<>() : null;
    }

    public String name() { return name; }
    public Dentry parent() { return parent; }
    public Inode inode() { return inode; }
    public Map<String, Dentry> children() { return children; }

    public boolean isDir() { return inode.kind() == InodeKind.DIRECTORY; }

    public Dentry addChild(Dentry child) {
        if (!isDir()) throw new IllegalStateException("cannot add child to non-dir " + name);
        children.put(child.name, child);
        return child;
    }

    public Dentry lookup(String childName) {
        if (!isDir()) return null;
        return children.get(childName);
    }

    public String absolutePath() {
        if (parent == null) return "/";
        StringBuilder sb = new StringBuilder();
        for (Dentry d = this; d.parent != null; d = d.parent) {
            sb.insert(0, d.name);
            sb.insert(0, '/');
        }
        return sb.toString();
    }
}
