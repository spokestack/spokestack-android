package io.spokestack.spokestack;

import java.util.Deque;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

/**
 * SpokeStack speech recognition context
 *
 * <p>
 * This class maintains global state for the speech pipeline, allowing
 * pipeline components to communicate information among themselves and
 * event handlers.
 * </p>
 */
public final class SpeechContext {
    /** speech event types. */
    public enum Event {
        /** speech recognition has become active. */
        ACTIVATE("activate"),
        /** speech recognition has become inactive. */
        DEACTIVATE("deactivate"),
        /** speech was recognized. */
        RECOGNIZE("recognize"),
        /** the activation timeout expired. */
        TIMEOUT("timeout"),
        /** a speech error occurred. */
        ERROR("error"),
        /** a trace event occurred. */
        TRACE("trace");

        private final String event;

        Event(String e) {
            this.event = e;
        }

        /** @return the event type string */
        @Override
        public String toString() {
            return this.event;
        }
    }

    /** trace levels, for simple filtering. */
    public enum TraceLevel {
        /** all the traces. */
        DEBUG(10),
        /** performance traces. */
        PERF(20),
        /** informational traces. */
        INFO(30),
        /** no traces. */
        NONE(100);

        private final int level;

        TraceLevel(int l) {
            this.level = l;
        }

        /** @return the trace level value */
        public int value() {
            return this.level;
        }
    }

    private final List<OnSpeechEventListener> listeners = new ArrayList<>();
    private final int traceLevel;
    private Deque<ByteBuffer> buffer;
    private boolean speech;
    private boolean active;
    private String transcript = "";
    private double confidence;
    private Throwable error;
    private String message;

    /**
     * initializes a new configuration instance.
     * @param config speech configuration
    */
    public SpeechContext(SpeechConfig config) {
        this.traceLevel = config.getInteger(
            "trace-level",
            TraceLevel.NONE.value());
    }

    /** @return speech frame buffer */
    public Deque<ByteBuffer> getBuffer() {
        return this.buffer;
    }

    /**
     * attaches a frame buffer to the context.
     * @param value frame buffer to attach
     * @return this
     */
    public SpeechContext attachBuffer(Deque<ByteBuffer> value) {
        this.buffer = value;
        return this;
    }

    /**
     * removes the attached frame buffer.
     * @return this
     */
    public SpeechContext detachBuffer() {
        this.buffer = null;
        return this;
    }

    /** @return speech detected indicator */
    public boolean isSpeech() {
        return this.speech;
    }

    /**
     * sets speech detected indicator.
     * @param value value to assign
     * @return this
     */
    public SpeechContext setSpeech(boolean value) {
        this.speech = value;
        return this;
    }

    /** @return speech recognition active indicator */
    public boolean isActive() {
        return this.active;
    }

    /**
     * activates speech recognition.
     * @param value value to assign
     * @return this
     */
    public SpeechContext setActive(boolean value) {
        this.active = value;
        return this;
    }

    /** @return the current speech transcript. */
    public String getTranscript() {
        return this.transcript;
    }

    /**
     * updates the current speech transcript.
     * @param value speech text value to assign
     * @return this
     */
    public SpeechContext setTranscript(String value) {
        this.transcript = value;
        return this;
    }

    /** @return the current speech recognition confidence: [0-1) */
    public double getConfidence() {
        return this.confidence;
    }

    /**
     * updates the current speech confidence level.
     * @param value speech confidence to assign
     * @return this
     */
    public SpeechContext setConfidence(double value) {
        this.confidence = value;
        return this;
    }

    /** @return the last error raised on the context */
    public Throwable getError() {
        return this.error;
    }

    /**
     * raises an error with the speech context.
     * @param value the exception to attach
     * @return this
     */
    public SpeechContext setError(Throwable value) {
        this.error = value;
        return this;
    }

    /**
     * @return the current trace message
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * resets the context to the default state.
     * @return this
     */
    public SpeechContext reset() {
        setSpeech(false);
        setActive(false);
        setTranscript("");
        setConfidence(0);
        setError(null);
        this.message = null;
        return this;
    }

    /**
     * traces a debug level message.
     * @param format trace message format string
     * @param params trace message format parameters
     * @return this
     */
    public SpeechContext traceDebug(String format, Object... params) {
        return trace(TraceLevel.DEBUG, format, params);
    }

    /**
     * traces a performance level message.
     * @param format trace message format string
     * @param params trace message format parameters
     * @return this
     */
    public SpeechContext tracePerf(String format, Object... params) {
        return trace(TraceLevel.PERF, format, params);
    }

    /**
     * traces an informational level message.
     * @param format trace message format string
     * @param params trace message format parameters
     * @return this
     */
    public SpeechContext traceInfo(String format, Object... params) {
        return trace(TraceLevel.INFO, format, params);
    }

    /**
     * raises a trace event.
     * @param level tracing level
     * @param format trace message format string
     * @param params trace message format parameters
     * @return this
     */
    public SpeechContext trace(
            TraceLevel level,
            String format,
            Object... params) {
        if (canTrace(level)) {
            this.message = String.format(format, params);
            dispatch(Event.TRACE);
        }
        return this;
    }

    /**
     * indicates whether a message will be traced at a level.
     * @param level tracing level
     * @return true if tracing will occur, false otherwise
     */
    public boolean canTrace(TraceLevel level) {
        return level.value() >= this.traceLevel;
    }

    /**
     * dispatches a speech event.
     * @param event the event to publish
     * @return this
     */
    public SpeechContext dispatch(Event event) {
        for (OnSpeechEventListener listener: this.listeners) {
            try {
                listener.onEvent(event, this);
            } catch (Exception e) {
                if (event != Event.TRACE)
                    traceInfo("dispatch-failed: %s", e.toString());
            }
        }
        return this;
    }

    /**
     * attaches a speech listener.
     * @param listener listener callback to attach
     * @return this
     */
    public SpeechContext addOnSpeechEventListener(
            OnSpeechEventListener listener) {
        this.listeners.add(listener);
        return this;
    }

    /**
     * detaches a speech listener.
     * @param listener listener callback to remove
     * @return this
     */
    public SpeechContext removeOnSpeechEventListener(
            OnSpeechEventListener listener) {
        this.listeners.remove(listener);
        return this;
    }
}
