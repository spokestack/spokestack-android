package io.spokestack.spokestack.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class EventTracerTest {

    @Test
    public void canTrace() {
        EventTracer tracer = new EventTracer(EventTracer.Level.NONE.value());
        assertFalse(tracer.canTrace(EventTracer.Level.DEBUG));
        assertFalse(tracer.canTrace(EventTracer.Level.PERF));
        assertFalse(tracer.canTrace(EventTracer.Level.INFO));

        tracer = new EventTracer(EventTracer.Level.INFO.value());
        assertFalse(tracer.canTrace(EventTracer.Level.DEBUG));
        assertFalse(tracer.canTrace(EventTracer.Level.PERF));
        assertTrue(tracer.canTrace(EventTracer.Level.INFO));

        tracer = new EventTracer(EventTracer.Level.PERF.value());
        assertFalse(tracer.canTrace(EventTracer.Level.DEBUG));
        assertTrue(tracer.canTrace(EventTracer.Level.PERF));
        assertTrue(tracer.canTrace(EventTracer.Level.INFO));

        tracer = new EventTracer(EventTracer.Level.DEBUG.value());
        assertTrue(tracer.canTrace(EventTracer.Level.DEBUG));
        assertTrue(tracer.canTrace(EventTracer.Level.PERF));
        assertTrue(tracer.canTrace(EventTracer.Level.INFO));
    }
}