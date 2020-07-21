package io.spokestack.spokestack.nlu;

import androidx.annotation.NonNull;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.util.EventTracer;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class NLUContextTest implements TraceListener {
    private String message;

    @Test
    public void testTrace() {
        SpeechConfig config = new SpeechConfig();
        NLUContext context;

        // default tracing
        context = new NLUContext(config);
        context.addTraceListener(this);
        context.traceDebug("trace");
        assertNull(this.message);

        // skipped tracing
        config.put("trace-level", EventTracer.Level.INFO.value());
        context = new NLUContext(config);
        context.addTraceListener(this);
        context.traceDebug("trace");
        assertNull(this.message);

        // informational tracing
        config.put("trace-level", EventTracer.Level.INFO.value());
        context = new NLUContext(config);
        context.addTraceListener(this);
        context.traceInfo("test %d", 42);
        assertEquals("test 42", this.message);

        // performance tracing
        config.put("trace-level", EventTracer.Level.PERF.value());
        context = new NLUContext(config);
        context.addTraceListener(this);
        context.tracePerf("test %d", 42);
        assertEquals("test 42", this.message);

        // debug tracing
        config.put("trace-level", EventTracer.Level.DEBUG.value());
        context = new NLUContext(config);
        context.addTraceListener(this);
        context.traceDebug("test %d", 42);
        assertEquals("test 42", this.message);
    }

    @Override
    public void onTrace(@NonNull EventTracer.Level level, @NonNull String message) {
        this.message = message;
    }
}
