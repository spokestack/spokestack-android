package io.spokestack.spokestack;

import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.tts.TTSEvent;
import io.spokestack.spokestack.tts.TTSListener;
import io.spokestack.spokestack.util.Callback;
import io.spokestack.spokestack.util.EventTracer;
import io.spokestack.spokestack.util.TraceListener;
import org.jetbrains.annotations.NotNull;

/**
 * An abstract adapter class for receiving events from Spokestack modules.
 *
 * <p>
 * This class provides empty implementations of all Spokestack listener methods.
 * It can be extended and individual methods overridden to receive only those
 * events.
 * </p>
 */
public abstract class SpokestackAdapter implements
      Callback<NLUResult>,
      OnSpeechEventListener,
      TraceListener,
      TTSListener {

    /**
     * Receive events from the speech pipeline.
     *
     * <p>
     * Clients should typically override {@link #speechEvent(SpeechContext.Event,
     * SpeechContext)} instead of this method.
     * </p>
     *
     * @param event   The name of the event that was raised.
     * @param context The current speech context.
     */
    @Override
    public void onEvent(@NotNull SpeechContext.Event event,
                        @NotNull SpeechContext context) {
        if (event == SpeechContext.Event.TRACE) {
            trace(SpokestackModule.SPEECH_PIPELINE, context.getMessage());
        } else if (event == SpeechContext.Event.ERROR) {
            error(SpokestackModule.SPEECH_PIPELINE, context.getError());
        }
        speechEvent(event, context);
    }

    /**
     * Receive events from the speech pipeline.
     *
     * @param event   The name of the event that was raised.
     * @param context The current speech context.
     */
    public void speechEvent(@NotNull SpeechContext.Event event,
                            @NotNull SpeechContext context) {
    }

    /**
     * Called when an NLU classification result is available if this class is
     * registered as a callback at classification time. Adapters added to a
     * {@link Spokestack} class at build time are automatically registered for
     * all classifications.
     *
     * <p>
     * Clients should typically override {@link #nluResult(NLUResult)} instead
     * of this method.
     * </p>
     *
     * @param result The NLU result.
     * @see io.spokestack.spokestack.nlu.tensorflow.TensorflowNLU#classify(String)
     * @see io.spokestack.spokestack.util.AsyncResult
     */
    @Override
    public void call(@NotNull NLUResult result) {
        nluResult(result);
    }

    /**
     * Called when an NLU classification result is available if this class is
     * registered as a callback at classification time. Adapters added to a
     * {@link Spokestack} class at build time are automatically registered for
     * all classifications.
     *
     * @param result The NLU result.
     * @see io.spokestack.spokestack.nlu.tensorflow.TensorflowNLU#classify(String)
     * @see io.spokestack.spokestack.util.AsyncResult
     */
    public void nluResult(@NotNull NLUResult result) {
    }

    /**
     * Receive notifications of errors that occur during NLU classification if
     * this class is registered as a callback at classification time.Adapters
     * added to a {@link Spokestack} class at build time are automatically
     * registered for all classifications.
     *
     * <p>
     * Clients should typically override {@link #error(SpokestackModule,
     * Throwable)} instead of this method.
     * </p>
     *
     * @param err An error generated during expected NLU classification.
     * @see io.spokestack.spokestack.nlu.tensorflow.TensorflowNLU#classify(String)
     * @see io.spokestack.spokestack.util.AsyncResult
     */
    @Override
    public void onError(@NotNull Throwable err) {
        error(SpokestackModule.NLU, err);
    }

    /**
     * Receive trace messages from the NLU module.
     *
     * <p>
     * Clients should typically override {@link #trace(SpokestackModule,
     * String)} instead of this method.
     * </p>
     *
     * @param level   The trace event's severity level.
     * @param message the trace event's message.
     */
    @Override
    public void onTrace(@NotNull EventTracer.Level level,
                        @NotNull String message) {
        trace(SpokestackModule.NLU, message);
    }

    /**
     * Receive events from the TTS module.
     *
     * <p>
     * Clients should typically override {@link #ttsEvent(TTSEvent)} instead of
     * this method.
     * </p>
     *
     * @param event The event from the TTS module.
     */
    @Override
    public void eventReceived(@NotNull TTSEvent event) {
        if (event.type == TTSEvent.Type.ERROR) {
            error(SpokestackModule.TTS, event.getError());
        }
        ttsEvent(event);
    }

    /**
     * Receive events from the TTS module.
     *
     * @param event The event from the TTS module.
     */
    public void ttsEvent(@NotNull TTSEvent event) {
    }

    /**
     * Receive trace messages from any module.
     *
     * @param module  The module where the message originated.
     * @param message the trace event's message.
     */
    public void trace(@NotNull SpokestackModule module,
                      @NotNull String message) {
    }

    /**
     * Receive notifications of errors from any module.
     *
     * @param module The module where the error originated.
     * @param err    An error generated during Spokestack operation.
     */
    public void error(@NotNull SpokestackModule module,
                      @NotNull Throwable err) {
    }
}
