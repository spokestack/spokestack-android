package io.spokestack.spokestack.android;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.SpeechInput;

import java.nio.ByteBuffer;

/**
 * A variant of {@link MicrophoneInput} that releases its internal {@link
 * AudioRecord} when ASR is activated to avoid microphone conflicts.
 *
 *
 * <p>
 * This class uses the configured sample rate and always reads single-chanel
 * 16-bit PCM samples.
 * </p>
 */
public final class PreASRMicrophoneInput implements SpeechInput {
    private AudioRecord recorder;
    private int sampleRate;
    private int bufferSize;
    private boolean recording;

    /**
     * initializes a new microphone instance and opens the audio recorder.
     *
     * @param config speech pipeline configuration
     */
    public PreASRMicrophoneInput(SpeechConfig config) {
        this.sampleRate = config.getInteger("sample-rate");
        this.bufferSize = AudioRecord.getMinBufferSize(
              sampleRate,
              AudioFormat.CHANNEL_IN_MONO,
              AudioFormat.ENCODING_PCM_16BIT
        );
    }

    /**
     * @return the current {@code AudioRecord} instance. Used for testing.
     */
    AudioRecord getRecorder() {
        return this.recorder;
    }

    /**
     * Releases the resources associated with the microphone.
     */
    public void close() {
        if (this.recorder != null) {
            this.recorder.release();
            this.recorder = null;
        }
    }

    /**
     * Reads a frame from the microphone if the pipeline is inactive. When the
     * pipeline is activated, the microphone resource is released, and the
     * speech context is flagged as being externally managed. The ASR component
     * is expected to deactivate the pipeline to signal that Spokestack can
     * recapture the microphone.
     *
     * @param context the current speech context
     * @param frame   the frame buffer to fill
     *
     * @throws AudioRecordError if audio cannot be read
     */
    public void read(SpeechContext context, ByteBuffer frame)
          throws AudioRecordError {
        if (context.isActive()) {
            stopRecording(context);
        } else {
            if (!this.recording) {
                startRecording(context);
            }
            int read = this.recorder.read(frame, frame.capacity());
            if (read != frame.capacity()) {
                throw new AudioRecordError(read);
            }
        }
    }

    private void stopRecording(SpeechContext context) {
        if (this.recorder != null) {
            this.recorder.release();
            this.recorder = null;
        }
        this.recording = false;
    }

    private void startRecording(SpeechContext context) {
        this.recorder = new AudioRecord(
              AudioSource.VOICE_RECOGNITION,
              this.sampleRate,
              AudioFormat.CHANNEL_IN_MONO,
              AudioFormat.ENCODING_PCM_16BIT,
              this.bufferSize
        );
        this.recorder.startRecording();
        this.recording = true;
    }
}
