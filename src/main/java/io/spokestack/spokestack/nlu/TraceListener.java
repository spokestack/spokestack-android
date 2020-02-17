package io.spokestack.spokestack.nlu;

/**
 * A simple interface implemented by classes interested in receiving trace
 * events.
 */
public interface TraceListener {

    /**
     * A notification that a trace event has occurred.
     * @param message the trace event's message.
     */
    void onTrace(String message);
}
