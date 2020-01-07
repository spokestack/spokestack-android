package io.spokestack.spokestack;

import android.content.Context;
import androidx.lifecycle.LifecycleObserver;
import io.spokestack.spokestack.tts.AudioResponse;
import io.spokestack.spokestack.tts.TTSComponent;
import io.spokestack.spokestack.tts.TTSEvent;
import io.spokestack.spokestack.tts.TTSListener;

/**
 * Text-to-speech output.
 *
 * <p>
 * This is the audio output interface for the Spokestack framework. Implementers
 * receive an audio URL provided by a TTS service and must interact with a media
 * player to play that audio, pausing the speech pipeline while the resulting
 * audio plays if desired and then resuming it when playback is over.
 * </p>
 *
 * <p>
 * {@code SpeechOutput} implementers are {@link TTSListener}s by definition;
 * thus, if a TTS subsystem has an output class, additional listeners are not
 * required. {@code SpeechOutput} components do publish their own events using
 * the same interface, however, so an app may wish to include a listener
 * component to receive media player events or errors.
 * </p>
 *
 * <p>
 * To be used in a TTS subsystem, an implementing class must provide a
 * constructor that accepts a {@link SpeechConfig} instance.
 * </p>
 *
 * @see TTSComponent
 */
public abstract class SpeechOutput extends TTSComponent
      implements AutoCloseable, LifecycleObserver, TTSListener {

    @Override
    public void eventReceived(TTSEvent event) {
        if (event.type == TTSEvent.Type.AUDIO_AVAILABLE) {
            audioReceived(event.getTtsResponse());
        }
    }

    /**
     * Notifies the component that audio is available for immediate playback or
     * caching at the specified location.
     *
     * @param response The TTS response containing the URI of the synthesized
     *                 audio.
     */
    public abstract void audioReceived(AudioResponse response);

    /**
     * Sets the output's application context.
     *
     * @param appContext The application context.
     */
    public abstract void setAppContext(Context appContext);
}
