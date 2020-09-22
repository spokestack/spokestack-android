package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * A speech pipeline profile that uses TensorFlow Lite for wakeword detection
 * and Android's {@code SpeechRecognizer} API for ASR. Properties related to
 * signal processing are tuned for the "Spokestack" wakeword.
 *
 * <p>
 * Wakeword detection requires configuration to locate the models used for
 * classification; these properties must be set elsewhere:
 * </p>
 *
 * <ul>
 *   <li>
 *      <b>wake-filter-path</b> (string, required): file system path to the
 *      "filter" Tensorflow-Lite model, which is used to calculate a mel
 *      spectrogram frame from the linear STFT; its inputs should be shaped
 *      [fft-width], and its outputs [mel-width]
 *   </li>
 *   <li>
 *      <b>wake-encode-path</b> (string, required): file system path to the
 *      "encode" Tensorflow-Lite model, which is used to perform each
 *      autoregressive step over the mel frames; its inputs should be shaped
 *      [mel-length, mel-width], and its outputs [encode-width], with an
 *      additional state input/output shaped [state-width]
 *   </li>
 *   <li>
 *      <b>wake-detect-path</b> (string, required): file system path to the
 *      "detect" Tensorflow-Lite model; its inputs shoudld be shaped
 *      [encode-length, encode-width], and its outputs [1]
 *   </li>
 * </ul>
 *
 * <p>
 * Using Android's built-in ASR requires that an Android {@code Context} object
 * be attached to the speech pipeline using it. This must be done separately
 * from profile application, using
 * {@link SpeechPipeline.Builder#setAndroidContext(android.content.Context)}.
 * </p>
 *
 * @see io.spokestack.spokestack.android.AndroidSpeechRecognizer
 * @see io.spokestack.spokestack.wakeword.WakewordTrigger
 */
public class TFWakewordAndroidASR implements PipelineProfile {
    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        List<String> stages = new ArrayList<>();
        stages.add("io.spokestack.spokestack.webrtc.AutomaticGainControl");
        stages.add("io.spokestack.spokestack.webrtc.AcousticNoiseSuppressor");
        stages.add("io.spokestack.spokestack.webrtc.VoiceActivityDetector");
        stages.add("io.spokestack.spokestack.wakeword.WakewordTrigger");
        stages.add("io.spokestack.spokestack.android.AndroidSpeechRecognizer");

        return builder
              .setInputClass(
                    "io.spokestack.spokestack.android.PreASRMicrophoneInput")
              .setProperty("ans-policy", "aggressive")
              .setProperty("vad-mode", "very-aggressive")
              .setProperty("vad-fall-delay", 800)
              .setProperty("wake-threshold", 0.9)
              .setProperty("pre-emphasis", 0.97)
              .setStageClasses(stages);
    }
}
