package io.spokestack.spokestack;

import android.content.Context;

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
 *      .addStageClass("io.spokestack.spokestack.webrtc.VoiceActivityTrigger")
 *      .addStageClass("io.spokestack.spokestack.ActivationTimeout")
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
 * lifecycle. While stopped, the pipeline consumes as few resources as possible.
 * The pipeline runs asynchronously on a dedicated thread, so it does not block
 * the caller to perform I/O or speech processing.
 * </p>
 *
 * <p>
 * When running, the pipeline communicates with the client via the event
 * interface on the speech context. All calls to event handlers are made in the
 * context of the pipeline's thread, so event handlers should not perform
 * blocking operations, and should use message passing when communicating with
 * UI components, etc.
 * </p>
 */
public final class SpeechPipeline implements AutoCloseable {
    /**
     * audio sample rate default, in samples/sec.
     */
    public static final int DEFAULT_SAMPLE_RATE = 16000;
    /**
     * audio frame width, in ms.
     */
    public static final int DEFAULT_FRAME_WIDTH = 20;
    /**
     * audio frame buffer width, in ms.
     */
    public static final int DEFAULT_BUFFER_WIDTH = 20;

    private final Object lock = new Object();
    private final String inputClass;
    private final List<String> stageClasses;
    private final SpeechConfig config;
    private final SpeechContext context;
    private volatile boolean running;
    private volatile boolean paused;
    private SpeechInput input;
    private List<SpeechProcessor> stages;
    private Thread thread;
    private boolean managed;

    /**
     * initializes a new speech pipeline instance.
     *
     * @param builder pipeline builder with configuration parameters
     */
    private SpeechPipeline(Builder builder) {
        this.inputClass = builder.inputClass;
        this.stageClasses = builder.stageClasses;
        this.config = builder.config;
        this.context = new SpeechContext(this.config);
        this.context.setAndroidContext(builder.appContext);
        this.stages = new ArrayList<>();

        for (OnSpeechEventListener l : builder.listeners) {
            this.context.addOnSpeechEventListener(l);
        }
    }

    /**
     * shuts down the pipeline and releases its resources.
     */
    public void close() {
        stop();
    }

    /**
     * @return current pipeline configuration.
     */
    public SpeechConfig getConfig() {
        return this.config;
    }

    /**
     * @return current pipeline configuration.
     */
    public SpeechContext getContext() {
        return this.context;
    }

    /**
     * @return true if the pipeline has been started and is not paused,
     * false otherwise.
     */
    public boolean isRunning() {
        return this.running && !isPaused();
    }

    /**
     * @return true if the pipeline has been paused, false otherwise.
     */
    public boolean isPaused() {
        return this.paused;
    }

    /** manually activate the speech pipeline. */
    public void activate() {
        this.context.setActive(true);
    }

    /** manually deactivate the speech pipeline. */
    public void deactivate() {
        this.context.reset();
        for (SpeechProcessor stage : this.stages) {
            try {
                stage.reset();
            } catch (Exception e) {
                raiseError(e);
            }
        }
    }

    /**
     * Add a new listener to receive events from the speech pipeline.
     * @param listener The listener to add.
     */
    public void addListener(OnSpeechEventListener listener) {
        this.context.addOnSpeechEventListener(listener);
    }

    /**
     * Remove a pipeline listener, allowing it to be garbage collected.
     * @param listener The listener to remove.
     */
    public void removeListener(OnSpeechEventListener listener) {
        this.context.removeOnSpeechEventListener(listener);
    }

    /**
     * Starts the speech pipeline. If the pipeline is already running but has
     * been paused, it will be resumed.
     *
     * @throws Exception on configuration/startup error
     */
    public void start() throws Exception {
        if (this.running) {
            resume();
            this.context.traceDebug(
                  "attempting to start a running pipeline; ignoring");
            return;
        }

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
              .newInstance(new Object[]{this.config});

        // create the pipeline stage components
        for (String name : this.stageClasses) {
            this.stages.add((SpeechProcessor) Class
                  .forName(name)
                  .getConstructor(SpeechConfig.class)
                  .newInstance(new Object[]{this.config})
            );
        }
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
        for (int i = 0; i < frameCount; i++) {
            buffer.addLast(ByteBuffer
                  .allocateDirect(frameSize)
                  .order(ByteOrder.nativeOrder()));
        }

        // attach the buffers to the speech context
        this.context.attachBuffer(buffer);
    }

    private void startThread() throws Exception {
        this.thread = new Thread(this::run, "Spokestack-speech-pipeline");
        this.running = true;
        this.thread.start();
    }

    /**
     * Pauses the speech pipeline, temporarily stopping passive listening.
     *
     * <p>
     * Note that active listening (an active ASR stage) cannot be paused, so
     * the pipeline is deactivated before it is paused. This may prevent the
     * delivery of a
     * {@link io.spokestack.spokestack.SpeechContext.Event#RECOGNIZE} event if
     * an ASR request is currently in progress.
     * </p>
     *
     * <p>
     * While paused, the pipeline will not respond to the wakeword, but
     * in order to support a quick {@link #resume()}, it will retain control
     * of the microphone. No audio is explicitly read or analyzed. To fully
     * release the pipeline's resources, see {@link #stop()}.
     * </p>
     */
    public void pause() {
        deactivate();
        this.paused = true;
    }

    /**
     * Resumes a paused speech pipeline, returning the pipeline to a passive
     * listening state.
     *
     * Does nothing if the pipeline has not been previously paused.
     */
    public void resume() {
        if (!this.paused) {
            return;
        }
        this.paused = false;
        synchronized (lock) {
            lock.notify();
        }
    }

    /**
     * stops the speech pipeline and releases all resources.
     */
    public void stop() {
        if (this.running) {
            this.running = false;
            try {
                this.thread.join();
            } catch (InterruptedException e) {
                // ignore
            }
            this.thread = null;
        }
    }

    private void run() {
        synchronized (lock) {
            while (this.running) {
                step();
            }
            cleanup();
        }
    }

    private void step() {
        if (this.paused) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                this.running = false;
            }
        } else {
            dispatch();
        }
        if (Thread.currentThread().isInterrupted()) {
            this.running = false;
        }
    }

    private void dispatch() {
        try {
            // cycle the deque and fetch the next frame to write
            ByteBuffer frame = this.context.getBuffer().removeFirst();
            this.context.getBuffer().addLast(frame);

            // fill the frame from the input, stopping if audio cannot be read
            try {
                this.input.read(this.context, frame);
            } catch (Exception e) {
                raiseError(e);
                stop();
            }

            // when leaving the managed state, reset all stages internally
            boolean isManaged = this.context.isManaged();
            if (this.managed && !isManaged) {
                for (SpeechProcessor stage : this.stages) {
                    stage.reset();
                }
            }
            this.managed = isManaged;

            // dispatch the frame to the stages
            for (SpeechProcessor stage : this.stages) {
                if (!this.managed) {
                    frame.rewind();
                    stage.process(this.context, frame);
                }
            }
        } catch (Exception e) {
            raiseError(e);
        }
    }

    private void cleanup() {
        for (SpeechProcessor stage : this.stages) {
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
        private Context appContext;
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
         *
         * @param value input component class name
         * @return this
         */
        public Builder setInputClass(String value) {
            this.inputClass = value;
            return this;
        }

        /**
         * sets the class names of the pipeline stage components in bulk.
         *
         * @param value list of pipeline component names
         * @return this
         */
        public Builder setStageClasses(List<String> value) {
            this.stageClasses = value;
            return this;
        }

        /**
         * adds a single pipeline stage component class name.
         *
         * @param value stage component class name
         * @return this
         */
        public Builder addStageClass(String value) {
            this.stageClasses.add(value);
            return this;
        }

        /**
         * attaches a pipeline configuration object.
         *
         * @param value configuration to attach
         * @return this
         */
        public Builder setConfig(SpeechConfig value) {
            this.config = value;
            return this;
        }

        /**
         * Sets the android context for the pipeline. Some components may
         * require an application context instead of an activity context;
         * see individual component documentation for details.
         *
         * @param androidContext the android context for the pipeline.
         * @return this
         * @see io.spokestack.spokestack.android.AndroidSpeechRecognizer
         */
        public Builder setAndroidContext(Context androidContext) {
            this.appContext = androidContext;
            return this;
        }

        /**
         * sets a pipeline configuration value.
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
         * applies configuration from a {@link PipelineProfile} to the current
         * builder, returning the modified builder. subsequent calls to {@code
         * useProfile} or {@code setProperty} can override configuration set by
         * a profile.
         *
         * @param profileClass class name of the profile to apply.
         * @return an updated builder
         * @throws IllegalArgumentException if the specified profile does not
         *                                  exist
         */
        public Builder useProfile(String profileClass)
              throws IllegalArgumentException {
            PipelineProfile profile;
            try {
                profile = (PipelineProfile) Class
                      .forName(profileClass)
                      .getConstructor()
                      .newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException(
                      profileClass + " pipeline profile is invalid!");
            }

            return profile.apply(this);
        }

        /**
         * adds a pipeline event listener.
         *
         * @param listen listener callback
         * @return this
         */
        public Builder addOnSpeechEventListener(OnSpeechEventListener listen) {
            this.listeners.add(listen);
            return this;
        }

        /**
         * creates and initializes the speech pipeline.
         *
         * @return configured pipeline instance
         */
        public SpeechPipeline build() {
            return new SpeechPipeline(this);
        }
    }
}
