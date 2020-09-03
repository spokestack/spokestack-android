package io.spokestack.spokestack.util;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static io.spokestack.spokestack.util.EventTracer.Level.*;
import static org.junit.Assert.*;

public class EventTracerTest {

    @Test
    public void canTrace() {
        List<TraceListener> listeners = new ArrayList<>();
        EventTracer.Level level = DEBUG;
        TestListener listener = new TestListener(level);
        listeners.add(listener);

        EventTracer tracer = new EventTracer(level.value(), listeners);
        for (EventTracer.Level logLevel : EventTracer.Level.values()) {
            tracer.canTrace(logLevel);
            tracer.trace(logLevel, "test");
        }

        level = PERF;
        listener.setLevel(level);
        tracer = new EventTracer(level.value(), listeners);
        assertFalse(tracer.canTrace(DEBUG));
        assertTrue(tracer.canTrace(PERF));
        assertTrue(tracer.canTrace(INFO));
        assertTrue(tracer.canTrace(WARN));
        assertTrue(tracer.canTrace(ERROR));
        assertTrue(tracer.canTrace(NONE));
        for (EventTracer.Level logLevel : EventTracer.Level.values()) {
            tracer.trace(logLevel, "test");
        }

        level = INFO;
        listener.setLevel(level);
        tracer = new EventTracer(level.value(), listeners);
        assertFalse(tracer.canTrace(DEBUG));
        assertFalse(tracer.canTrace(PERF));
        assertTrue(tracer.canTrace(INFO));
        assertTrue(tracer.canTrace(WARN));
        assertTrue(tracer.canTrace(ERROR));
        assertTrue(tracer.canTrace(NONE));
        for (EventTracer.Level logLevel : EventTracer.Level.values()) {
            tracer.trace(logLevel, "test");
        }

        level = WARN;
        listener.setLevel(level);
        tracer = new EventTracer(level.value(), listeners);
        assertFalse(tracer.canTrace(DEBUG));
        assertFalse(tracer.canTrace(PERF));
        assertFalse(tracer.canTrace(INFO));
        assertTrue(tracer.canTrace(WARN));
        assertTrue(tracer.canTrace(ERROR));
        assertTrue(tracer.canTrace(NONE));
        for (EventTracer.Level logLevel : EventTracer.Level.values()) {
            tracer.trace(logLevel, "test");
        }

        level = ERROR;
        listener.setLevel(level);
        tracer = new EventTracer(level.value(), listeners);
        assertFalse(tracer.canTrace(DEBUG));
        assertFalse(tracer.canTrace(PERF));
        assertFalse(tracer.canTrace(INFO));
        assertFalse(tracer.canTrace(WARN));
        assertTrue(tracer.canTrace(ERROR));
        assertTrue(tracer.canTrace(NONE));
        for (EventTracer.Level logLevel : EventTracer.Level.values()) {
            tracer.trace(logLevel, "test");
        }

        level = NONE;
        listener.setLevel(level);
        tracer = new EventTracer(level.value(), listeners);
        assertFalse(tracer.canTrace(DEBUG));
        assertFalse(tracer.canTrace(PERF));
        assertFalse(tracer.canTrace(INFO));
        assertFalse(tracer.canTrace(WARN));
        assertFalse(tracer.canTrace(ERROR));
        assertTrue(tracer.canTrace(NONE));
        for (EventTracer.Level logLevel : EventTracer.Level.values()) {
            tracer.trace(logLevel, "test");
        }
    }


    private static class TestListener implements TraceListener {
        private EventTracer.Level level;

        TestListener(EventTracer.Level level) {
            this.level = level;
        }

        public void setLevel(EventTracer.Level level) {
            this.level = level;
        }

        @Override
        public void onTrace(@NotNull EventTracer.Level level,
                            @NotNull String message) {
            if (level.value() < this.level.value()) {
                String error = "traced at level %s but configured for %s";
                fail(String.format(error, level, this.level));
            }
        }
    }
}