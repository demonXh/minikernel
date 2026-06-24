package org.minikernel.hal;

/**
 * CPU register file snapshot.
 *
 * <p>Mirrors the role of {@code struct pt_regs} in Linux: a place to dump
 * the register set on context switch / interrupt entry. We model a minimal
 * RISC-like set: a program counter, a stack pointer, and 8 general-purpose
 * registers. The class is mutable so a CPU thread can update its registers
 * in place during the fetch-execute loop.
 */
public final class Registers {

    public long pc;
    public long sp;
    public final long[] gpr = new long[8];

    public Registers copy() {
        Registers r = new Registers();
        r.pc = this.pc;
        r.sp = this.sp;
        System.arraycopy(this.gpr, 0, r.gpr, 0, this.gpr.length);
        return r;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Registers{pc=").append(pc).append(", sp=").append(sp);
        for (int i = 0; i < gpr.length; i++) sb.append(", r").append(i).append('=').append(gpr[i]);
        return sb.append('}').toString();
    }
}
