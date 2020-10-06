package io.spokestack.spokestack;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * speech sampling logger.
 *
 * <p>
 * This is the spokestack pipeline component for logging speech samples. The
 * samples are written in the wav format to the configured output directory
 * with rotating file names. The sampler is useful for debugging pipeline
 * configuration, microphone levels, etc. The sampler only logs audio samples
 * that correspond to speech (where context.isSpeech() is true).
 * </p>
 *
 * <p>
 * This pipeline component supports the following configuration properties:
 * </p>
 * <ul>
 *   <li>
 *      <b>sample-log-path</b> (string): path to the directory to write logs
 *   </li>
 *   <li>
 *      <b>sample-log-max-files</b> (int): maximum number of rotated files
 *      to create (default: 10)
 *   </li>
 * </ul>
 *
 */
public final class SpeechSampler implements SpeechProcessor {
    /** default maximum number of rotated sample files. */
    public static final int DEFAULT_SAMPLE_MAX = 10;

    private final String logPath;
    private final int sampleMax;
    private int sampleId;
    private FileOutputStream stream;
    private final ByteBuffer header;

    /**
     * constructs a new sampler instance.
     * @param config the pipeline configuration instance
     * @throws Exception on error
     */
    public SpeechSampler(SpeechConfig config) throws Exception {
        this.logPath = config.getString("sample-log-path");
        this.sampleMax = config.getInteger(
            "sample-log-max-files",
            DEFAULT_SAMPLE_MAX);

        // create the log path if it doesn't exist
        new File(logPath).mkdirs();

        // create the wav file header
        int sampleRate = config.getInteger("sample-rate");
        this.header = ByteBuffer
            .allocate(44)
            .order(ByteOrder.LITTLE_ENDIAN);
        // riff/wave header
        this.header.put("RIFF".getBytes("ASCII"));
        this.header.putInt(Integer.MAX_VALUE);
        this.header.put("WAVE".getBytes("ASCII"));
        // format chunk
        this.header.put("fmt ".getBytes("ASCII"));
        this.header.putInt(16);                     // size of format chunk
        this.header.putShort((short) 1);            // pcm
        this.header.putShort((short) 1);            // channels
        this.header.putInt(sampleRate);             // sample rate
        this.header.putInt(sampleRate * 2);         // byte rate
        this.header.putShort((short) 2);            // block align
        this.header.putShort((short) 16);           // bits per sample
        // data chunk
        this.header.put("data".getBytes("ASCII"));
        this.header.putInt(Integer.MAX_VALUE);      // size of data chunk
    }

    @Override
    public void reset() throws Exception {
        close();
    }

    /**
     * destroys the resources attached to the copmonent.
     * @throws Exception on error
     */
    public void close() throws Exception {
        if (this.stream != null)
            this.stream.close();
    }

    /**
     * processes a frame of audio.
     * @param context the current speech context
     * @param frame   the audio frame to detect
     * @throws Exception on error
     */
    public void process(SpeechContext context, ByteBuffer frame)
            throws Exception {
        if (context.isSpeech() && this.stream == null) {
            // speech rising edge, create and attach the log file
            File file = new File(
                this.logPath,
                String.format("%05d.wav", this.sampleId++ % this.sampleMax));
            this.stream = new FileOutputStream(file);
            this.stream.write(this.header.array());
        } else if (!context.isSpeech() && this.stream != null) {
            // speech falling edge, flush changes and close
            this.stream.close();
            this.stream = null;
        }
        // write the current audio frame to the wav file
        if (this.stream != null) {
            byte[] data = new byte[frame.remaining()];
            frame.get(data);
            this.stream.write(data);
        }
    }
}
