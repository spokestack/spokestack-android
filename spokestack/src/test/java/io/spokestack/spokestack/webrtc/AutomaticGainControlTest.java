package io.spokestack.spokestack.webrtc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

import io.spokestack.spokestack.ComponentConfig;
import io.spokestack.spokestack.SpeechContext;

public class AutomaticGainControlTest {

    @Test
    public void testConstruction() {
        // default config
        final ComponentConfig config = new ComponentConfig();
        config.put("sample-rate", 8000);
        config.put("frame-width", 10);
        new AutomaticGainControl(config);

        // invalid sample rate
        config.put("sample-rate", 48000);
        config.put("frame-width", 20);
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() { new AutomaticGainControl(config); }
        });

        // invalid frame width
        config.put("sample-rate", 8000);
        config.put("frame-width", 25);
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() { new AutomaticGainControl(config); }
        });

        // invalid target level
        config.put("agc-target-level-dbfs", "invalid");
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() { new AutomaticGainControl(config); }
        });

        // invalid compression gain
        config.put("agc-compression-gain-db", "invalid");
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() { new AutomaticGainControl(config); }
        });

        // valid config
        config.put("sample-rate", 16000);
        config.put("frame-width", 20);
        config.put("agc-target-level-dbfs", 3);
        config.put("agc-compression-gain-db", 9);
        new AutomaticGainControl(config);

        // valid rates
        config.put("sample-rate", 8000);
        new AutomaticGainControl(config);
        config.put("sample-rate", 16000);
        new AutomaticGainControl(config);
        config.put("sample-rate", 32000);
        new AutomaticGainControl(config);

        // valid widths
        config.put("frame-width", 10);
        new AutomaticGainControl(config);
        config.put("frame-width", 20);
        new AutomaticGainControl(config);

        // close coverage
        new AutomaticGainControl(config).close();
    }

    @Test
    public void testProcessing() {
        final ComponentConfig config = new ComponentConfig()
            .put("sample-rate", 8000)
            .put("frame-width", 10)
            .put("agc-target-level-dbfs", 9)
            .put("agc-compression-gain-db", 2);

        final SpeechContext context = new SpeechContext(config);
        AutomaticGainControl agc;
        ByteBuffer frame;
        double level;

        // invalid frame
        assertThrows(IllegalStateException.class, new Executable() {
            public void execute() {
                new AutomaticGainControl(config)
                    .process(context, ByteBuffer.allocateDirect(1));
            }
        });

        // valid amplification
        agc = new AutomaticGainControl(config);
        frame = sinFrame(config, 0.08);
        level = rms(frame);
        agc.process(context, frame);
        assertTrue(rms(frame) > level);

        // valid attenuation
        agc = new AutomaticGainControl(config);
        frame = sinFrame(config, 1.0);
        level = rms(frame);
        agc.process(context, frame);
        assertTrue(rms(frame) < level);
    }

    @Test
    public void testTracing() {
        final ComponentConfig config = new ComponentConfig()
            .put("sample-rate", 8000)
            .put("frame-width", 10)
            .put("trace-level", SpeechContext.TraceLevel.PERF.value());
        final SpeechContext context = new SpeechContext(config);
        final AutomaticGainControl agc = new AutomaticGainControl(config);
        for (int i = 0; i < 100; i++)
            agc.process(context, sinFrame(config, 0.08));
        assertTrue(context.getMessage() != null);
    }

    private ByteBuffer sinFrame(ComponentConfig config, double amplitude) {
        ByteBuffer buffer = sampleBuffer(config);
        double rate = config.getInteger("sample-rate");
        double freq = 2000;
        for (int i = 0; i < buffer.capacity() / 2; i++) {
            double sample =
                amplitude
                * Math.sin((double)i / (rate / freq) * 2 * Math.PI);
            buffer.putShort((short)(sample * Short.MAX_VALUE));
        }
        buffer.rewind();
        return buffer;
    }

    private ByteBuffer sampleBuffer(ComponentConfig config) {
        int samples = config.getInteger("sample-rate")
            / 1000
            * config.getInteger("frame-width");
        return ByteBuffer
            .allocateDirect(samples * 2)
            .order(ByteOrder.nativeOrder());
    }

    private double rms(ByteBuffer signal) {
        double sum = 0;
        int count = 0;

        signal.rewind();
        while (signal.hasRemaining()) {
            double sample = (double) signal.getShort() / Short.MAX_VALUE;
            sum += sample * sample;
            count++;
        }

        return 20 * Math.log10(Math.max(Math.sqrt(sum / count), 1e-5) / 2e-5);
    }
}
