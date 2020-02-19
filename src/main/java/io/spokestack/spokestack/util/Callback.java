package io.spokestack.spokestack.util;

/**
 * A simple interface representing a callback function called with a single
 * argument. Since callbacks are often used to propagate results of
 * asynchronous operations, the callback may also receive an error generated
 * during the task meant to produce its result.
 *
 * @param <T> The type of result with which the callback should be executed.
 */
public interface Callback<T> {

    /**
     * Call the callback with the specified argument.
     * @param arg The callback's argument.
     */
     void call(T arg);

    /**
     * Call the callback with an error generated during an asynchronous task.
     * @param err An error generated instead of the callback's intended result.
     */
    void onError(Throwable err);
}
