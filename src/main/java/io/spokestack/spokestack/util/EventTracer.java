package io.spokestack.spokestack.util;

/**
 * A utility class for components capable of dispatching trace/logging events,
 * encapsulating the logic of checking an event's specified level against a
 * preconfigured threshold to determine whether the event message should be
 * formatted and dispatched, similar to the Java logger.
 */
public final class EventTracer {

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

    private int traceLevel;

    /**
     * Creates a new tracer at the specified level.
     * @param level The threshold that events must surpass to be delivered.
     */
    public EventTracer(int level) {
        this.traceLevel = level;
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
