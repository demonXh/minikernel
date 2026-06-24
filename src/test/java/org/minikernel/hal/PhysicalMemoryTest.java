package org.minikernel.hal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhysicalMemoryTest {

    @Test
    void rejectsBadSize() {
        assertThrows(IllegalArgumentException.class, () -> new PhysicalMemory(0));
        assertThrows(IllegalArgumentException.class, () -> new PhysicalMemory(1234));
    }

    @Test
    void allocAndFreeFrames() {
        PhysicalMemory mem = new PhysicalMemory(4 * PhysicalMemory.PAGE_SIZE);
        assertEquals(4, mem.totalFrames());
        assertEquals(4, mem.freeFrames());

        int a = mem.allocFrame();
        int b = mem.allocFrame();
        assertNotEquals(a, b);
        assertEquals(2, mem.freeFrames());

        mem.freeFrame(a);
        assertEquals(3, mem.freeFrames());

        assertThrows(IllegalStateException.class, () -> mem.freeFrame(a));
        assertThrows(IndexOutOfBoundsException.class, () -> mem.freeFrame(b + 100_000));
    }

    @Test
    void readWriteRoundTrip() {
        PhysicalMemory mem = new PhysicalMemory(PhysicalMemory.PAGE_SIZE);
        mem.writeByte(0L, (byte) 0x42);
        assertEquals((byte) 0x42, mem.readByte(0L));

        byte[] payload = "kernel".getBytes();
        mem.writeBytes(100, payload, 0, payload.length);
        byte[] out = new byte[payload.length];
        mem.readBytes(100, out, 0, out.length);
        assertArrayEquals(payload, out);
    }

    @Test
    void rejectsOutOfRange() {
        PhysicalMemory mem = new PhysicalMemory(PhysicalMemory.PAGE_SIZE);
        assertThrows(IndexOutOfBoundsException.class, () -> mem.readByte(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> mem.readByte(PhysicalMemory.PAGE_SIZE));
    }
}
