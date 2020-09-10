package io.spokestack.spokestack;

import androidx.annotation.NonNull;
import io.spokestack.spokestack.tensorflow.TensorflowModel;
import io.spokestack.spokestack.wakeword.WakewordTrigger;
import io.spokestack.spokestack.wakeword.WakewordTriggerTest;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Before;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ActivationTimeoutTest {
    private SpeechContext context;

    @Test
    public void testClose() throws Exception {
        // verify that close() throws no errors
        ActivationTimeoutTest.TestEnv env =
              new ActivationTimeoutTest.TestEnv(testConfig());
        env.process();
        env.close();
    }

    @Test
    public void testVadDeactivate() throws Exception {
        // verify deactivation on vad timeout after min activation length
        ActivationTimeoutTest.TestEnv env =
              new ActivationTimeoutTest.TestEnv(testConfig());

        env.context.setActive(true);
        assertEquals(SpeechContext.Event.ACTIVATE, env.event);
        // reset the event so we can verify nothing gets sent until the
        // minimum activation time has passed
        env.event = null;

        env.context.setSpeech(true);
        env.process();
        env.process();

        env.context.setSpeech(false);
        assertNull(env.event);

        env.process();
        assertEquals(SpeechContext.Event.DEACTIVATE, env.event);
        assertFalse(env.context.isActive());
    }

    @Test
    public void testManualTimeout() throws Exception {
        // verify manual activation max activation timeout
        ActivationTimeoutTest.TestEnv env =
              new ActivationTimeoutTest.TestEnv(testConfig());

        env.context.setActive(true);
        env.process();
        env.process();
        env.process();
        env.process();

        assertEquals(SpeechContext.Event.DEACTIVATE, env.event);
        assertFalse(env.context.isActive());
    }

    private SpeechConfig testConfig() {
        return new SpeechConfig()
              .put("sample-rate", 16000)
              .put("frame-width", 10)
              .put("wake-active-min", 20)
              .put("wake-active-max", 30);
    }

    public class TestEnv implements OnSpeechEventListener {
        public final ByteBuffer frame;
        public final SpeechContext context;
        public final ActivationTimeout timeout;
        public SpeechContext.Event event;

        public TestEnv(SpeechConfig config) {
            int sampleRate = config.getInteger("sample-rate");
            int frameWidth = config.getInteger("frame-width");

            // create the frame buffer and wakeword trigger
            this.frame = ByteBuffer.allocateDirect(frameWidth * sampleRate / 1000 * 2);

            // create the speech context for processing calls
            this.context = new SpeechContext(config);
            context.addOnSpeechEventListener(this);

            this.timeout = new ActivationTimeout(config);
        }

        public void process() throws Exception {
            this.timeout.process(this.context, this.frame);
        }

        public void close() {
            this.timeout.close();
        }

        public void onEvent(@NonNull SpeechContext.Event event, @NonNull SpeechContext context) {
            this.event = event;
        }
    }
}