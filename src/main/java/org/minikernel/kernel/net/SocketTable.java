package org.minikernel.kernel.net;

import java.util.Arrays;

/**
 * Per-process socket descriptor table. Sockets live in a separate index
 * space from file fds in this toy kernel (real Linux unifies them). User
 * code uses negative-discriminated handles to keep the two apart.
 */
public final class SocketTable {

    public static final int DEFAULT_CAPACITY = 16;
    /** Socket handles are returned as {@code sd | SOCKFD_FLAG} to distinguish from file fds. */
    public static final int SOCKFD_FLAG = 0x40000000;

    private UdpSocket[] slots;

    public SocketTable() { this(DEFAULT_CAPACITY); }
    public SocketTable(int capacity) { this.slots = new UdpSocket[capacity]; }

    public synchronized int install(UdpSocket s) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) { slots[i] = s; return i | SOCKFD_FLAG; }
        }
        int oldLen = slots.length;
        slots = Arrays.copyOf(slots, oldLen * 2);
        slots[oldLen] = s;
        return oldLen | SOCKFD_FLAG;
    }

    public synchronized UdpSocket get(int handle) {
        if ((handle & SOCKFD_FLAG) == 0) return null;
        int idx = handle & ~SOCKFD_FLAG;
        if (idx < 0 || idx >= slots.length) return null;
        return slots[idx];
    }

    public synchronized UdpSocket remove(int handle) {
        if ((handle & SOCKFD_FLAG) == 0) return null;
        int idx = handle & ~SOCKFD_FLAG;
        if (idx < 0 || idx >= slots.length) return null;
        UdpSocket s = slots[idx];
        slots[idx] = null;
        return s;
    }

    public static boolean isSocketHandle(int handle) {
        return (handle & SOCKFD_FLAG) != 0;
    }
}
