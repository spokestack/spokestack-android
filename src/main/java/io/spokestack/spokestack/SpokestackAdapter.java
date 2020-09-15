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

    @Override
    public void onEvent(@NotNull SpeechContext.Event event,
                        @NotNull SpeechContext context) throws Exception {
    }

    @Override
    public void onTrace(@NotNull EventTracer.Level level,
                        @NotNull String message) {
    }

    @Override
    public void eventReceived(@NotNull TTSEvent event) {

    }

    @Override
    public void call(@NotNull NLUResult arg) {

    }

    @Override
    public void onError(@NotNull Throwable err) {

    }
}
