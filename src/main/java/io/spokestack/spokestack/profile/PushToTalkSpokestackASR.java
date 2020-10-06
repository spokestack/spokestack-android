package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * A speech pipeline profile that relies on manual pipeline activation, using
 * Spokestack's cloud-based ASR.
 *
 * <p>
 * Spokestack's cloud-based  ASR requires extra configuration, which must be
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
public class PushToTalkSpokestackASR implements PipelineProfile {
    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        List<String> stages = new ArrayList<>();
        stages.add("io.spokestack.spokestack.webrtc.AutomaticGainControl");
        stages.add("io.spokestack.spokestack.webrtc.AcousticNoiseSuppressor");
        stages.add("io.spokestack.spokestack.webrtc.VoiceActivityDetector");
        stages.add("io.spokestack.spokestack.ActivationTimeout");
        stages.add(
              "io.spokestack.spokestack.android.SpokestackCloudRecognizer");

        return builder
              .setInputClass("io.spokestack.spokestack.android.MicrophoneInput")
              .setStageClasses(stages);
    }
}
