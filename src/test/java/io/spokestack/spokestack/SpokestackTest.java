package io.spokestack.spokestack;

import android.content.Context;
import android.os.SystemClock;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import io.spokestack.spokestack.nlu.NLUContext;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.nlu.tensorflow.NLUTestUtils;
import io.spokestack.spokestack.nlu.tensorflow.TensorflowNLU;
import io.spokestack.spokestack.tts.SynthesisRequest;
import io.spokestack.spokestack.tts.TTSEvent;
import io.spokestack.spokestack.tts.TTSManager;
import io.spokestack.spokestack.tts.TTSTestUtils;
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

        // no subsystems exist to handle these calls
        assertThrows(NullPointerException.class, spokestack::start);
        assertThrows(NullPointerException.class,
              () -> spokestack.classify("test"));
        assertThrows(NullPointerException.class, spokestack::stopPlayback);

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

        Spokestack spokestack = mockedNluBuilder()
              .addListener(listener)
              .build();

        listener.setSpokestack(spokestack);

        NLUResult result = spokestack.classify("test").get();
        NLUResult lastResult = listener.nluResults.poll(1, TimeUnit.SECONDS);
        assertNotNull(lastResult);
        assertEquals(result.getIntent(), lastResult.getIntent());

        NLUContext fakeContext = new NLUContext(testConfig());
        result = spokestack.getNlu().classify("test", fakeContext).get();
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

        Spokestack spokestack = mockedNluBuilder()
              .withoutAutoClassification()
              .addListener(listener)
              .build();

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

        Spokestack spokestack = mockedNluBuilder()
              .addListener(listener)
              .withTranscriptEditor(String::toUpperCase)
              .build();

        // transcripts can be edited before automatic classification
        String transcript = "test";
        assertTrue(listener.nluResults.isEmpty());
        SpeechContext context = spokestack.getSpeechPipeline().getContext();
        context.setTranscript(transcript);
        context.dispatch(SpeechContext.Event.RECOGNIZE);
        NLUResult result = spokestack.classify("test").get();
        NLUResult lastResult = listener.nluResults.poll(1, TimeUnit.SECONDS);
        assertNotNull(lastResult);
        assertEquals(result.getIntent(), lastResult.getIntent());
        assertEquals(transcript.toUpperCase(), lastResult.getUtterance());

        // ...but not before classification via convenience method
        result = spokestack.classify(transcript).get();
        assertEquals(transcript, result.getUtterance());
    }

    private Spokestack.Builder mockedNluBuilder() throws Exception {
        mockStatic(SystemClock.class);

        NLUTestUtils.TestEnv nluEnv = new NLUTestUtils.TestEnv();
        TensorflowNLU.Builder nluBuilder = nluEnv.nluBuilder;

        return new Spokestack.Builder(
              new SpeechPipeline.Builder(),
              nluBuilder,
              new TTSManager.Builder()
        )
              .withoutWakeword()
              .withoutTts();
    }

    @Test
    public void testTts() throws Exception {
        mockStatic(SystemClock.class);
        TestAdapter listener = new TestAdapter();

        // inject fake builders into wrapper's builder
        // speech pipeline
        SpeechPipeline.Builder pipelineBuilder = new SpeechPipeline.Builder();

        // NLU
        NLUTestUtils.TestEnv nluEnv = new NLUTestUtils.TestEnv();
        TensorflowNLU.Builder nluBuilder = nluEnv.nluBuilder;

        // TTS
        Context context = mock(Context.class);
        LifecycleRegistry lifecycleRegistry =
              new LifecycleRegistry(mock(LifecycleOwner.class));

        TTSManager.Builder ttsBuilder = new TTSManager.Builder()
              .setTTSServiceClass(
                    "io.spokestack.spokestack.tts.TTSTestUtils$Service")
              .setOutputClass(
                    "io.spokestack.spokestack.tts.TTSTestUtils$Output")
              .setProperty("spokestack-id", "default");

        Spokestack spokestack = new Spokestack
              .Builder(pipelineBuilder, nluBuilder, ttsBuilder)
              .withoutWakeword()
              .withAndroidContext(context)
              .withLifecycle(lifecycleRegistry)
              .addListener(listener)
              .build();

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