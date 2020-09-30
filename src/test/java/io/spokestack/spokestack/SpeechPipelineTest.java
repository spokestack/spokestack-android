package io.spokestack.spokestack;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.nio.ByteBuffer;

import androidx.annotation.NonNull;
import io.spokestack.spokestack.android.AudioRecordError;
import io.spokestack.spokestack.util.EventTracer;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

public class SpeechPipelineTest implements OnSpeechEventListener {
    private static final List<Class<?>> PROFILES = Arrays.asList(
          io.spokestack.spokestack.profile.PushToTalkAndroidASR.class,
          io.spokestack.spokestack.profile.PushToTalkAzureASR.class,
          io.spokestack.spokestack.profile.PushToTalkGoogleASR.class,
          io.spokestack.spokestack.profile.PushToTalkSpokestackASR.class,
          io.spokestack.spokestack.profile.TFWakewordAndroidASR.class,
          io.spokestack.spokestack.profile.TFWakewordAzureASR.class,
          io.spokestack.spokestack.profile.TFWakewordGoogleASR.class,
          io.spokestack.spokestack.profile.TFWakewordSpokestackASR.class,
          io.spokestack.spokestack.profile.VADTriggerAndroidASR.class,
          io.spokestack.spokestack.profile.VADTriggerAzureASR.class,
          io.spokestack.spokestack.profile.VADTriggerGoogleASR.class,
          io.spokestack.spokestack.profile.VADTriggerSpokestackASR.class
    );

    private List<SpeechContext.Event> events = new ArrayList<>();

    @Before
    public void before() {
        this.events.clear();
    }

    @Test
    public void testBuilder() throws Exception {
        // default config
        try (SpeechPipeline pipeline = new SpeechPipeline.Builder()
                .build()) {
            assertEquals(
                SpeechPipeline.DEFAULT_SAMPLE_RATE,
                pipeline.getConfig().getInteger("sample-rate")
            );
            assertEquals(
                SpeechPipeline.DEFAULT_FRAME_WIDTH,
                pipeline.getConfig().getInteger("frame-width")
            );
            assertEquals(
                SpeechPipeline.DEFAULT_BUFFER_WIDTH,
                pipeline.getConfig().getInteger("buffer-width")
            );
        }

        // invalid pipeline
        try (SpeechPipeline pipeline = new SpeechPipeline.Builder()
                .setInputClass("invalid")
                .build()) {
            assertThrows(Exception.class, new Executable() {
                public void execute() throws Exception { pipeline.start(); }
            });
        }

        // attach config
        SpeechConfig config = new SpeechConfig();
        config.put("sample-rate", 8000);
        config.put("frame-width", 10);
        config.put("buffer-width", 300);
        try (SpeechPipeline pipeline = new SpeechPipeline.Builder()
                .setInputClass("io.spokestack.spokestack.SpeechPipelineTest$Input")
                .setConfig(config)
                .build()) {
            pipeline.start();
            assertEquals(8000, pipeline.getConfig().getInteger("sample-rate"));
            assertEquals(10, pipeline.getConfig().getInteger("frame-width"));
            assertEquals(300, pipeline.getConfig().getInteger("buffer-width"));
            Input.stop();
        }

        // attach stages
        List<String> stages = new ArrayList<>();
        stages.add("io.spokestack.spokestack.SpeechPipelineTest$Stage");
        try (SpeechPipeline pipeline = new SpeechPipeline.Builder()
                .setInputClass("io.spokestack.spokestack.SpeechPipelineTest$Input")
                .setStageClasses(stages)
                .build()) {
            pipeline.start();
            Input.stop();
        }
    }

    @Test
    public void testProfiles() {
        assertThrows(IllegalArgumentException.class, () ->
              new SpeechPipeline.Builder()
                    .useProfile("io.spokestack.InvalidProfile")
        );

        // no pre-set profiles should throw errors on use
        // (use instantiates the associated profile class)
        for (Class<?> profileClass : PROFILES) {
            new SpeechPipeline.Builder()
                  .useProfile(profileClass.getCanonicalName());
        }

        // The implicated class requires a config property
        SpeechPipeline pipeline = new SpeechPipeline.Builder()
              .useProfile(
                    "io.spokestack.spokestack.SpeechPipelineTest$TestProfile")
              .build();

        assertThrows(InvocationTargetException.class, pipeline::start);
    }

    @Test
    public void testStartStop() throws Exception {
        final SpeechPipeline pipeline = new SpeechPipeline.Builder()
            .setInputClass("io.spokestack.spokestack.SpeechPipelineTest$Input")
            .addStageClass("io.spokestack.spokestack.SpeechPipelineTest$Stage")
            .setProperty("sample-rate", 16000)
            .setProperty("frame-width", 20)
            .setProperty("buffer-width", 300)
            .setProperty("trace-level", EventTracer.Level.INFO.value())
            .addOnSpeechEventListener(this)
            .build();

        // startup
        pipeline.start();
        assertTrue(pipeline.isRunning());
        assertEquals(15, pipeline.getContext().getBuffer().size());
        assertEquals(0, Input.counter);
        assertTrue(Stage.open);

        // idempotent restart
        pipeline.start();
        assertEquals(SpeechContext.Event.TRACE, this.events.get(0));

        // first frame
        transact(false);
        assertEquals(SpeechContext.Event.ACTIVATE, this.events.get(0));
        assertTrue(pipeline.getContext().isActive());

        // next frame
        transact(false);
        assertEquals(SpeechContext.Event.DEACTIVATE, this.events.get(0));
        assertFalse(pipeline.getContext().isActive());

        // third frame reactivates the context
        transact(false);
        assertEquals(SpeechContext.Event.ACTIVATE, this.events.get(0));
        assertTrue(pipeline.getContext().isActive());

        // shutdown
        Input.stop();
        pipeline.close();
        assertFalse(pipeline.isRunning());
        assertFalse(pipeline.getContext().isActive());
        assertEquals(-1, Input.counter);
        assertFalse(Stage.open);
    }

    @Test
    public void testInputFailure() throws Exception {
        SpeechPipeline pipeline = new SpeechPipeline.Builder()
            .setInputClass("io.spokestack.spokestack.SpeechPipelineTest$FailInput")
            .addOnSpeechEventListener(this)
            .build();
        pipeline.start();

        // wait for pipeline to shut down due to error
        while (pipeline.isRunning()) {
            Thread.sleep(1);
        }
        assertEquals(SpeechContext.Event.ERROR, this.events.get(0));
    }

    @Test
    public void testStageFailure() throws Exception {
        SpeechPipeline pipeline = new SpeechPipeline.Builder()
            .setInputClass("io.spokestack.spokestack.SpeechPipelineTest$Input")
            .addStageClass("io.spokestack.spokestack.SpeechPipelineTest$FailStage")
            .addOnSpeechEventListener(this)
            .build();
        pipeline.start();

        transact(false);
        assertEquals(SpeechContext.Event.ERROR, this.events.get(0));
        this.events.clear();

        Input.stop();
        pipeline.stop();
        assertEquals(SpeechContext.Event.ERROR, this.events.get(0));
    }

    @Test
    public void testContextManagement() throws Exception {
        SpeechPipeline pipeline = new SpeechPipeline.Builder()
              .setInputClass("io.spokestack.spokestack.SpeechPipelineTest$ManagedInput")
              .addStageClass("io.spokestack.spokestack.SpeechPipelineTest$Stage")
              .addOnSpeechEventListener(this)
              .build();
        pipeline.start();

        // no activation event because stages don't run
        pipeline.getContext().setManaged(true);
        transact(true);
        assertTrue(this.events.isEmpty());
        assertFalse(pipeline.getContext().isActive());

        // still no event, and the external activation isn't overridden by the
        // stage, which would normally deactivate on the second frame
        pipeline.activate();
        transact(true);
        assertTrue(this.events.isEmpty());
        assertTrue(pipeline.getContext().isActive());

        // ensure that manual deactivation resets stages
        assertFalse(Stage.reset);
        pipeline.deactivate();
        assertTrue(Stage.reset);
        assertFalse(pipeline.getContext().isActive());
        assertFalse(pipeline.getContext().isManaged());

        // now that external management is off,
        // we get the expected activations/events
        transact(false);
        assertEquals(SpeechContext.Event.ACTIVATE, this.events.get(0));
        assertTrue(pipeline.getContext().isActive());
        transact(false);
        assertEquals(SpeechContext.Event.DEACTIVATE, this.events.get(0));
        assertFalse(pipeline.getContext().isActive());
    }

    private void transact(boolean managed) throws Exception {
        this.events.clear();
        Input.send();
        if (!managed) {
            while (this.events.isEmpty()) {
                Thread.sleep(1);
            }
        }
    }

    public void onEvent(@NonNull SpeechContext.Event event,
                        @NonNull SpeechContext context) {
        this.events.add(event);
    }

    public static class Input implements SpeechInput {
        private static Semaphore semaphore;
        private static boolean stopped;
        public static int counter;

        public Input(SpeechConfig config) {
            semaphore = new Semaphore(0);
            stopped = false;
            counter = 0;
        }

        public void close() {
            counter = -1;
        }

        public void read(SpeechContext context, ByteBuffer frame)
              throws InterruptedException {
            if (!stopped) {
                semaphore.acquire();
                frame.putInt(0, ++counter);
            }
        }

        public static void send() {
            semaphore.release();
        }

        public static void stop() {
            stopped = true;
            semaphore.release();
        }
    }

    private static class ManagedInput extends Input {
        public ManagedInput(SpeechConfig config) {
            super(config);
        }

        @Override
        public void read(SpeechContext context, ByteBuffer frame)
              throws InterruptedException {
            if (!context.isManaged()) {
                super.read(context, frame);
            }
        }
    }

    public static class Stage implements SpeechProcessor {
        public static boolean open;
        public static boolean reset = false;

        public Stage(SpeechConfig config) {
            open = true;
        }

        public void reset() {
            reset = true;
            close();
        }

        public void close() {
            open = false;
        }

        public void process(SpeechContext context, ByteBuffer frame) {
            int counter = frame.getInt(0);
            boolean active = counter % 2 == 1;

            context.setActive(active);
            context.dispatch(
                active
                ? SpeechContext.Event.ACTIVATE
                : SpeechContext.Event.DEACTIVATE
            );

            Iterator<ByteBuffer> buffer =
                context.getBuffer().descendingIterator();
            while (buffer.hasNext() && counter >= 0)
                assertEquals(counter--, buffer.next().getInt(0));
        }
    }

    public static class FailInput implements SpeechInput {
        public FailInput(SpeechConfig config) {
        }

        public void close() throws Exception {
            throw new Exception("fail");
        }

        public void read(SpeechContext context, ByteBuffer frame)
              throws Exception {
            throw new AudioRecordError(-3);
        }
    }

    public static class FailStage implements SpeechProcessor {
        public FailStage(SpeechConfig config) {
        }

        public void reset() throws Exception {
            close();
        }

        public void close() throws Exception {
            throw new Exception("fail");
        }

        public void process(SpeechContext context, ByteBuffer frame)
                throws Exception {
            throw new Exception("fail");
        }
    }

    public static class ConfigRequiredStage implements SpeechProcessor {
        public ConfigRequiredStage(SpeechConfig config) {
            config.getString("required-property");
        }

        public void reset() throws Exception {
            close();
        }

        public void close() throws Exception {
            throw new Exception("fail");
        }

        public void process(SpeechContext context, ByteBuffer frame)
              throws Exception {
            throw new Exception("fail");
        }
    }

    private static class TestProfile implements PipelineProfile {

        public TestProfile() {}

        @Override
        public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
            return builder
                  .setInputClass(
                        "io.spokestack.spokestack.SpeechPipelineTest$Input")
                  .addStageClass(
                        "io.spokestack.spokestack.SpeechPipelineTest$ConfigRequiredStage");
        }
    }
}
