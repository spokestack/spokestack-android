package io.spokestack.spokestack.util;

import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for components capable of dispatching trace/logging events,
 * encapsulating the logic of checking an event's specified level against a
 * preconfigured threshold to determine whether the event message should be
 * formatted and dispatched, similar to the Java logger.
 */
public class EventTracer {

    /** trace levels, for simple filtering. */
    public enum Level {
        /** all the traces. */
        DEBUG(10),
        /** performance traces. */
        PERF(20),
        /** informational traces. */
        INFO(30),
        /** warning traces. */
        WARN(50),
        /** error traces. */
        ERROR(80),
        /** no traces. */
        NONE(100);

        private final int level;

        Level(int l) {
            this.level = l;
        }

        /** @return the trace level value */
        public int value() {
            return this.level;
        }
    }

    private final int traceLevel;
    private final List<? extends TraceListener> listeners;

    /**
     * Creates a new tracer with no listeners to be used only for trace level
     * checks.
     * @param level The threshold that events must surpass to be delivered.
     */
    public EventTracer(int level) {
        this(level, new ArrayList<>());
    }

    /**
     * Creates a new tracer at the specified level.
     * @param level The threshold that events must surpass to be delivered.
     * @param traceListeners Listeners that should receive valid trace events.
     */
    public EventTracer(int level,
                       List<? extends TraceListener> traceListeners) {
        this.traceLevel = level;
        this.listeners = traceListeners;
    }

    /**
     * Raises a trace event.
     *
     * @param level  tracing level
     * @param format trace message format string
     * @param params trace message format parameters
     */
    public void trace(
          EventTracer.Level level,
          String format,
          Object... params) {
        if (!canTrace(level)) {
            return;
        }
        String message = String.format(format, params);
        for (TraceListener listener : this.listeners) {
            try {
                listener.onTrace(level, message);
            } catch (Exception e) {
                // failed traces fail in silence
            }
        }
    }

    /**
     * indicates whether a message will be traced at a level.
     * @param level tracing level
     * @return true if tracing will occur, false otherwise
     */
    public boolean canTrace(Level level) {
        return level.value() >= this.traceLevel;
    }
}
