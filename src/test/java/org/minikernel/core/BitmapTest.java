package org.minikernel.core;

import org.junit.jupiter.api.Test;
import org.minikernel.core.ds.Bitmap;

import static org.junit.jupiter.api.Assertions.*;

class BitmapTest {

    @Test
    void setClearGet() {
        Bitmap b = new Bitmap(16);
        assertFalse(b.get(3));
        b.set(3);
        assertTrue(b.get(3));
        b.clear(3);
        assertFalse(b.get(3));
    }

    @Test
    void allocFirstFreeProgresses() {
        Bitmap b = new Bitmap(4);
        assertEquals(0, b.allocFirstFree());
        assertEquals(1, b.allocFirstFree());
        assertEquals(2, b.allocFirstFree());
        assertEquals(3, b.allocFirstFree());
        assertEquals(-1, b.allocFirstFree());
    }

    @Test
    void allocFirstFreeRecyclesClearedBits() {
        Bitmap b = new Bitmap(4);
        b.allocFirstFree();
        b.allocFirstFree();
        b.clear(0);
        assertEquals(0, b.allocFirstFree());
    }

    @Test
    void boundsChecked() {
        Bitmap b = new Bitmap(8);
        assertThrows(IndexOutOfBoundsException.class, () -> b.set(8));
        assertThrows(IndexOutOfBoundsException.class, () -> b.get(-1));
    }
}
