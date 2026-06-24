package org.minikernel.core.interrupt;

import org.minikernel.hal.VirtualCpu;

/**
 * Top-half interrupt service routine. Equivalent to a Linux IRQ handler.
 *
 * <p>Implementations should be short and non-blocking; defer heavy work to
 * a softirq via {@link InterruptController#raiseSoftIrq(Runnable)}.
 */
@FunctionalInterface
public interface InterruptHandler {

    void handle(int vector, VirtualCpu cpu);
}
