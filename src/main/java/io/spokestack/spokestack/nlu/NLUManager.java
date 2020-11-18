package io.spokestack.spokestack.nlu;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.nlu.tensorflow.TensorflowNLU;
import io.spokestack.spokestack.nlu.tensorflow.parsers.DigitsParser;
import io.spokestack.spokestack.nlu.tensorflow.parsers.IdentityParser;
import io.spokestack.spokestack.nlu.tensorflow.parsers.IntegerParser;
import io.spokestack.spokestack.nlu.tensorflow.parsers.SelsetParser;
import io.spokestack.spokestack.util.AsyncResult;
import io.spokestack.spokestack.util.EventTracer;
import io.spokestack.spokestack.util.TraceListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager for natural language understanding (NLU) components in Spokestack.
 *
 * <p>
 * Spokestack's NLU manager follows the same setup pattern as its {@link
 * io.spokestack.spokestack.SpeechPipeline} and {@link io.spokestack.spokestack.tts.TTSManager}
 * modules. The manager constructs the component ultimately responsible for
 * classification (an {@link NLUService}) and manages the context required to
 * perform these classifications and dispatch events to registered listeners.
 * </p>
 */
public final class NLUManager {
    private final NLUService nlu;
    private final NLUContext context;

    /**
     * Constructs a new {@code NLUManager} with an initialized NLU service.
     *
     * @param builder builder with configuration parameters
     * @throws Exception If there is an error constructing the service.
     */
    private NLUManager(Builder builder) throws Exception {
        this.context = builder.context;
        this.nlu = buildService(builder);
    }

    private NLUService buildService(Builder builder) throws Exception {
        Object constructed = Class
              .forName(builder.serviceClass)
              .getConstructor(SpeechConfig.class, NLUContext.class)
              .newInstance(builder.config, builder.context);
        return (NLUService) constructed;
    }

    /**
     * Classify a user utterance, returning a wrapper that can either block
     * until the classification is complete or call a registered callback when
     * the result is ready.
     *
     * @param utterance The utterance to classify.
     * @return An object representing the result of the asynchronous
     * classification.
     */
    public AsyncResult<NLUResult> classify(String utterance) {
        return this.nlu.classify(utterance, this.context);
    }

    /**
     * Add a new listener to receive trace events from the NLU subsystem.
     *
     * @param listener The listener to add.
     */
    public void addListener(TraceListener listener) {
        this.context.addTraceListener(listener);
    }

    /**
     * Remove a trace listener, allowing it to be garbage collected.
     *
     * @param listener The listener to remove.
     */
    public void removeListener(TraceListener listener) {
        this.context.removeTraceListener(listener);
    }

    /**
     * Fluent builder interface for initializing an NLU manager.
     */
    public static class Builder {
        private final List<TraceListener> traceListeners = new ArrayList<>();
        private NLUContext context;
        private SpeechConfig config = new SpeechConfig();
        private String serviceClass;

        /**
         * Creates a new builder instance.
         */
        public Builder() {
            config.put("trace-level", EventTracer.Level.ERROR.value());
            config.put("slot-digits", DigitsParser.class.getName());
            config.put("slot-integer", IntegerParser.class.getName());
            config.put("slot-selset", SelsetParser.class.getName());
            config.put("slot-entity", IdentityParser.class.getName());
            this.serviceClass = TensorflowNLU.class.getCanonicalName();
        }

        /**
         * Sets the name of the NLU service class to be used.
         *
         * @param className The name of the NLU service class to be used.
         * @return this
         */
        public Builder setServiceClass(String className) {
            this.serviceClass = className;
            return this;
        }

        /**
         * Attaches a configuration object, overwriting any existing
         * configuration.
         *
         * @param value configuration to attach
         * @return this
         */
        public Builder setConfig(SpeechConfig value) {
            this.config = value;
            return this;
        }

        /**
         * Sets a configuration value.
         *
         * @param key   configuration property name
         * @param value property value
         * @return this
         */
        public Builder setProperty(String key, Object value) {
            config.put(key, value);
            return this;
        }

        /**
         * Adds a trace listener to receive events from the NLU system.
         *
         * @param listener the listener to register
         * @return this
         */
        public Builder addTraceListener(TraceListener listener) {
            this.traceListeners.add(listener);
            return this;
        }

        /**
         * Create a new NLU service, automatically loading any necessary
         * resources in the background. Any errors encountered during loading
         * will be reported to registered {@link TraceListener}s.
         *
         * @return An initialized {@code NLUManager} instance
         * @throws Exception If there is an error constructing the NLU service.
         */
        public NLUManager build() throws Exception {
            this.context = new NLUContext(this.config);
            for (TraceListener listener : this.traceListeners) {
                this.context.addTraceListener(listener);
            }
            return new NLUManager(this);
        }

    }
}
