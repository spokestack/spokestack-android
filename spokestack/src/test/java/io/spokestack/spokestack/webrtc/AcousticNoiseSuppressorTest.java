package io.spokestack.spokestack.webrtc;

import java.util.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

import io.spokestack.spokestack.ComponentConfig;
import io.spokestack.spokestack.SpeechContext;

public class AcousticNoiseSuppressorTest {

    @Test
    public void testConstruction() {
        // default config
        final ComponentConfig config = new ComponentConfig();
        config.put("sample-rate", 8000);
        config.put("frame-width", 10);
        new AcousticNoiseSuppressor(config);

        // invalid sample rate
        config.put("sample-rate", 48000);
        config.put("frame-width", 20);
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() { new AcousticNoiseSuppressor(config); }
        });

        // invalid frame width
        config.put("sample-rate", 8000);
        config.put("frame-width", 25);
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() { new AcousticNoiseSuppressor(config); }
        });

        // invalid policy
        config.put("sample-rate", 8000);
        config.put("frame-width", 20);
        config.put("ans-policy", "invalid");
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() { new AcousticNoiseSuppressor(config); }
        });

        // valid config
        config.put("sample-rate", 16000);
        config.put("frame-width", 20);
        config.put("ans-policy", "medium");
        new AcousticNoiseSuppressor(config);

        // valid rates
        config.put("sample-rate", 8000);
        new AcousticNoiseSuppressor(config);
        config.put("sample-rate", 16000);
        new AcousticNoiseSuppressor(config);
        config.put("sample-rate", 32000);
        new AcousticNoiseSuppressor(config);

        // valid widths
        config.put("frame-width", 10);
        new AcousticNoiseSuppressor(config);
        config.put("frame-width", 20);
        new AcousticNoiseSuppressor(config);

        // valid policies
        config.put("ans-policy", "mild");
        new AcousticNoiseSuppressor(config);
        config.put("ans-policy", "medium");
        new AcousticNoiseSuppressor(config);
        config.put("ans-policy", "aggressive");
        new AcousticNoiseSuppressor(config);
        config.put("ans-policy", "very-aggressive");
        new AcousticNoiseSuppressor(config);

        // close coverage
        new AcousticNoiseSuppressor(config).close();
    }

    @Test
    public void testProcessing() {
        final ComponentConfig config = new ComponentConfig()
            .put("sample-rate", 8000)
            .put("frame-width", 20)
            .put("ans-policy", "medium");

        final SpeechContext context = new SpeechContext(config);
        AcousticNoiseSuppressor ans;
        ByteBuffer actual;
        ByteBuffer expect;

        // invalid frame
        assertThrows(IllegalStateException.class, new Executable() {
            public void execute() {
                new AcousticNoiseSuppressor(config)
                    .process(context, ByteBuffer.allocateDirect(1));
            }
        });

        // no-op suppression
        ans = new AcousticNoiseSuppressor(config);
        expect = sinFrame(config);
        actual = sinFrame(config);
        ans.process(context, sinFrame(config));                 // warmup
        ans.process(context, actual);
        assertEquals(rms(expect), rms(actual), 3);

        // valid suppression
        ans = new AcousticNoiseSuppressor(config);
        expect = sinFrame(config);
        actual = addNoise(sinFrame(config));
        ans.process(context, addNoise(sinFrame(config)));       // warmup
        ans.process(context, actual);
        assertEquals(rms(expect), rms(actual), 3);
    }

    private ByteBuffer sinFrame(ComponentConfig config) {
        ByteBuffer frame = sampleBuffer(config);
        double rate = config.getInteger("sample-rate");
        double freq = 100;
        for (int i = 0; i < frame.capacity() / 2; i++) {
            double sample = Math.sin(i / (rate / freq) * 2 * Math.PI);
            frame.putShort(i * 2, (short)(sample * Short.MAX_VALUE));
        }
        return frame;
    }

    private ByteBuffer addNoise(ByteBuffer frame) {
        Random rng = new Random(42);
        double snr = Math.pow(10, 10 / 20.0);
        for (int i = 0; i < frame.capacity() / 2; i++) {
            double sample = (double) frame.getShort(i * 2) / Short.MAX_VALUE;
            double noise = rng.nextGaussian() / snr;
            sample = Math.min(Math.max(sample + noise, -1.0), 1.0);
            frame.putShort(i * 2, (short)(sample * Short.MAX_VALUE));
        }
        return frame;
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
