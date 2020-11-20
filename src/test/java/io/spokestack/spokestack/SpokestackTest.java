package io.spokestack.spokestack;

import android.content.Context;
import android.os.SystemClock;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import io.spokestack.spokestack.nlu.NLUManager;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.nlu.tensorflow.NLUTestUtils;
import io.spokestack.spokestack.tts.SynthesisRequest;
import io.spokestack.spokestack.tts.TTSEvent;
import io.spokestack.spokestack.tts.TTSManager;
import io.spokestack.spokestack.tts.TTSTestUtils;
import io.spokestack.spokestack.util.EventTracer;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest({SystemClock.class})
public class SpokestackTest {

    @Test
    public void testBuild() throws Exception {
        // missing all required config
        assertThrows(IllegalArgumentException.class,
              () -> new Spokestack.Builder().build());

        Spokestack.Builder builder = new Spokestack.Builder();

        // missing lifecycle
        assertThrows(IllegalArgumentException.class,
              () -> {
                  builder
                        .withAndroidContext(mock(Context.class))
                        .build();
              });

        // TTS playback disabled
        // avoid creating a real websocket by also faking the service class
        Spokestack.Builder noOutputBuilder = new Spokestack.Builder()
              .withoutSpeechPipeline()
              .withoutNlu()
              .withoutAutoPlayback();
        noOutputBuilder.getTtsBuilder().setTTSServiceClass(
              "io.spokestack.spokestack.tts.TTSTestUtils$Service");

        Spokestack noOutput = noOutputBuilder.build();
        assertNull(noOutput.getTts().getOutput());

        // everything disabled
        // note that it's unnecessary to call both disableWakeword() and
        // disableAsr() or disableTts() and disableTtsPlayback() for normal
        // usage
        Spokestack spokestack = builder
              .withoutWakeword()
              .withoutSpeechPipeline()
              .withoutNlu()
              .withoutTts()
              .withoutAutoPlayback()
              .setProperty("test", "test")
              .build();

        // closing with no active subsystems is fine
        assertDoesNotThrow(spokestack::close);
    }

    @Test
    public void testSpeechPipeline() throws Exception {
        mockStatic(SystemClock.class);
        TestAdapter listener = new TestAdapter();

        Spokestack.Builder builder = new Spokestack.Builder()
              .setConfig(testConfig())
              .withoutWakeword()
              .withoutNlu()
              .withoutTts()
              .addListener(listener);

        builder.getPipelineBuilder().setStageClasses(new ArrayList<>());
        Spokestack spokestack = builder.build();

        listener.setSpokestack(spokestack);

        // activations work through both the retrieved pipeline and the wrapper
        spokestack.getSpeechPipeline().activate();
        SpeechContext.Event event =
              listener.speechEvents.poll(1, TimeUnit.SECONDS);
        assertEquals(SpeechContext.Event.ACTIVATE, event);

        spokestack.deactivate();
        event = listener.speechEvents.poll(1, TimeUnit.SECONDS);
        assertEquals(SpeechContext.Event.DEACTIVATE, event);

        // other convenience methods
        spokestack.activate();
        event = listener.speechEvents.poll(1, TimeUnit.SECONDS);
        assertEquals(SpeechContext.Event.ACTIVATE, event);
        spokestack.start();
        assertTrue(spokestack.getSpeechPipeline().isRunning());
        spokestack.stop();
        assertFalse(spokestack.getSpeechPipeline().isRunning());
    }

    @Test
    public void testNlu() throws Exception {
        TestAdapter listener = new TestAdapter();

        Spokestack.Builder builder = new Spokestack
              .Builder(new SpeechPipeline.Builder(), mockTts())
              .addListener(listener)
              .withoutWakeword()
              .withoutTts();

        Spokestack spokestack = new Spokestack(builder, mockNlu());
        listener.setSpokestack(spokestack);

        NLUResult result = spokestack.classify("test").get();
        NLUResult lastResult = listener.nluResults.poll(1, TimeUnit.SECONDS);
        assertNotNull(lastResult);
        assertEquals(result.getIntent(), lastResult.getIntent());

        result = spokestack.getNlu().classify("test").get();
        assertEquals(result.getIntent(), lastResult.getIntent());

        // classification is called automatically on ASR results
        assertTrue(listener.nluResults.isEmpty());
        SpeechContext context = spokestack.getSpeechPipeline().getContext();
        context.setTranscript("test");
        context.dispatch(SpeechContext.Event.RECOGNIZE);
        lastResult = listener.nluResults.poll(1, TimeUnit.SECONDS);
        assertNotNull(lastResult);
        assertEquals(result.getIntent(), lastResult.getIntent());
    }

    @Test
    public void testAutoClassification() throws Exception {
        TestAdapter listener = new TestAdapter();

        Spokestack.Builder builder = new Spokestack
              .Builder(new SpeechPipeline.Builder(), mockTts())
              .withoutWakeword()
              .withoutAutoClassification()
              .addListener(listener);

        builder = mockAndroidComponents(builder);
        Spokestack spokestack = new Spokestack(builder, mockNlu());

        // automatic classification can be disabled
        listener.clear();
        assertTrue(listener.nluResults.isEmpty());
        SpeechContext context = spokestack.getSpeechPipeline().getContext();
        context.dispatch(SpeechContext.Event.RECOGNIZE);
        NLUResult lastResult =
              listener.nluResults.poll(500, TimeUnit.MILLISECONDS);
        assertNull(lastResult);
    }

    @Test
    public void testTranscriptEditing() throws Exception {
        TestAdapter listener = new TestAdapter();

        Spokestack.Builder builder = new Spokestack
              .Builder(new SpeechPipeline.Builder(), mockTts())
              .withoutWakeword()
              .addListener(listener)
              .withTranscriptEditor(String::toUpperCase);

        builder = mockAndroidComponents(builder);
        Spokestack spokestack = new Spokestack(builder, mockNlu());

        // transcripts can be edited before automatic classification
        String transcript = "test";
        assertTrue(listener.nluResults.isEmpty());
        SpeechContext context = spokestack.getSpeechPipeline().getContext();
        context.setTranscript(transcript);
        context.dispatch(SpeechContext.Event.RECOGNIZE);
        NLUResult result = spokestack.classify("TEST").get();
        NLUResult lastResult = listener.nluResults.poll(1, TimeUnit.SECONDS);
        assertNotNull(lastResult);
        assertEquals(result.getIntent(), lastResult.getIntent());
        assertEquals(transcript.toUpperCase(), lastResult.getUtterance());

        // ...but not before classification via convenience method
        result = spokestack.classify(transcript).get();
        assertEquals(transcript, result.getUtterance());
    }

    @Test
    public void testTts() throws Exception {
        TestAdapter listener = new TestAdapter();

        // inject fake builders into wrapper's builder
        // speech pipeline
        SpeechPipeline.Builder pipelineBuilder = new SpeechPipeline.Builder();

        Spokestack.Builder builder = new Spokestack
              .Builder(pipelineBuilder, mockTts())
              .withoutWakeword()
              .addListener(listener);

        Spokestack spokestack = new Spokestack(builder, mockNlu());

        // handle context/lifecycle separately to make sure the convenience
        // methods work
        Context androidContext = mock(Context.class);
        LifecycleRegistry lifecycleRegistry =
              new LifecycleRegistry(mock(LifecycleOwner.class));

        spokestack.setAndroidContext(androidContext);
        spokestack.setAndroidLifecycle(lifecycleRegistry);

        listener.setSpokestack(spokestack);
        TTSManager tts = spokestack.getTts();

        LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();
        ((TTSTestUtils.Output) tts.getOutput()).setEventQueue(events);

        spokestack.classify("test").get();
        NLUResult lastResult = listener.nluResults.poll(1, TimeUnit.SECONDS);
        assertNotNull(lastResult);

        TTSEvent event = listener.ttsEvents.poll(1, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(TTSEvent.Type.AUDIO_AVAILABLE, event.type);
        String outputEvent = events.poll(1, TimeUnit.SECONDS);
        assertEquals("audioReceived", outputEvent);

        // other convenience methods
        spokestack.releaseTts();
        assertNull(tts.getTtsService());
        spokestack.prepareTts();
        assertNotNull(tts.getTtsService());
        spokestack.stopPlayback();
        outputEvent = events.poll(1, TimeUnit.SECONDS);
        assertEquals("stop", outputEvent);

        // close with a valid speech pipeline and TTS
        assertDoesNotThrow(spokestack::close);
    }

    @Test
    public void testListenerManagement() throws Exception {
        mockStatic(SystemClock.class);
        TestAdapter listener = new TestAdapter();
        TestAdapter listener2 = new TestAdapter();

        Spokestack.Builder builder = new Spokestack
              .Builder(new SpeechPipeline.Builder(), mockTts())
              .withoutWakeword()
              .setConfig(testConfig())
              .setProperty("trace-level", EventTracer.Level.INFO.value())
              .addListener(listener);

        builder = mockAndroidComponents(builder);
        builder.getPipelineBuilder().setStageClasses(new ArrayList<>());
        Spokestack spokestack = new Spokestack(builder, mockNlu());
        spokestack.addListener(listener2);

        spokestack.getSpeechPipeline().activate();
        SpeechContext.Event event =
              listener.speechEvents.poll(1, TimeUnit.SECONDS);
        assertEquals(SpeechContext.Event.ACTIVATE, event);
        event = listener2.speechEvents.poll(1, TimeUnit.SECONDS);
        assertEquals(SpeechContext.Event.ACTIVATE, event);

        spokestack.removeListener(listener2);

        SpeechContext context = spokestack.getSpeechPipeline().getContext();
        String message = "trace";
        context.traceInfo(message);
        event = listener.speechEvents.poll(500, TimeUnit.MILLISECONDS);
        assertEquals(SpeechContext.Event.TRACE, event);
        // second listener should no longer receive events
        event = listener2.speechEvents.poll(500, TimeUnit.MILLISECONDS);
        assertNull(event);
        // trace message also sent through the convenience tracing method
        String trace = listener.traces.poll(500, TimeUnit.MILLISECONDS);
        assertEquals(message, trace);

        Throwable error = new NullPointerException();
        context.setError(error);
        context.dispatch(SpeechContext.Event.ERROR);
        event = listener.speechEvents.poll(500, TimeUnit.MILLISECONDS);
        assertEquals(SpeechContext.Event.ERROR, event);
        // error also sent through the convenience error handling method
        Throwable err = listener.errors.poll(500, TimeUnit.MILLISECONDS);
        assertEquals(error, err);
    }

    private NLUManager mockNlu() throws Exception {
        return NLUTestUtils.mockManager();
    }

    private TTSManager.Builder mockTts() {
        return new TTSManager.Builder()
              .setTTSServiceClass(
                    "io.spokestack.spokestack.tts.TTSTestUtils$Service")
              .setOutputClass(
                    "io.spokestack.spokestack.tts.TTSTestUtils$Output")
              .setProperty("spokestack-id", "default");
    }

    private Spokestack.Builder mockAndroidComponents(
          Spokestack.Builder builder) {
        Context context = mock(Context.class);
        LifecycleRegistry lifecycleRegistry =
              new LifecycleRegistry(mock(LifecycleOwner.class));

        return builder
              .withAndroidContext(context)
              .withLifecycle(lifecycleRegistry);
    }

    private SpeechConfig testConfig() {
        SpeechConfig config = NLUTestUtils.testConfig();
        config.put("sample-rate", 16000);
        config.put("frame-width", 20);
        config.put("buffer-width", 300);
        return config;
    }

    static class TestAdapter extends SpokestackAdapter {
        SpeechContext speechContext;
        Spokestack spokestack;
        LinkedBlockingQueue<NLUResult> nluResults = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<TTSEvent> ttsEvents = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<SpeechContext.Event> speechEvents =
              new LinkedBlockingQueue<>();
        LinkedBlockingQueue<String> traces = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();

        public void setSpokestack(Spokestack spokestack) {
            this.spokestack = spokestack;
            clear();
        }

        public void clear() {
            this.speechEvents.clear();
            this.nluResults.clear();
            this.ttsEvents.clear();
            this.traces.clear();
            this.errors.clear();
        }

        @Override
        public void speechEvent(@NotNull SpeechContext.Event event,
                                @NotNull SpeechContext context) {
            this.speechEvents.add(event);
            this.speechContext = context;
        }

        @Override
        public void ttsEvent(@NotNull TTSEvent event) {
            this.ttsEvents.add(event);
        }

        @Override
        public void nluResult(@NotNull NLUResult result) {
            this.nluResults.add(result);
            if (this.spokestack != null) {
                SynthesisRequest request =
                      new SynthesisRequest.Builder(result.getIntent()).build();
                this.spokestack.synthesize(request);
            }
        }

        @Override
        public void trace(@NotNull SpokestackModule module,
                          @NotNull String message) {
            this.traces.add(message);
        }

        @Override
        public void error(@NotNull SpokestackModule module,
                          @NotNull Throwable error) {
            this.errors.add(error);
        }
    }
}