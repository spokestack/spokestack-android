package io.spokestack.spokestack.wakeword;

import java.util.*;

import org.junit.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

public class RingBufferTest {
    @Test
    public void testConstruction() {
        RingBuffer buffer;

        // empty buffer
        buffer = new RingBuffer(0);
        assertEquals(buffer.capacity(), 0);
        assertTrue(buffer.isEmpty());
        assertTrue(buffer.isFull());

        // unit uffer
        buffer = new RingBuffer(1);
        assertEquals(buffer.capacity(), 1);
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());

        // valid buffer
        buffer = new RingBuffer(10);
        assertEquals(buffer.capacity(), 10);
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());
    }

    @Test
    public void testReadWrite() {
        final RingBuffer buffer = new RingBuffer(3);

        // invalid read
        assertThrows(IllegalStateException.class, new Executable() {
            public void execute() throws Exception { buffer.read(); }
        });

        // single read/write
        buffer.write(1);
        assertFalse(buffer.isEmpty());
        assertFalse(buffer.isFull());
        assertEquals(buffer.read(), 1);
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());

        // full buffer write
        for (int i = 0; i < buffer.capacity(); i++)
            buffer.write(i + 1);
        assertFalse(buffer.isEmpty());
        assertTrue(buffer.isFull());

        assertThrows(IllegalStateException.class, new Executable() {
            public void execute() throws Exception { buffer.write(0); }
        });

        for (int i = 0; i < buffer.capacity(); i++)
            assertEquals(buffer.read(), i + 1);
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());
    }

    @Test
    public void testRewind() {
        final RingBuffer buffer = new RingBuffer(4);

        // default rewind
        buffer.rewind();
        assertFalse(buffer.isEmpty());
        assertTrue(buffer.isFull());
        while (!buffer.isEmpty())
            buffer.read();

        // valid rewind
        for (int i = 0; i < buffer.capacity(); i++)
            buffer.write(i + 1);
        while (!buffer.isEmpty())
            buffer.read();

        buffer.rewind();
        assertFalse(buffer.isEmpty());
        assertTrue(buffer.isFull());

        for (int i = 0; i < buffer.capacity(); i++)
            assertEquals(buffer.read(), i + 1);
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());
    }

    @Test
    public void testSeek() {
        final RingBuffer buffer = new RingBuffer(5);

        // valid seek
        for (int i = 0; i < buffer.capacity(); i++)
            buffer.write(i + 1);

        buffer.seek(1);
        for (int i = 1; i < buffer.capacity(); i++)
            assertEquals(buffer.read(), i + 1);
        buffer.rewind();

        buffer.seek(buffer.capacity() - 1);
        for (int i = buffer.capacity() - 1; i < buffer.capacity(); i++)
            assertEquals(buffer.read(), i + 1);

        // negative seek wraps around --
        // the read head here starts at position 5 and will end up at 4
        buffer.seek(-7);
        assertEquals(buffer.read(), buffer.capacity());
    }

    @Test
    public void testReset() {
        final RingBuffer buffer = new RingBuffer(3);

        // reset empty
        buffer.reset();
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());

        // reset valid
        for (int i = 0; i < buffer.capacity(); i++)
            buffer.write(i + 1);

        buffer.reset();
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());
    }

    @Test
    public void testFill() {
        final RingBuffer buffer = new RingBuffer(4);

        // complete fill
        buffer.fill(1);
        assertFalse(buffer.isEmpty());
        assertTrue(buffer.isFull());

        for (int i = 0; i < buffer.capacity(); i++)
            assertEquals(buffer.read(), 1);

        // partial fill
        buffer.rewind();
        buffer.seek(1);
        buffer.fill(2);
        assertFalse(buffer.isEmpty());
        assertTrue(buffer.isFull());

        for (int i = 0; i < buffer.capacity() - 1; i++)
            assertEquals(buffer.read(), 1);

        assertEquals(buffer.read(), 2);
    }
}
