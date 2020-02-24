package io.spokestack.spokestack.nlu;

import io.spokestack.spokestack.util.EventTracer;

/**
 * A simple interface implemented by classes interested in receiving trace
 * events.
 */
public interface TraceListener {

    /**
     * A notification that a trace event has occurred.
     * @param level The trace event's severity level.
     * @param message the trace event's message.
     */
    void onTrace(EventTracer.Level level, String message);
}
