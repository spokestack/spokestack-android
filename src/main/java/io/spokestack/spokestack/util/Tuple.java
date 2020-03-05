package io.spokestack.spokestack.util;

/**
 * A simple wrapper for an immutable tuple of objects.
 * @param <T> The type of the tuple's first member.
 * @param <U> The type of the tuple's second member.
 */
public final class Tuple<T, U> {

    private final T first;
    private final U second;

    /**
     * Create a new tuple.
     * @param val1 The first value.
     * @param val2 The second value.
     */
    public Tuple(T val1, U val2) {
        this.first = val1;
        this.second = val2;
    }

    /**
     * @return The tuple's first object.
     */
    public T first() {
        return first;
    }

    /**
     * @return The tuple's second object.
     */
    public U second() {
        return second;
    }
}
