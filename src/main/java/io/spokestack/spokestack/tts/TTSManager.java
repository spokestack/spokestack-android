package io.spokestack.spokestack.tts;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager for text-to-speech output in Spokestack.
 *
 * <p>
 * Spokestack's TTS manager follows the same setup pattern as its speech
 * pipeline, and the two APIs are similar, but the components operate
 * independently.
 * </p>
 *
 * <p>
 * The TTS manager establishes the external service that will be used to
 * synthesize system prompts and, optionally, a component that handles playback
 * of the resulting audio streams without the app having to manage Android media
 * player resources. The following configuration enables both features, using
 * Spokestack's TTS service to process synthesis requests and its output
 * component to automatically play them:
 * </p>
 *
 * <pre>
 * {@code
 * TTSManager ttsManager = new TTSManager.Builder()
 *     .setTTSServiceClass("io.spokestack.spokestack.tts.SpokestackTTSService")
 *     .setOutputClass("io.spokestack.spokestack.tts.SpokestackTTSOutput")
 *     .setProperty("spokestack-key", "f0bc990c-e9db-4a0c-a2b1-6a6395a3d97e")
 *     .setProperty(
 *         "spokestack-secret",
 *         "5BD5483F573D691A15CFA493C1782F451D4BD666E39A9E7B2EBE287E6A72C6B6")
 *     .setAndroidContext(getApplicationContext())
 *     .setLifecycle(getLifecycle())
 *     .build();
 * }
 * </pre>
 *
 * <p>
 * A TTS manager does not need to be explicitly started to do its job, but when
 * it is no longer needed (or when certain Android lifecycle events occur), it's
 * a good idea to call {@link #release()} to free any resources it's holding. If
 * an output class is specified, this is critical, as it may be holding a
 * prepared Android media player.
 * </p>
 *
 * <p>
 * Once released, an explicit call to {@link #prepare()} is required to
 * reallocate a manager's resources.
 * </p>
 */
public final class TTSManager implements AutoCloseable {
    private final String ttsServiceClass;
    private final String outputClass;
    private final SpeechConfig config;
    private final List<TTSListener> listeners = new ArrayList<>();
    private TTSService ttsService;
    private SpeechOutput output;
    private Lifecycle lifecycle;
    private Context appContext;

    /**
     * Get the current TTS service.
     *
     * @return The TTS service being managed by this subsystem.
     */
    public TTSService getTtsService() {
        return ttsService;
    }

    /**
     * Get the current speech output component.
     *
     * @return The speech output component being managed by this subsystem.
     */
    public SpeechOutput getOutput() {
        return output;
    }

    /**
     * Construction only allowed via use of the builder.
     *
     * @param builder The builder used to construct this manager.
     * @throws Exception if an error occurs during initialization.
     */
    private TTSManager(Builder builder) throws Exception {
        this.ttsServiceClass = builder.ttsServiceClass;
        this.outputClass = builder.outputClass;
        this.config = builder.config;
        this.lifecycle = builder.lifecycle;
        this.listeners.addAll(builder.listeners);
        this.appContext = builder.appContext;
        prepare();
    }

    /**
     * Synthesizes a piece of text or SSML, dispatching the result to any
     * registered listeners.
     *
     * @param request The synthesis request data.
     */
    public void synthesize(SynthesisRequest request) {
        this.ttsService.synthesize(request);
    }

    /**
     * Registers the currently active lifecycle with the manager, allowing any
     * output classes to handle media player components based on system
     * lifecycle events.
     *
     * @param newLifecycle The current lifecycle.
     */
    public void registerLifecycle(@NonNull Lifecycle newLifecycle) {
        Lifecycle currentLifecycle = this.lifecycle;
        this.lifecycle = newLifecycle;
        if (output != null) {
            if (currentLifecycle != null) {
                currentLifecycle.removeObserver(output);
            }
            this.lifecycle.addObserver(output);
        }
    }

    /**
     * Sets the android context for the TTS Subsystem. Some components may
     * require an application context instead of an activity context;
     * see individual component documentation for details.
     *
     * @param androidContext the android context for the TTS subsystem.
     *
     * @see io.spokestack.spokestack.tts.SpokestackTTSOutput
     */
    public void setAndroidContext(Context androidContext) {
        this.appContext = androidContext;
        if (this.output != null) {
            this.output.setAndroidContext(androidContext);
        }
    }

    @Override
    public void close() {
        release();
        this.ttsService = null;
        this.output = null;
        this.listeners.clear();
    }

    /**
     * Dynamically constructs TTS component classes, allocating any resources
     * they control.
     *
     * @throws Exception If there is an error constructing TTS components.
     */
    public void prepare() throws Exception {
        if (this.ttsService != null) {
            throw new IllegalStateException("already prepared");
        }

        this.ttsService =
              createComponent(this.ttsServiceClass, TTSService.class);
        for (TTSListener listener : this.listeners) {
            this.ttsService.addListener(listener);
        }
        if (this.outputClass != null && this.output == null) {
            this.output = createComponent(this.outputClass, SpeechOutput.class);
            this.output.setAndroidContext(appContext);
            this.ttsService.addListener(this.output);
            if (this.lifecycle != null) {
                this.registerLifecycle(this.lifecycle);
            }
        }
    }

    private <T> T createComponent(String className, Class<T> clazz)
          throws Exception {
        Object constructed = Class
              .forName(className)
              .getConstructor(SpeechConfig.class)
              .newInstance(this.config);
        return clazz.cast(constructed);
    }

    /**
     * Stops activity in the TTS subsystem and releases any resources held by
     * its components. No internally queued audio will be played after this
     * method is called, and the queue will be cleared.
     */
    public void release() {
        if (this.output != null) {
            try {
                if (this.lifecycle != null) {
                    this.lifecycle.removeObserver(this.output);
                }
                this.output.close();
            } catch (Exception e) {
                raiseError(e);
            }
        }

        try {
            this.ttsService.close();
            this.ttsService = null;
        } catch (Exception e) {
            raiseError(e);
        }
    }

    private void raiseError(Throwable e) {
        TTSEvent event = new TTSEvent(TTSEvent.Type.ERROR);
        event.setError(e);
        for (TTSListener listener : this.listeners) {
            listener.eventReceived(event);
        }
    }

    /**
     * TTS manager builder.
     */
    public static final class Builder {
        private String ttsServiceClass;
        private String outputClass;
        private Context appContext;
        private Lifecycle lifecycle;
        private SpeechConfig config = new SpeechConfig();
        private List<TTSListener> listeners = new ArrayList<>();

        /**
         * Initializes a new builder with no default configuration.
         */
        public Builder() { }

        /**
         * Sets the class name of the external TTS service component.
         *
         * @param value TTS service component class name
         * @return this
         */
        public Builder setTTSServiceClass(String value) {
            this.ttsServiceClass = value;
            return this;
        }

        /**
         * Sets the class name of the audio output component.
         *
         * @param value Audio output component class name
         * @return this
         */
        public Builder setOutputClass(String value) {
            this.outputClass = value;
            return this;
        }

        /**
         * Attaches a configuration object.
         *
         * @param value configuration to attach
         * @return this
         */
        public Builder setConfig(SpeechConfig value) {
            this.config = value;
            return this;
        }

        /**
         * Sets a component configuration value.
         *
         * @param key   configuration property name
         * @param value property value
         * @return this
         */
        public Builder setProperty(String key, Object value) {
            this.config.put(key, value);
            return this;
        }

        /**
         * Sets the Android context for the pipeline. Some components may
         * require an Application context instead of an Activity context;
         * see individual component documentation for details.
         *
         * @param androidContext The Android context for the pipeline.
         * @return this
         * @see io.spokestack.spokestack.tts.SpokestackTTSOutput
         */
        public Builder setAndroidContext(Context androidContext) {
            this.appContext = androidContext;
            return this;
        }

        /**
         * Sets the manager's current lifecycle.
         *
         * @param curLifecycle The lifecycle dispatching events to this TTS
         *                     manager.
         * @return this
         */
        public Builder setLifecycle(Lifecycle curLifecycle) {
            this.lifecycle = curLifecycle;
            return this;
        }

        /**
         * Adds a TTS listener.
         *
         * @param listener listener implementation
         * @return this
         */
        public Builder addTTSListener(TTSListener listener) {
            this.listeners.add(listener);
            return this;
        }

        /**
         * Creates and initializes the TTS manager subsystem.
         *
         * @return configured TTS manager instance
         * @throws Exception if there is an error during construction.
         */
        public TTSManager build() throws Exception {
            return new TTSManager(this);
        }
    }

}
