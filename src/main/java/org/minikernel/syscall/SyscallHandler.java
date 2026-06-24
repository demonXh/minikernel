package org.minikernel.syscall;

/**
 * One system-call implementation. Returns a 64-bit value that becomes the
 * syscall's return value to user space. Errors are conventionally returned
 * as a negative errno (Linux style); successful results are non-negative.
 */
@FunctionalInterface
public interface SyscallHandler {

    long handle(SyscallContext ctx);
}
