package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

/**
 * A speech pipeline profile that uses the device microphone for input and
 * TensorFlow Lite models for wakeword detection.
 *
 * <p>
 * When using this profile, setting the paths to the profiles via {@link
 * SpeechPipeline.Builder#setProperty(String, Object)} and the {@code
 * wake-detect-path}, {@code wake-encode-path}, and {@code wake-fiter-path} keys
 * is still required.
 * </p>
 *
 * @see io.spokestack.spokestack.wakeword.WakewordTrigger
 */
public class TFLiteWakeword implements PipelineProfile {

    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        builder
              .setInputClass(
                    "io.spokestack.spokestack.android.MicrophoneInput")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.AcousticNoiseSuppressor")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.AutomaticGainControl")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.VoiceActivityDetector")
              .addStageClass(
                    "io.spokestack.spokestack.wakeword.WakewordTrigger")
              .setProperty("agc-compression-gain-db", 15)
              .setProperty("wake-active-min", 2000)
              .setProperty("pre-emphasis", 0.97);

        return builder;
    }
}
