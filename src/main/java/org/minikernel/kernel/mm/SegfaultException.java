package org.minikernel.kernel.mm;

/**
 * Thrown when virtual-to-physical translation fails and cannot be resolved
 * via the VMA list. Analogue of SIGSEGV in user space.
 */
public class SegfaultException extends RuntimeException {

    public SegfaultException(String message) { super(message); }
}
