package io.spokestack.spokestack;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.nio.ByteBuffer;

import org.junit.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

import io.spokestack.spokestack.OnSpeechEventListener;
import io.spokestack.spokestack.SpeechInput;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechProcessor;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.SpeechPipeline;

public class SpeechPipelineTest implements OnSpeechEventListener {
    private SpeechContext.Event event;

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
    public void testStartStop() throws Exception {
        final SpeechPipeline pipeline = new SpeechPipeline.Builder()
            .setInputClass("io.spokestack.spokestack.SpeechPipelineTest$Input")
            .addStageClass("io.spokestack.spokestack.SpeechPipelineTest$Stage")
            .setProperty("sample-rate", 16000)
            .setProperty("frame-width", 20)
            .setProperty("buffer-width", 300)
            .addOnSpeechEventListener(this)
            .build();

        // startup
        pipeline.start();
        assertTrue(pipeline.isRunning());
        assertEquals(15, pipeline.getContext().getBuffer().size());
        assertEquals(0, Input.counter);
        assertTrue(Stage.open);

        // invalid restart
        assertThrows(IllegalStateException.class, new Executable() {
            public void execute() throws Exception { pipeline.start(); }
        });

        // first frame
        transact();
        assertEquals(SpeechContext.Event.ACTIVATE, this.event);
        assertTrue(pipeline.getContext().isActive());

        // next frame
        transact();
        assertEquals(SpeechContext.Event.DEACTIVATE, this.event);
        assertFalse(pipeline.getContext().isActive());

        // shutdown
        Input.stop();
        pipeline.close();
        assertFalse(pipeline.isRunning());
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
        pipeline.stop();
        assertEquals(SpeechContext.Event.ERROR, this.event);
    }

    @Test
    public void testStageFailure() throws Exception {
        SpeechPipeline pipeline = new SpeechPipeline.Builder()
            .setInputClass("io.spokestack.spokestack.SpeechPipelineTest$Input")
            .addStageClass("io.spokestack.spokestack.SpeechPipelineTest$FailStage")
            .addOnSpeechEventListener(this)
            .build();
        pipeline.start();

        transact();
        assertEquals(SpeechContext.Event.ERROR, this.event);
        this.event = null;

        Input.stop();
        pipeline.stop();
        assertEquals(SpeechContext.Event.ERROR, this.event);
    }

    private void transact() throws Exception {
        this.event = null;
        Input.send();
        while (this.event == null)
            Thread.sleep(0);
    }

    public void onEvent(SpeechContext.Event event, SpeechContext context) {
        this.event = event;
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

        public void read(ByteBuffer frame) throws InterruptedException {
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

    public static class Stage implements SpeechProcessor {
        public static boolean open;

        public Stage(SpeechConfig config) {
            open = true;
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

        public void read(ByteBuffer frame) throws Exception {
            throw new Exception("fail");
        }
    }

    public static class FailStage implements SpeechProcessor {
        public FailStage(SpeechConfig config) {
        }

        public void close() throws Exception {
            throw new Exception("fail");
        }

        public void process(SpeechContext context, ByteBuffer frame)
                throws Exception {
            throw new Exception("fail");
        }
    }
}
