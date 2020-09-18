package io.spokestack.spokestack;

import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.nlu.TraceListener;
import io.spokestack.spokestack.tts.TTSEvent;
import io.spokestack.spokestack.tts.TTSListener;
import io.spokestack.spokestack.util.Callback;
import io.spokestack.spokestack.util.EventTracer;
import org.jetbrains.annotations.NotNull;

/**
 * An abstract adapter class for receiving events from Spokestack subsystems.
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
     * @param event   The name of the event that was raised.
     * @param context The current speech context.
     */
    @Override
    public void onEvent(@NotNull SpeechContext.Event event,
                        @NotNull SpeechContext context) {
    }

    /**
     * Receive trace messages from the NLU subsystem.
     *
     * @param level   The trace event's severity level.
     * @param message the trace event's message.
     */
    @Override
    public void onTrace(@NotNull EventTracer.Level level,
                        @NotNull String message) {
    }

    /**
     * Receive events from the TTS subsystem.
     *
     * @param event The event from the TTS subsystem.
     */
    @Override
    public void eventReceived(@NotNull TTSEvent event) {

    }

    /**
     * Called when an NLU classification result is available if this class is
     * registered as a callback at classification time.
     *
     * @param result The NLU result.
     * @see io.spokestack.spokestack.nlu.tensorflow.TensorflowNLU#classify(String)
     * @see io.spokestack.spokestack.util.AsyncResult
     */
    @Override
    public void call(@NotNull NLUResult result) {

    }

    /**
     * Receive notifications of errors that occur during NLU classification if
     * this class is registered as a callback at classification time.
     *
     * @param err An error generated during expected NLU classification.
     * @see io.spokestack.spokestack.nlu.tensorflow.TensorflowNLU#classify(String)
     * @see io.spokestack.spokestack.util.AsyncResult
     */
    @Override
    public void onError(@NotNull Throwable err) {

    }
}
