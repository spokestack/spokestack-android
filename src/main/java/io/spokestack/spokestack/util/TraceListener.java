package io.spokestack.spokestack.util;

import androidx.annotation.NonNull;

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
    void onTrace(@NonNull EventTracer.Level level, @NonNull String message);
}
