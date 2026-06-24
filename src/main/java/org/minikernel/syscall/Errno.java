package org.minikernel.syscall;

/**
 * Linux-style negative errno return codes. Only the few we use today.
 */
public final class Errno {
    public static final int EPERM   = 1;
    public static final int ENOENT  = 2;
    public static final int EINTR   = 4;
    public static final int EBADF   = 9;
    public static final int ECHILD  = 10;
    public static final int ENOMEM  = 12;
    public static final int EFAULT  = 14;
    public static final int EINVAL  = 22;
    public static final int ENOSYS  = 38;

    private Errno() {}

    public static long fail(int errno) { return -((long) errno); }
}
