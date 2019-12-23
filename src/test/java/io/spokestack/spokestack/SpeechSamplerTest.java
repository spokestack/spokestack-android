import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.jupiter.api.Assertions.*;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.SpeechSampler;

public class SpeechSamplerTest {
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Test
    public void testConstruction() throws Exception {
        // default config
        final SpeechConfig config = new SpeechConfig()
            .put("sample-rate", 8000)
            .put("sample-log-path", testFolder.getRoot().toString());
        new SpeechSampler(config);

        // valid config
        config.put("sample-log-max-files", 20);
        new SpeechSampler(config);

        // close coverage
        new SpeechSampler(config).close();
    }

    @Test
    public void testProcessing() throws Exception {
        final SpeechConfig config = new SpeechConfig()
            .put("sample-rate", 8000)
            .put("frame-width", 10)
            .put("sample-log-path", testFolder.getRoot())
            .put("sample-log-max-files", 5);
        final SpeechContext context = new SpeechContext(config);
        final SpeechSampler sampler = new SpeechSampler(config);

        // non-speech samples
        sampler.process(context, sampleBuffer(config));
        assertEquals(0, testFolder.getRoot().listFiles().length);

        // speech samples
        for (int i = 0; i < 2 * config.getInteger("sample-log-max-files"); i++) {
            context.setSpeech(true);
            sampler.process(context, sampleBuffer(config));
            sampler.process(context, sampleBuffer(config));
            context.setSpeech(false);
            sampler.process(context, sampleBuffer(config));
            sampler.process(context, sampleBuffer(config));
        }
        assertEquals(
            config.getInteger("sample-log-max-files"),
            testFolder.getRoot().listFiles().length);

        // close with speech
        context.setSpeech(true);
        sampler.process(context, sampleBuffer(config));
        sampler.close();
    }

    private ByteBuffer sampleBuffer(SpeechConfig config) {
        int samples = config.getInteger("sample-rate")
            / 1000
            * config.getInteger("frame-width");
        return ByteBuffer
            .allocateDirect(samples * 2)
            .order(ByteOrder.nativeOrder());
    }
}
