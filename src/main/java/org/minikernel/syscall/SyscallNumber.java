package org.minikernel.syscall;

/**
 * System call numbers. Values are stable identifiers used at the user/kernel
 * boundary; mirrors the role of {@code __NR_xxx} constants in Linux's
 * {@code unistd.h}.
 */
public enum SyscallNumber {

    READ(0),
    WRITE(1),
    GETPID(2),
    FORK(3),
    EXIT(4),
    WAITPID(5),
    BRK(6),
    SLEEP(7),
    YIELD(8),
    OPEN(9),
    CLOSE(10),
    LSEEK(11),
    MKDIR(12),
    UNLINK(13),
    SOCKET(14),
    BIND(15),
    SENDTO(16),
    RECVFROM(17);

    private final int number;

    SyscallNumber(int number) { this.number = number; }

    public int number() { return number; }

    public static SyscallNumber of(int number) {
        for (SyscallNumber n : values()) if (n.number == number) return n;
        throw new IllegalArgumentException("unknown syscall " + number);
    }

    public static int maxNumber() {
        int max = 0;
        for (SyscallNumber n : values()) if (n.number > max) max = n.number;
        return max;
    }
}
