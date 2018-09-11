package com.pylon.spokestack.android;

import java.nio.ByteBuffer;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;

import com.pylon.spokestack.SpeechInput;
import com.pylon.spokestack.SpeechConfig;

/**
 * android microphone speech input.
 *
 * <p>
 * This class wraps the android {@link AudioRecord} class for reading audio
 * samples from the microphone. It is assumed that the application has been
 * manifested for microphone input (android.permission.RECORD_AUDIO),
 * and that it has asked the user for microphone permissions via
 * android.support.v4.app.ActivityCompat.requestPermissions.
 * </p>
 *
 * <p>
 * This class uses the configured sample rate and always reads single-chanel
 * 16-bit PCM samples.
 * </p>
 */
public final class MicrophoneInput implements SpeechInput {
    private final AudioRecord recorder;

    /**
     * initializes a new microphone instance and opens the audio recorder.
     * @param config speech pipeline configuration
     */
    public MicrophoneInput(SpeechConfig config) {
        int sampleRate = config.getInteger("sample-rate");
        int bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        );
        this.recorder = new AudioRecord(
            AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        );
        this.recorder.startRecording();
    }

    /**
     * initializes a new microphone instance with an existing audio recorder;
     * useful for testing/mocking.
     * @param audioRecord android audio recorder instance to attach
     */
    public MicrophoneInput(AudioRecord audioRecord) {
        this.recorder = audioRecord;
        this.recorder.startRecording();
    }

    /**
     * releases the resources associated with the microphone.
     */
    public void close() {
        this.recorder.release();
    }

    /**
     * reads a frame from the microphone.
     * @param frame the frame buffer to fill
     */
    public void read(ByteBuffer frame) {
        int read = this.recorder.read(frame, frame.capacity());
        if (read != frame.capacity())
            throw new IllegalStateException();
    }
}
