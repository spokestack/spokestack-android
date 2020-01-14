package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

/**
 * A speech pipeline profile that uses voice activity detection to activate
 * Google Speech ASR.
 *
 * <p>
 * Google Speech requires extra configuration, which must be added to the
 * pipeline build process separately from this profile:
 * </p>
 *
 * <ul>
 *   <li>
 *      <b>google-credentials</b> (string): json-stringified google service
 *      account credentials, used to authenticate with the speech API
 *   </li>
 *   <li>
 *      <b>locale</b> (string): language code for speech recognition
 *   </li>
 * </ul>
 */
public class VADTriggerGoogleASR implements PipelineProfile {
    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        return builder
              .setInputClass(
                    "io.spokestack.spokestack.android.MicrophoneInput")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.AcousticNoiseSuppressor")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.AutomaticGainControl")
              .setProperty("agc-compression-gain-db", 15)
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.VoiceActivityDetector")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.VoiceActivityTrigger")
              .addStageClass("io.spokestack.spokestack.ActivationTimeout")
              .addStageClass(
                    "io.spokestack.spokestack.android.GoogleSpeechRecognizer");
    }
}
