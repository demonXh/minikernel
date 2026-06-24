package org.minikernel.core.ds;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Intrusive doubly-linked list, modelled on Linux's {@code struct list_head}.
 *
 * <p>An object that wants to be linkable embeds (or owns) a {@code ListHead<T>}
 * sentinel and passes itself as the {@code owner}. The list is circular and
 * uses a sentinel node as its head, matching the canonical Linux pattern:
 * {@code list_for_each}, {@code list_add}, {@code list_del}, etc.
 *
 * @param <T> the type of the payload owner. Sentinel nodes have a {@code null}
 *            owner; payload nodes carry a non-null reference.
 */
public final class ListHead<T> {

    public ListHead<T> prev;
    public ListHead<T> next;
    private final T owner;

    /** Construct a sentinel head node. */
    public ListHead() {
        this.owner = null;
        this.prev = this;
        this.next = this;
    }

    /** Construct a payload node that wraps {@code owner}. */
    public ListHead(T owner) {
        this.owner = owner;
        this.prev = this;
        this.next = this;
    }

    public T owner() { return owner; }

    public boolean isEmpty() { return next == this; }

    /** Insert {@code node} at the head of this list (after the sentinel). */
    public void addFirst(ListHead<T> node) {
        link(this, node, this.next);
    }

    /** Insert {@code node} at the tail of this list (before the sentinel). */
    public void addLast(ListHead<T> node) {
        link(this.prev, node, this);
    }

    /** Detach this node from whichever list it currently belongs to. */
    public void remove() {
        this.prev.next = this.next;
        this.next.prev = this.prev;
        this.prev = this;
        this.next = this;
    }

    private static <T> void link(ListHead<T> a, ListHead<T> n, ListHead<T> b) {
        n.prev = a;
        n.next = b;
        a.next = n;
        b.prev = n;
    }

    /** Iterate payload owners from head to tail. */
    public Iterable<T> iterable() {
        return () -> new Iterator<>() {
            ListHead<T> cursor = ListHead.this.next;
            @Override public boolean hasNext() { return cursor != ListHead.this; }
            @Override public T next() {
                if (cursor == ListHead.this) throw new NoSuchElementException();
                T v = cursor.owner;
                cursor = cursor.next;
                return v;
            }
        };
    }

    public int size() {
        int n = 0;
        for (ListHead<T> c = this.next; c != this; c = c.next) n++;
        return n;
    }
}
