package io.spokestack.spokestack.nlu;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.util.EventTracer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Context for NLU operations, including request metadata and a facility for
 * dispatch of trace events.
 */
public final class NLUContext {

    private final EventTracer tracer;
    private final List<TraceListener> listeners;
    private HashMap<String, Object> requestMetadata;

    /**
     * Create a new dispatcher.
     *
     * @param config the dispatcher's configuration
     */
    public NLUContext(SpeechConfig config) {
        int traceLevel = config.getInteger(
              "trace-level",
              EventTracer.Level.ERROR.value());

        this.tracer = new EventTracer(traceLevel);
        this.listeners = new ArrayList<>();
        this.requestMetadata = new HashMap<>();
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
    public HashMap<String, Object> getRequestMetadata() {
        return requestMetadata;
    }

    /**
     * Set the metadata for the current request.
     * @param metadata the metadata for the current request.
     */
    public void setRequestMetadata(HashMap<String, Object> metadata) {
        this.requestMetadata = metadata;
    }

    /**
     * Resets state held by the context object, including request metadata.
     */
    public void reset() {
        this.requestMetadata.clear();
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
     * Traces a warning level message.
     *
     * @param format trace message format string
     * @param params trace message format parameters
     */
    public void traceWarn(String format, Object... params) {
        trace(EventTracer.Level.WARN, format, params);
    }

    /**
     * Traces an error level message.
     *
     * @param format trace message format string
     * @param params trace message format parameters
     */
    public void traceError(String format, Object... params) {
        trace(EventTracer.Level.ERROR, format, params);
    }

    /**
     * indicates whether a message will be traced at a level.
     * @param level tracing level
     * @return true if tracing will occur, false otherwise
     */
    public boolean canTrace(EventTracer.Level level) {
        return this.tracer.canTrace(level);
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
            dispatchTrace(level, message);
        }
    }

    /**
     * Dispatches an NLU trace message.
     *
     * @param level the severity level of the trace
     * @param message the trace message to publish
     */
    public void dispatchTrace(EventTracer.Level level, String message) {
        for (TraceListener listener : this.listeners) {
            try {
                listener.onTrace(level, message);
            } catch (Exception e) {
                // failed traces fail in silence
            }
        }
    }
}
