package io.spokestack.spokestack;

import android.content.Context;
import android.os.SystemClock;
import io.spokestack.spokestack.dialogue.DialogueEvent;
import io.spokestack.spokestack.dialogue.DialogueManager;
import io.spokestack.spokestack.dialogue.FinalizedPrompt;
import io.spokestack.spokestack.dialogue.Prompt;
import io.spokestack.spokestack.dialogue.Proposal;
import io.spokestack.spokestack.nlu.NLUManager;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.nlu.tensorflow.NLUTestUtils;
import io.spokestack.spokestack.tts.SynthesisRequest;
import io.spokestack.spokestack.tts.TTSEvent;
import io.spokestack.spokestack.tts.TTSManager;
import io.spokestack.spokestack.tts.TTSTestUtils;
import io.spokestack.spokestack.util.EventTracer;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static io.spokestack.spokestack.SpeechTestUtils.FreeInput;

@RunWith(PowerMockRunner.class)
@PrepareForTest({SystemClock.class})
public class SpokestackTest {

    @After
    public void after() {
        // shut down any stray speech pipeline background threads
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().contains("Spokestack")) {
                thread.interrupt();
            }
        }
    }

    @Test
    public void testBuild() throws Exception {
        // missing all required config
        assertThrows(IllegalArgumentException.class,
              () -> new Spokestack.Builder().build());

        Spokestack.Builder builder = new Spokestack.Builder();

        // missing context
        assertThrows(IllegalArgumentException.class, builder::build);

        // TTS playback disabled
        // avoid creating a real websocket by also faking the service class
        Spokestack.Builder noOutputBuilder = new Spokestack.Builder()
              .withoutSpeechPipeline()
              .withoutNlu()
              .withoutDialogueManagement()
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
              .withoutDialogueManagement()
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
              .withoutDialogueManagement()
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
    public void testPipelineProfile() {
        // this only tests that the convenience method properly dispatches to
        // the pipeline version, failing with an invalid profile;
        // the valid profiles are tested by the speech pipeline test
        assertThrows(IllegalArgumentException.class, () ->
              new Spokestack.Builder()
                    .withPipelineProfile("io.spokestack.InvalidProfile")
        );
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

        builder = mockAndroidComponents(builder);
        Spokestack spokestack = new Spokestack(builder, mockNlu());

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
        spokestack.stopPlayback();
        outputEvent = events.poll(1, TimeUnit.SECONDS);
        assertEquals("stop", outputEvent);

        // close with a valid speech pipeline and TTS
        assertDoesNotThrow(spokestack::close);
    }

    @Test
    public void testDialogue() throws Exception {
        // just test convenience methods for now
        TestAdapter listener = new TestAdapter();

        SpeechPipeline.Builder pipelineBuilder = new SpeechPipeline.Builder();

        Spokestack.Builder builder = new Spokestack
              .Builder(pipelineBuilder, mockTts())
              .withoutWakeword()
              .addListener(listener);

        // explicitly include dialogue management
        builder.getDialogueBuilder()
              .withPolicyFile("src/test/resources/dialogue.json");

        builder = mockAndroidComponents(builder);
        Spokestack spokestack = new Spokestack(builder, mockNlu());

        listener.setSpokestack(spokestack);
        DialogueManager dialogueManager = spokestack.getDialogueManager();

        spokestack.putConversationData("key", "value");
        Object storedValue = dialogueManager.getDataStore().get("key");
        assertEquals("value", String.valueOf(storedValue));

        Prompt prompt = new Prompt.Builder("id", "{{key}}")
              .withVoice("{{voice}}")
              .withProposal(new Proposal())
              .endsConversation()
              .build();

        spokestack.putConversationData("voice", "one two three");

        FinalizedPrompt finalized = spokestack.finalizePrompt(prompt);

        assertNotNull(finalized);
        assertEquals("value", finalized.getText());
        assertEquals("one two three", finalized.getVoice());
        assertTrue(finalized.endsConversation());
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

    @Test
    public void testRestart() throws Exception {
        mockStatic(SystemClock.class);
        TestAdapter listener = new TestAdapter();

        Spokestack.Builder builder = new Spokestack
              .Builder(new SpeechPipeline.Builder(), mockTts())
              .withoutWakeword()
              .setConfig(testConfig())
              .setProperty("trace-level", EventTracer.Level.INFO.value())
              .addListener(listener);

        builder = mockAndroidComponents(builder);
        builder.getPipelineBuilder().setStageClasses(new ArrayList<>());
        Spokestack spokestack = new Spokestack(builder, mockNlu());

        spokestack.getSpeechPipeline().activate();
        SpeechContext.Event event =
              listener.speechEvents.poll(1, TimeUnit.SECONDS);
        assertEquals(SpeechContext.Event.ACTIVATE, event);

        // modules don't work after stop()
        spokestack.stop();
        assertNull(spokestack.getTts().getTtsService());
        assertNull(spokestack.getNlu().getNlu());
        assertThrows(
              IllegalStateException.class,
              () -> spokestack.classify("test"));
        SynthesisRequest request =
              new SynthesisRequest.Builder("test").build();
        assertThrows(
              IllegalStateException.class,
              () -> spokestack.synthesize(request));

        // restart supported
        spokestack.start();
        assertNotNull(spokestack.getTts().getTtsService());
        assertNotNull(spokestack.getNlu().getNlu());
        assertDoesNotThrow(() -> spokestack.classify("test"));
        assertDoesNotThrow(() -> spokestack.synthesize(request));
    }

    @Test
    public void testPause() throws Exception {
        mockStatic(SystemClock.class);
        TestAdapter listener = new TestAdapter();

        Spokestack.Builder builder = new Spokestack
              .Builder(new SpeechPipeline.Builder(), mockTts())
              .withoutWakeword()
              .setConfig(testConfig())
              .addListener(listener);

        builder = mockAndroidComponents(builder);
        builder.getPipelineBuilder()
              .setInputClass("io.spokestack.spokestack.SpeechTestUtils$FreeInput");
        Spokestack spokestack = new Spokestack(builder, mockNlu());

        // startup
        int frames = FreeInput.counter;
        assertEquals(frames, 0);
        spokestack.start();
        Thread.sleep(10);
        assertTrue(FreeInput.counter > frames);

        // we won't get any more frames if we're paused
        spokestack.pause();

        // wait for the pause to take effect
        Thread.sleep(10);
        frames = FreeInput.counter;

        // wait some more to make sure we don't get any more frames
        Thread.sleep(15);
        assertEquals(FreeInput.counter, frames);

        // after resuming, frames should start increasing almost immediately
        spokestack.resume();
        Thread.sleep(5);
        assertTrue(FreeInput.counter > frames);
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

        return builder
              .withAndroidContext(context);
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
        LinkedBlockingQueue<DialogueEvent> dialogueEvents =
              new LinkedBlockingQueue<>();
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
            this.dialogueEvents.clear();
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
        public void onDialogueEvent(@NotNull DialogueEvent event) {
            this.dialogueEvents.add(event);
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
