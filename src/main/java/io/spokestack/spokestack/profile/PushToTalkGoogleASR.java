package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * A speech pipeline profile that relies on manual pipeline activation, using
 * Google Speech for ASR.
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
 *
 * @see io.spokestack.spokestack.google.GoogleSpeechRecognizer
 */
public class PushToTalkGoogleASR implements PipelineProfile {
    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        List<String> stages = new ArrayList<>();
        stages.add("io.spokestack.spokestack.webrtc.AutomaticGainControl");
        stages.add("io.spokestack.spokestack.webrtc.AcousticNoiseSuppressor");
        stages.add("io.spokestack.spokestack.webrtc.VoiceActivityDetector");
        stages.add("io.spokestack.spokestack.ActivationTimeout");
        stages.add("io.spokestack.spokestack.google.GoogleSpeechRecognizer");

        return builder
              .setInputClass("io.spokestack.spokestack.android.MicrophoneInput")
              .setStageClasses(stages);
    }
}
