package io.spokestack.spokestack.nlu;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.util.EventTracer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Context for NLU operations, including request metadata and a facility for
 * dispatch of trace events.
 */
final class NLUContext {

    private final EventTracer tracer;
    private final List<TraceListener> listeners;
    private Map<String, Object> requestMetadata;

    /**
     * Create a new dispatcher.
     *
     * @param config the dispatcher's configuration
     */
    NLUContext(SpeechConfig config) {
        int traceLevel = config.getInteger(
              "trace-level",
              EventTracer.Level.NONE.value());

        this.tracer = new EventTracer(traceLevel);
        this.listeners = new ArrayList<>();
    }

    /**
     * Add a listener interested in receiving NLU trace events.
     *
     * @param listener the listener to add
     */
    public void addTraceListener(TraceListener listener) {
        this.listeners.add(listener);
    }

    /**
     * @return the metadata for the current request.
     */
    public Map<String, Object> getRequestMetadata() {
        return requestMetadata;
    }

    /**
     * Set the metadata for the current request.
     * @param metadata the metadata for the current request.
     */
    public void setRequestMetadata(Map<String, Object> metadata) {
        this.requestMetadata = metadata;
    }

    /**
     * Traces a debug level message.
     *
     * @param format trace message format string
     * @param params trace message format parameters
     */
    public void traceDebug(String format, Object... params) {
        trace(EventTracer.Level.DEBUG, format, params);
    }

    /**
     * Traces a performance level message.
     *
     * @param format trace message format string
     * @param params trace message format parameters
     */
    public void tracePerf(String format, Object... params) {
        trace(EventTracer.Level.PERF, format, params);
    }

    /**
     * Traces an informational level message.
     *
     * @param format trace message format string
     * @param params trace message format parameters
     */
    public void traceInfo(String format, Object... params) {
        trace(EventTracer.Level.INFO, format, params);
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
        if (this.tracer.canTrace(level)) {
            String message = String.format(format, params);
            dispatchTrace(message);
        }
    }

    /**
     * Dispatches an NLU trace message.
     *
     * @param message the trace message to publish
     */
    public void dispatchTrace(String message) {
        for (TraceListener listener : this.listeners) {
            try {
                listener.onTrace(message);
            } catch (Exception e) {
                // failed traces fail in silence
            }
        }
    }
}
