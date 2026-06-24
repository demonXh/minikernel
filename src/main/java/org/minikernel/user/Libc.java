package org.minikernel.user;

import org.minikernel.syscall.ForkPayload;
import org.minikernel.syscall.SyscallNumber;
import org.minikernel.syscall.Trap;

/**
 * "libc" facade for user programs: friendly wrappers around the raw
 * {@link Trap#syscall(int, long...)} interface. User code is expected to
 * call these only, never reach into kernel packages directly.
 */
public final class Libc {

    public static final int STDOUT = 1;
    public static final int STDERR = 2;

    private Libc() {}

    /** Print a string to a console fd. Returns bytes written or -errno. */
    public static long write(int fd, String s) {
        byte[] bytes = s.getBytes();
        // Stage the bytes in a scratch buffer at the bottom of the heap VMA.
        // We exploit BRK to materialize a page and put bytes there. For a
        // pure-string write we can just stash them in physical memory at a
        // fixed scratch address (frame 0 is owned by the kernel image, so
        // we use offset 0 which works for this simulator).
        long vaddr = WriteBuffer.put(bytes);
        return Trap.syscall(SyscallNumber.WRITE.number(), fd, vaddr, bytes.length);
    }

    public static int getpid() {
        return (int) Trap.syscall(SyscallNumber.GETPID.number());
    }

    /**
     * Fork. The {@code childBody} runs in the new task. Returns the child's
     * pid (parent) or 0 (child, conventionally) - but since the child's
     * execution starts directly in {@code childBody}, only the parent path
     * really returns from {@code fork()}.
     */
    public static int fork(Runnable childBody) {
        ForkPayload.stage(childBody);
        return (int) Trap.syscall(SyscallNumber.FORK.number());
    }

    public static void exit(int code) {
        Trap.syscall(SyscallNumber.EXIT.number(), code);
    }

    /** Returns (pid<<32)|exitCode, or -errno on failure. */
    public static long waitpid() {
        return Trap.syscall(SyscallNumber.WAITPID.number());
    }

    public static long brk(int extraPages) {
        return Trap.syscall(SyscallNumber.BRK.number(), extraPages);
    }

    public static void sleep(long millis) {
        Trap.syscall(SyscallNumber.SLEEP.number(), millis);
    }

    public static void yield() {
        Trap.syscall(SyscallNumber.YIELD.number());
    }

    /* ---------------- file I/O ---------------- */

    public static long open(String path, int flags) {
        long vaddr = WriteBuffer.putCString(path);
        return Trap.syscall(SyscallNumber.OPEN.number(), vaddr, flags);
    }

    public static long close(int fd) {
        return Trap.syscall(SyscallNumber.CLOSE.number(), fd);
    }

    public static long writeFd(int fd, String s) {
        byte[] bytes = s.getBytes();
        long vaddr = WriteBuffer.put(bytes);
        return Trap.syscall(SyscallNumber.WRITE.number(), fd, vaddr, bytes.length);
    }

    /** Read up to {@code maxLen} bytes from fd and return them as a String. */
    public static String readAll(int fd, int maxLen) {
        long vaddr = WriteBuffer.SCRATCH_ADDR;
        long n = Trap.syscall(SyscallNumber.READ.number(), fd, vaddr, maxLen);
        if (n <= 0) return "";
        return WriteBuffer.fetch((int) n);
    }

    public static long lseek(int fd, long offset, int whence) {
        return Trap.syscall(SyscallNumber.LSEEK.number(), fd, offset, whence);
    }

    public static long mkdir(String path, int mode) {
        long vaddr = WriteBuffer.putCString(path);
        return Trap.syscall(SyscallNumber.MKDIR.number(), vaddr, mode);
    }

    public static long unlink(String path) {
        long vaddr = WriteBuffer.putCString(path);
        return Trap.syscall(SyscallNumber.UNLINK.number(), vaddr);
    }
}
