package org.minikernel.kernel.mm;

/**
 * Page Table Entry flags (bit positions match Linux PTE conventions in spirit).
 */
public final class PteFlags {
    public static final int PRESENT = 1;
    public static final int WRITE   = 1 << 1;
    public static final int USER    = 1 << 2;
    public static final int DIRTY   = 1 << 3;
    public static final int ACCESSED = 1 << 4;

    private PteFlags() {}

    public static boolean has(int pte, int flag) { return (pte & flag) != 0; }
}
