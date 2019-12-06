package io.spokestack.spokestack.wakeword;

/**
 * a simple circular buffer of floating point values.
 */
final class RingBuffer {
    private final float[] data;     // data buffer (n + 1) elements
    private int rpos;               // current read position
    private int wpos;               // current write position

    /**
     * constructs a new ring buffer instance.
     * @param capacity the maximum number of elements to store
     */
    RingBuffer(int capacity) {
        this.data = new float[capacity + 1];
        this.wpos = 0;
        this.rpos = 0;
    }

    /**
     * @return the maximum number of elements that can be stored
     */
    public int capacity() {
        return this.data.length - 1;
    }

    /**
     * @return true if no elements can be read, false otherwise
     */
    public boolean isEmpty() {
        return this.rpos == this.wpos;
    }

    /**
     * @return true if no elements can be written, false otherwise
     */
    public boolean isFull() {
        return pos(this.wpos + 1) == this.rpos;
    }

    /**
     * seeks the read head to the beginning, marking it full and
     * allowing all elements to be read.
     * @return this
     */
    public RingBuffer rewind() {
        this.rpos = pos(this.wpos + 1);
        return this;
    }

    /**
     * seeks the read head forward.
     * care must be taken by the caller to avoid read overflow
     * @param elems the number of elements to move forward/backward
     * @return this
     */
    public RingBuffer seek(int elems) {
        this.rpos = pos(this.rpos + elems);
        return this;
    }

    /**
     * resets the read head buffer, marking the buffer empty, but not
     * modifying any elements.
     * @return this
     */
    public RingBuffer reset() {
        this.rpos = this.wpos;
        return this;
    }

    /**
     * fills the remaining positions in the buffer with the specified value.
     * @param value the value to write
     * @return this
     */
    public RingBuffer fill(float value) {
        while (!isFull())
            write(value);
        return this;
    }

    /**
     * reads the next value from the buffer.
     * @return the value that was read
     */
    public float read() {
        if (isEmpty())
            throw new IllegalStateException("empty");

        float value = this.data[this.rpos];
        this.rpos = pos(this.rpos + 1);
        return value;
    }

    /**
     * writes the next value to the buffer.
     * @param value the value to write
     */
    public void write(float value) {
        if (isFull())
            throw new IllegalStateException("full");

        this.data[this.wpos] = value;
        this.wpos = pos(this.wpos + 1);
    }

    private int pos(int x) {
        int mod = x % this.data.length;
        if (x < 0) {
            return -mod;
        }
        return mod;
    }
}
