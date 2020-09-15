package io.spokestack.spokestack;

import android.content.Context;
import androidx.lifecycle.Lifecycle;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.nlu.NLUService;
import io.spokestack.spokestack.nlu.tensorflow.TensorflowNLU;
import io.spokestack.spokestack.tts.SynthesisRequest;
import io.spokestack.spokestack.tts.TTSManager;
import io.spokestack.spokestack.util.AsyncResult;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * This class combines all Spokestack subsystems into a single component to
 * provide a unified interface to the library's ASR, NLU, and TTS features. Like
 * the individual subsystems, it is configurable using a fluent builder pattern,
 * but it provides a default configuration; only a few parameters are required
 * from the calling application, and those only for specific features noted in
 * the documentation for the builder's methods.
 *
 * <p>
 * Client applications may wish to establish event listeners for purposes such
 * as logging, but events necessary to forward user interactions through the
 * system (for example, sending ASR transcripts through NLU) are handled
 * entirely by this class. This includes internal management of TTS playback,
 * which requires the client application to declare additional media player
 * dependencies; see
 * <a href="https://www.spokestack.io/docs/Android/tts#prerequisites">the
 * documentation</a> for more details. This feature can be disabled via the
 * builder if desired.
 * </p>
 *
 * <p>
 * In general, the default configuration assumes that the client application
 * wants to use all of Spokestack's features, regardless of their implied
 * dependencies or required configuration, so an error will be thrown if any
 * prerequisite is missing at build time. Individual features can be disabled
 * via the builder.
 * </p>
 *
 * <p>
 * Convenience methods are provided to interact with the most important features
 * of individual subsystems, but they do not completely duplicate the
 * subsystems' public APIs. Each subsystem in use can be retrieved via its own
 * getter, enabling use of its full API.
 * </p>
 *
 * <p>
 * Any convenience methods called on a subsystem that has been explicitly
 * disabled will result in a {@code NullPointerException}.
 * </p>
 *
 * <p>
 * This class is not threadsafe; public methods used to interact with Spokestack
 * subsystems should be called from the same thread. The subsystems themselves
 * use background threads where appropriate to perform intensive tasks.
 * </p>
 *
 * @see SpeechPipeline
 * @see TensorflowNLU
 * @see TTSManager
 */
public final class Spokestack extends SpokestackAdapter
      implements AutoCloseable {

    private final List<SpokestackAdapter> listeners;
    private SpeechPipeline speechPipeline;
    private TensorflowNLU nlu;
    private TTSManager tts;

    /**
     * Construct a new Spokestack wrapper from an existing builder.
     *
     * @param builder The builder used to construct the Spokestack wrapper.
     * @throws Exception if there is an error during initialization.
     */
    private Spokestack(Builder builder) throws Exception {
        this.listeners = new ArrayList<>();
        this.listeners.addAll(builder.listeners);
        if (builder.useAsr) {
            this.speechPipeline = builder.getPipelineBuilder()
                  .addOnSpeechEventListener(this)
                  .build();
        }
        if (builder.useNLU) {
            this.nlu = builder.getNluBuilder()
                  .addTraceListener(this)
                  .build();
        }
        if (builder.useTTS) {
            if (!builder.useTTSPlayback) {
                builder.ttsBuilder.setOutputClass(null);
            }
            this.tts = builder.getTtsBuilder()
                  .addTTSListener(this)
                  .build();
        }
    }

    // speech pipeline

    /**
     * @return The speech pipeline currently in use.
     */
    public SpeechPipeline getSpeechPipeline() {
        return speechPipeline;
    }

    /**
     * Starts the speech pipeline in order to process user input via the
     * microphone (or chosen input class).
     *
     * @throws Exception if there is an error configuring or starting the speech
     *                   pipeline.
     */
    public void start() throws Exception {
        this.speechPipeline.start();
    }

    /**
     * Stops the speech pipeline and releases all its internal resources.
     */
    public void stop() {
        this.speechPipeline.stop();
    }

    /**
     * Manually activate the speech pipeline, forcing the current ASR class to
     * begin recognizing speech. Useful when a wakeword is not employed, or in
     * conjunction with a microphone button.
     */
    public void activate() {
        this.speechPipeline.activate();
    }

    /**
     * Manually deactivate the speech pipeline, forcing the current ASR class to
     * stop recognizing speech. Useful in conjunction with a hold-to-talk
     * button.
     *
     * <p>
     * <b>Note</b>: This method currently has no effect on the Android speech
     * recognizer this class uses by default.
     * </p>
     */
    public void deactivate() {
        this.speechPipeline.deactivate();
    }

    // NLU

    /**
     * @return The NLU service currently in use.
     */
    public NLUService getNlu() {
        return nlu;
    }

    /**
     * Classify a user utterance, returning a wrapper that can either block
     * until the classification is complete or call a registered callback when
     * the result is ready.
     *
     * <p>
     * This convenience method automatically registers all {@code
     * SpokestackAdapter}s added to this class at build time to receive the
     * classification result asynchronously.
     * </p>
     *
     * @param utterance The utterance to classify.
     * @return An object representing the result of the asynchronous
     * classification.
     */
    public AsyncResult<NLUResult> classify(String utterance) {
        return classifyInternal(utterance);
    }

    // TTS

    /**
     * @return The TTS manager currently in use.
     */
    public TTSManager getTts() {
        return tts;
    }

    /**
     * Dynamically constructs TTS component classes, allocating any resources
     * they control. It is only necessary to explicitly call this if the TTS
     * subsystem's resources have been freed via {@link #releaseTts()} or {@link
     * #close()}.
     *
     * @throws Exception If there is an error constructing TTS components.
     */
    public void prepareTts() throws Exception {
        this.tts.prepare();
    }

    /**
     * Stops activity in the TTS subsystem and releases any resources held by
     * its components. No internally queued audio will be played after this
     * method is called, and the queue will be cleared.
     *
     * <p>
     * Once released, an explicit call to {@link #prepareTts()} is required to
     * reallocate TTS resources.
     * </p>
     */
    public void releaseTts() {
        this.tts.release();
    }

    /**
     * Synthesizes a piece of text or SSML, dispatching the result to any
     * registered listeners.
     *
     * @param request The synthesis request data.
     */
    public void synthesize(SynthesisRequest request) {
        this.tts.synthesize(request);
    }

    /**
     * Stops playback of any playing or queued synthesis results.
     */
    public void stopPlayback() {
        this.tts.stopPlayback();
    }

    // listeners

    @Override
    public void onEvent(@NotNull SpeechContext.Event event,
                        @NotNull SpeechContext context) {
        // automatically classify final ASR transcripts
        if (event == SpeechContext.Event.RECOGNIZE) {
            if (this.nlu != null) {
                classifyInternal(context.getTranscript());
            }
        }
    }

    private AsyncResult<NLUResult> classifyInternal(String text) {
        AsyncResult<NLUResult> result =
              this.nlu.classify(text);
        result.registerCallback(this);
        for (SpokestackAdapter listener : this.listeners) {
            result.registerCallback(listener);
        }
        return result;
    }

    @Override
    public void close() {
        if (this.speechPipeline != null) {
            this.speechPipeline.close();
        }
        if (this.tts != null) {
            this.tts.close();
        }
    }

    /**
     * Fluent builder interface for configuring Spokestack.
     *
     * @see Spokestack
     * @see Builder#Builder()
     */
    public static class Builder {
        private final SpeechConfig speechConfig;
        private final SpeechPipeline.Builder pipelineBuilder;
        private final TensorflowNLU.Builder nluBuilder;
        private final TTSManager.Builder ttsBuilder;
        private final List<SpokestackAdapter> listeners = new ArrayList<>();

        private boolean useAsr = true;
        private boolean useNLU = true;
        private boolean useTTS = true;
        private boolean useTTSPlayback = true;

        private Context appContext;
        private Lifecycle appLifecycle;

        /**
         * Create a Spokestack builder with a default configuration. The speech
         * pipeline will use the {@link
         * io.spokestack.spokestack.profile.TFWakewordAndroidASR
         * TFWakewordAndroidASR} profile, and all features will be enabled.
         *
         * <p>
         * Internally, this builder delegates to the builder APIs of individual
         * subsystems. These individual builders can be retrieved and customized
         * as desired. Calls to {@link #setProperty(String, Object)} are
         * propagated to all subsystems.
         * </p>
         *
         * <p>
         * Some subsystems require additional configuration that cannot be set
         * automatically. Properties are set via {@link #setProperty(String,
         * Object)}; other configuration is listed by method:
         * </p>
         *
         * <ul>
         *     <li>
         *         Wakeword detection (properties)
         *     <ul>
         *   <li>
         *      <b>wake-filter-path</b> (string): file system path to the
         *      "filter" Tensorflow Lite model.
         *   </li>
         *   <li>
         *      <b>wake-encode-path</b> (string): file system path to the
         *      "encode" Tensorflow Lite model.
         *   </li>
         *   <li>
         *      <b>wake-detect-path</b> (string): file system path to the
         *      "detect" Tensorflow Lite model.
         *   </li>
         *     </ul>
         *     </li>
         *     <li>
         *         NLU (properties)
         *     <ul>
         *   <li>
         *      <b>nlu-model-path</b> (string): file system path to the NLU
         *      TensorFlow Lite model.
         *   </li>
         *   <li>
         *      <b>nlu-metadata-path</b> (string): file system path to the
         *      model's metadata, used to decode intent and slot names and
         *      types.
         *   </li>
         *   <li>
         *      <b>wordpiece-vocab-path</b> (string): file system path to the
         *      wordpiece vocabulary file used by the wordpiece token encoder.
         *   </li>
         *     </ul>
         *     </li>
         *     <li>
         *         TTS (properties)
         *     <ul>
         *   <li>
         *      <b>spokestack-id</b> (string): client ID used to authorize TTS
         *      requests; see <a href="https://spokestack.io/account">
         *          https://spokestack.io/account</a>.
         *   </li>
         *   <li>
         *      <b>spokestack-secret</b> (string): client secret used to
         *      authorize TTS requests; see
         *      <a href="https://spokestack.io/account">
         *          https://spokestack.io/account</a>.
         *   </li>
         *   </ul>
         *     </li>
         *     <li>
         *         TTS (other)
         *     <ul>
         *   <li>
         *       {@link #setAndroidContext(android.content.Context)}:
         *       Android Application context used to manage the audio session
         *       for automatic playback.
         *   </li>
         *   <li>
         *       {@link #setLifecycle(androidx.lifecycle.Lifecycle)}:
         *       Android lifecycle context used to manage automatic pausing and
         *       resuming of audio on application lifecycle events.
         *   </li>
         *   </ul>
         *     </li>
         * </ul>
         */
        public Builder() {
            this.speechConfig = new SpeechConfig();
            String profileClass =
                  "io.spokestack.spokestack.profile.TFWakewordAndroidASR";
            this.pipelineBuilder =
                  new SpeechPipeline.Builder()
                        .setConfig(this.speechConfig)
                        .useProfile(profileClass);
            this.nluBuilder =
                  new TensorflowNLU.Builder().setConfig(this.speechConfig);
            String ttsServiceClass =
                  "io.spokestack.spokestack.tts.SpokestackTTSService";
            String ttsOutputClass =
                  "io.spokestack.spokestack.tts.SpokestackTTSOutput";
            this.ttsBuilder =
                  new TTSManager.Builder()
                        .setTTSServiceClass(ttsServiceClass)
                        .setOutputClass(ttsOutputClass)
                        .setConfig(this.speechConfig);
        }

        /**
         * Construct a wrapper builder with specific subsystem builders. Used
         * for testing.
         *
         * @param pipeline the speech pipeline builder
         * @param nlu      the NLU builder
         * @param tts      the TTS builder
         */
        Builder(SpeechPipeline.Builder pipeline, TensorflowNLU.Builder nlu,
                TTSManager.Builder tts) {
            this.speechConfig = new SpeechConfig();
            this.pipelineBuilder = pipeline;
            this.nluBuilder = nlu;
            this.ttsBuilder = tts;
        }

        /**
         * @return The builder used to configure the speech pipeline.
         */
        public SpeechPipeline.Builder getPipelineBuilder() {
            return pipelineBuilder;
        }

        /**
         * @return The builder used to configure the NLU subsystem.
         */
        public TensorflowNLU.Builder getNluBuilder() {
            return nluBuilder;
        }

        /**
         * @return The builder used to configure the TTS subsystem.
         */
        public TTSManager.Builder getTtsBuilder() {
            return ttsBuilder;
        }

        /**
         * Sets configuration for all subsystem builders.
         *
         * @param config configuration to attach
         * @return the updated builder
         */
        public Builder setConfig(SpeechConfig config) {
            this.pipelineBuilder.setConfig(config);
            this.nluBuilder.setConfig(config);
            this.ttsBuilder.setConfig(config);
            return this;
        }

        /**
         * Sets a configuration value.
         *
         * @param key   Configuration property name
         * @param value Property value
         * @return the updated builder
         */
        public Builder setProperty(String key, Object value) {
            this.speechConfig.put(key, value);
            return this;
        }

        /**
         * Sets the Android Context for the pipeline. Should be an Application
         * Context rather than an Activity Context.
         *
         * @param androidContext the Android Application Context.
         * @return the updated builder
         */
        public Builder setAndroidContext(Context androidContext) {
            this.appContext = androidContext;
            this.pipelineBuilder.setAndroidContext(androidContext);
            this.ttsBuilder.setAndroidContext(androidContext);
            return this;
        }

        /**
         * Sets the Android Lifecycle used for management of TTS playback.
         *
         * @param lifecycle the Android Lifecycle.
         * @return the updated builder
         */
        public Builder setLifecycle(Lifecycle lifecycle) {
            this.appLifecycle = lifecycle;
            this.ttsBuilder.setLifecycle(lifecycle);
            return this;
        }

        /**
         * Signal that Spokestack's speech pipeline should not be used to
         * recognize speech.
         *
         * @return the updated builder
         */
        public Builder disableAsr() {
            this.useAsr = false;
            return this;
        }

        /**
         * Signal that Spokestack's TensorFlow Lite wakeword detector should not
         * be used. This is equivalent to calling
         * <pre>
         * builder
         *     .getPipelineBuilder()
         *     .useProfile(
         *         "io.spokestack.spokestack.profile.PushToTalkAndroidASR");
         * </pre>
         * <p>
         * If a different profile is specified using the above approach, or if
         * the speech pipeline is disabled altogether with {@link
         * #disableAsr()}, this method should not be called.
         *
         * @return the updated builder
         */
        public Builder disableWakeword() {
            String profileClass =
                  "io.spokestack.spokestack.profile.PushToTalkAndroidASR";
            this.pipelineBuilder.useProfile(profileClass);
            return this;
        }

        /**
         * Signal that Spokestack's NLU subsystem should not be used.
         *
         * @return the updated builder
         */
        public Builder disableNlu() {
            this.useNLU = false;
            return this;
        }

        /**
         * Signal that Spokestack's TTS subsystem should not be used.
         *
         * @return the updated builder
         */
        public Builder disableTts() {
            this.useTTS = false;
            return this;
        }

        /**
         * Signal that Spokestack should not automatically manage TTS playback.
         * To disable TTS altogether, call {@link #disableTts()}; calling both
         * is unnecessary.
         *
         * @return the updated builder
         */
        public Builder disableTtsPlayback() {
            this.useTTSPlayback = false;
            return this;
        }

        /**
         * Add a listener that receives events from all subsystems. This method
         * is provided as a convenience; if desired, specific listeners can
         * still be added by retrieving the relevant subsystem builder and
         * adding a purpose-built listener to it.
         *
         * @param listener A listener that will receive events from all
         *                 Spokestack subsystems.
         * @return the updated builder
         */
        public Builder addListener(SpokestackAdapter listener) {
            this.pipelineBuilder.addOnSpeechEventListener(listener);
            this.nluBuilder.addTraceListener(listener);
            this.ttsBuilder.addTTSListener(listener);
            this.listeners.add(listener);
            return this;
        }

        /**
         * Use the current state of the builder to construct a full Spokestack
         * system.
         *
         * @return A Spokestack system configured with the current state of the
         * builder.
         * @throws Exception if required configuration is missing, or there is
         *                   an error during Spokestack initialization.
         */
        public Spokestack build() throws Exception {
            if (useTTS && useTTSPlayback) {
                if (this.appContext == null) {
                    throw new IllegalArgumentException("app context is "
                          + "required for playback management; see"
                          + "TTSManager.Builder.setAndroidContext()");
                }
                if (this.appLifecycle == null) {
                    throw new IllegalArgumentException("app lifecycle is "
                          + "required for playback management; see"
                          + "TTSManager.Builder.setLifecycle()");
                }
            }
            return new Spokestack(this);
        }
    }
}
