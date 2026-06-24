package org.minikernel.kernel.sched;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RunQueueTest {

    @Test
    void fifoOrdering() {
        RunQueue rq = new RunQueue();
        TaskStruct a = new TaskStruct(1, "a", null, () -> {});
        TaskStruct b = new TaskStruct(2, "b", null, () -> {});
        TaskStruct c = new TaskStruct(3, "c", null, () -> {});
        rq.enqueue(a);
        rq.enqueue(b);
        rq.enqueue(c);
        assertEquals(3, rq.size());
        assertSame(a, rq.pickNext());
        assertSame(b, rq.pickNext());
        assertSame(c, rq.pickNext());
        assertNull(rq.pickNext());
        assertTrue(rq.isEmpty());
    }

    @Test
    void doubleEnqueueNoOp() {
        RunQueue rq = new RunQueue();
        TaskStruct a = new TaskStruct(1, "a", null, () -> {});
        rq.enqueue(a);
        rq.enqueue(a);
        assertEquals(1, rq.size());
    }
}
