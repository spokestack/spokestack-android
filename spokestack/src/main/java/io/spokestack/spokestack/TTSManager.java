package io.spokestack.spokestack;

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
 *     .setTtsService("io.spokestack.spokestack.tts.SpokestackTTSClient")
 *     .setOutputClass("io.spokestack.spokestack.tts.MediaPlayerOutput")
 *     .setProperty("api-key", "f854fbf30a5f40c189ecb1b38bc78059")
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
    private final ComponentConfig config;
    private final List<TTSListener> listeners = new ArrayList<>();
    private TTSService ttsService;
    private SpeechOutput output;

    private TTSManager(Builder builder) throws Exception {
        this.ttsServiceClass = builder.ttsServiceClass;
        this.outputClass = builder.outputClass;
        this.config = builder.config;
        this.listeners.addAll(builder.listeners);
        prepare();
    }

    @Override
    public void close() {
        release();
    }

    /**
     * Dynamically constructs TTS component classes, allocating any resources
     * they control.
     *
     * @throws Exception If there is an error constructing TTS components.
     */
    public void prepare() throws Exception {
        this.ttsService =
              createComponent(this.ttsServiceClass, TTSService.class);
        if (this.outputClass != null) {
            this.output = createComponent(this.outputClass, SpeechOutput.class);
            this.ttsService.addListener(this.output);
        }
    }

    private <T> T createComponent(String className, Class<T> clazz)
          throws Exception {
        Object constructed = Class
              .forName(className)
              .getConstructor(ComponentConfig.class)
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
                this.output.close();
            } catch (Exception e) {
                raiseError(e);
            }
        }

        try {
            this.ttsService.close();
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
        private ComponentConfig config = new ComponentConfig();
        private List<TTSListener> listeners = new ArrayList<>();

        /**
         * Initializes a new builder with no default configuration.
         */
        public Builder() {
        }

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
        public Builder setConfig(ComponentConfig value) {
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
         */
        public TTSManager build() throws Exception {
            return new TTSManager(this);
        }
    }

}
