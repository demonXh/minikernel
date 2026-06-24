package org.minikernel.core;

import org.junit.jupiter.api.Test;
import org.minikernel.core.ds.ListHead;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ListHeadTest {

    @Test
    void emptyListIsEmpty() {
        ListHead<String> head = new ListHead<>();
        assertTrue(head.isEmpty());
        assertEquals(0, head.size());
    }

    @Test
    void addLastPreservesOrder() {
        ListHead<String> head = new ListHead<>();
        head.addLast(new ListHead<>("a"));
        head.addLast(new ListHead<>("b"));
        head.addLast(new ListHead<>("c"));

        List<String> seen = new ArrayList<>();
        head.iterable().forEach(seen::add);
        assertEquals(List.of("a", "b", "c"), seen);
        assertEquals(3, head.size());
    }

    @Test
    void addFirstReversesOrder() {
        ListHead<Integer> head = new ListHead<>();
        head.addFirst(new ListHead<>(1));
        head.addFirst(new ListHead<>(2));
        head.addFirst(new ListHead<>(3));

        List<Integer> seen = new ArrayList<>();
        head.iterable().forEach(seen::add);
        assertEquals(List.of(3, 2, 1), seen);
    }

    @Test
    void removeUnlinks() {
        ListHead<String> head = new ListHead<>();
        ListHead<String> a = new ListHead<>("a");
        ListHead<String> b = new ListHead<>("b");
        head.addLast(a);
        head.addLast(b);
        a.remove();

        List<String> seen = new ArrayList<>();
        head.iterable().forEach(seen::add);
        assertEquals(List.of("b"), seen);
        assertTrue(a.isEmpty());
    }
}
