package io.spokestack.spokestack;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Spokestack speech pipeline.
 *
 * <p>
 * This is the primary client entry point to the Spokestack framework. It
 * dynamically binds to configured components that implement the pipeline
 * interfaces for reading audio frames and performing speech recognition tasks.
 * </p>
 *
 * <p>
 * Clients initialize the pipeline using the {@link Builder} class. The
 * following is an example pipeline that reads the android microphone and
 * performs voice activity detection (VAD).
 * </p>
 *
 * <pre>
 * {@code
 *  SpeechPipeline pipeline = new SpeechPipeline.Builder()
 *      .setInputClass("io.spokestack.spokestack.android.MicrophoneInput")
 *      .addStageClass("io.spokestack.spokestack.libfvad.VADTrigger")
 *      .setProperty("sample-rate", 16000)
 *      .setProperty("frame-width", 20)
 *      .setProperty("buffer-width", 300)
 *      .addOnSpeechEventListener(this)
 *      .build();
 *  pipeline.start();
 * }
 * </pre>
 *
 * <p>
 * The pipeline may be stopped/restarted any number of times during its
 * lifecycle. While stopped, the pipeline consumes as few resources as
 * possible. The pipeline runs asynchronously on a dedicated thread, so
 * it does not block the caller to perform I/O or speech processing.
 * </p>
 *
 * <p>
 * When running, the pipeline communicates with the client via the event
 * interface on the speech context. All calls to event handlers are made
 * in the context of the pipeline's thread, so event handlers should not
 * perform blocking operations, and should use message passing when
 * communicating with UI components, etc.
 * </p>
 */
public final class SpeechPipeline implements AutoCloseable {
    /** audio sample rate default, in samples/sec. */
    public static final int DEFAULT_SAMPLE_RATE = 16000;
    /** audio frame width, in ms. */
    public static final int DEFAULT_FRAME_WIDTH = 20;
    /** audio frame buffer width, in ms. */
    public static final int DEFAULT_BUFFER_WIDTH = 20;

    private final String inputClass;
    private final List<String> stageClasses;
    private final SpeechConfig config;
    private final SpeechContext context;
    private SpeechInput input;
    private List<SpeechProcessor> stages;
    private Thread thread;
    private boolean running;

    /**
     * initializes a new speech pipeline instance.
     * @param builder pipeline builder with configuration parameters
     */
    private SpeechPipeline(Builder builder) {
        this.inputClass = builder.inputClass;
        this.stageClasses = builder.stageClasses;
        this.config = builder.config;
        this.context = new SpeechContext(this.config);
        this.stages = new ArrayList<>();

        for (OnSpeechEventListener l: builder.listeners)
            this.context.addOnSpeechEventListener(l);
    }

    /**
     * shuts down the pipeline and releases its resources.
     */
    public void close() {
        stop();
    }

    /** @return current pipeline configuration. */
    public SpeechConfig getConfig() {
        return this.config;
    }

    /** @return current pipeline configuration. */
    public SpeechContext getContext() {
        return this.context;
    }

    /** @return true if the pipeline is listening, false otherwise. */
    public boolean isRunning() {
        return this.running;
    }

    /**
     * starts up the speech pipeline.
     * @throws Exception on configuration/startup error
     */
    public void start() throws Exception {
        if (this.running)
            throw new IllegalStateException("already running");

        try {
            createComponents();
            attachBuffer();
            startThread();
        } catch (Throwable e) {
            stop();
            throw e;
        }
    }

    private void createComponents() throws Exception {
        // create the audio input component
        this.input = (SpeechInput) Class
            .forName(this.inputClass)
            .getConstructor(SpeechConfig.class)
            .newInstance(new Object[] {this.config});

        // create the pipeline stage components
        for (String name: this.stageClasses)
            this.stages.add((SpeechProcessor) Class
                .forName(name)
                .getConstructor(SpeechConfig.class)
                .newInstance(new Object[] {this.config})
            );
    }

    private void attachBuffer() throws Exception {
        // compute the frame size and number of buffers
        int sampleWidth = 2;
        int sampleRate = this.config.getInteger("sample-rate");
        int frameWidth = this.config.getInteger("frame-width");
        int bufferWidth = this.config.getInteger("buffer-width");
        int frameSize = sampleRate * frameWidth / 1000 * sampleWidth;
        int frameCount = Math.max(bufferWidth / frameWidth, 1);

        // allocate the deque of frame buffers
        LinkedList<ByteBuffer> buffer = new LinkedList<>();
        for (int i = 0; i < frameCount; i++)
            buffer.addLast(ByteBuffer
                .allocateDirect(frameSize)
                .order(ByteOrder.nativeOrder()));

        // attach the buffers to the speech context
        this.context.attachBuffer(buffer);
    }

    private void startThread() throws Exception {
        this.thread = new Thread(new Runnable() {
            public void run() {
                SpeechPipeline.this.run();
            }
        });
        this.running = true;
        this.thread.start();
    }

    /**
     * stops the speech pipeline and releases all resources.
     */
    public void stop() {
        if (this.running) {
            this.running = false;
            try {
                this.thread.join();
            } catch (InterruptedException e) { }
            this.thread = null;
        }
    }

    private void run() {
        while (this.running)
            dispatch();
        cleanup();
    }

    private void dispatch() {
        try {
            // cycle the deque and fetch the next frame to write
            ByteBuffer frame = this.context.getBuffer().removeFirst();
            this.context.getBuffer().addLast(frame);

            // fill the frame from the input
            this.input.read(frame);

            // dispatch the frame to the stages
            for (SpeechProcessor stage: this.stages) {
                frame.rewind();
                stage.process(this.context, frame);
            }
        } catch (Exception e) {
            raiseError(e);
        }
    }

    private void cleanup() {
        for (SpeechProcessor stage: this.stages) {
            try {
                stage.close();
            } catch (Exception e) {
                raiseError(e);
            }
        }
        this.stages.clear();

        try {
            this.input.close();
        } catch (Exception e) {
            raiseError(e);
        }
        this.input = null;

        this.context.reset();
        this.context.detachBuffer();
    }

    private void raiseError(Throwable e) {
        this.context.setError(e);
        this.context.dispatch(SpeechContext.Event.ERROR);
    }

    /**
     * speech pipeline builder.
     */
    public static final class Builder {
        private String inputClass;
        private List<String> stageClasses = new ArrayList<>();
        private SpeechConfig config = new SpeechConfig();
        private List<OnSpeechEventListener> listeners = new ArrayList<>();

        /**
         * initializes a new builder instance.
         */
        public Builder() {
            this.config.put("sample-rate", DEFAULT_SAMPLE_RATE);
            this.config.put("frame-width", DEFAULT_FRAME_WIDTH);
            this.config.put("buffer-width", DEFAULT_BUFFER_WIDTH);
        }

        /**
         * sets the class name of the audio input component.
         * @param value input component class name
         * @return this
         */
        public Builder setInputClass(String value) {
            this.inputClass = value;
            return this;
        }

        /**
         * sets the class names of the pipeline stage components in bulk.
         * @param value list of pipeline component names
         * @return this
         */
        public Builder setStageClasses(List<String> value) {
            this.stageClasses = value;
            return this;
        }

        /**
         * adds a single pipeline stage component class name.
         * @param value stage component class name
         * @return this
         */
        public Builder addStageClass(String value) {
            this.stageClasses.add(value);
            return this;
        }

        /**
         * attaches a pipeline configuration object.
         * @param value configuration to attach
         * @return this
         */
        public Builder setConfig(SpeechConfig value) {
            this.config = value;
            return this;
        }

        /**
         * sets a pipeline configuration value.
         * @param key   configuration property name
         * @param value property value
         * @return this
         */
        public Builder setProperty(String key, Object value) {
            this.config.put(key, value);
            return this;
        }

        /**
         * adds a pipeline event listener.
         * @param listen listener callback
         * @return this
         */
        public Builder addOnSpeechEventListener(OnSpeechEventListener listen) {
            this.listeners.add(listen);
            return this;
        }

        /**
         * creates and initializes the speech pipeline.
         * @return configured pipeline instance
         */
        public SpeechPipeline build() {
            return new SpeechPipeline(this);
        }
    }
}
