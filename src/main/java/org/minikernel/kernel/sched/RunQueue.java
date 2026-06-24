package org.minikernel.kernel.sched;

import org.minikernel.core.ds.ListHead;

/**
 * Per-CPU run queue (single-CPU for now). FIFO ordering implements round-robin.
 *
 * <p>Backed by an intrusive {@link ListHead}: tasks are added by their own
 * {@code runNode}, not wrapped in heap-allocated nodes, mirroring the
 * cache-friendly pattern in Linux's {@code struct rq}.
 */
public final class RunQueue {

    private final ListHead<TaskStruct> head = new ListHead<>();

    public synchronized void enqueue(TaskStruct t) {
        if (t.runNode.next != t.runNode) return;
        head.addLast(t.runNode);
    }

    public synchronized TaskStruct pickNext() {
        ListHead<TaskStruct> first = head.next;
        if (first == head) return null;
        TaskStruct t = first.owner();
        first.remove();
        return t;
    }

    public synchronized int size() { return head.size(); }

    public synchronized boolean isEmpty() { return head.isEmpty(); }
}
