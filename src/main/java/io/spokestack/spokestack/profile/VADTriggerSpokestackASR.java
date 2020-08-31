package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

/**
 * A speech pipeline profile that uses voice activity detection to activate
 * Spokestack's cloud-based ASR.
 *
 * <p>
 * Spokestack's cloud-based ASR requires extra configuration, which must be
 * added to the pipeline build process separately from this profile:
 * </p>
 *
 * <ul>
 *   <li>
 *       <b>spokestack-id</b> (string, required): The client ID used for
 *       synthesis requests.
 *   </li>
 *   <li>
 *       <b>spokestack-secret</b> (string, required): The client secret used
 *       to sign synthesis requests.
 *   </li>
 *   <li>
 *      <b>language</b> (string): language code for speech recognition
 *   </li>
 * </ul>
 *
 * @see io.spokestack.spokestack.asr.SpokestackCloudRecognizer
 */
public class VADTriggerSpokestackASR implements PipelineProfile {
    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        return builder
              .setInputClass(
                    "io.spokestack.spokestack.android.MicrophoneInput")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.AutomaticGainControl")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.AcousticNoiseSuppressor")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.VoiceActivityDetector")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.VoiceActivityTrigger")
              .addStageClass("io.spokestack.spokestack.ActivationTimeout")
              .addStageClass(
                    "io.spokestack.spokestack.asr.SpokestackCloudRecognizer");
    }
}
