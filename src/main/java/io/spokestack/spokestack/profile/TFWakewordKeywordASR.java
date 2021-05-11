package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * A speech pipeline profile that uses TensorFlow Lite for wakeword detection
 * and keyword detection-based ASR. The pipeline will be activated by
 * successful detection of the wakeword, and {@code KeywordRecognizer} will act
 * as the ASR component, only transcribing utterances contained in its
 * vocabulary. Properties related to signal processing are
 * tuned for the "Spokestack" wakeword.
 *
 * <p>
 * The activation period ends either:
 *
 * <ul>
 *   <li>
 *      when speech stops, as determined by {@code VoiceActivityDetector}'s
 *      configuration, or
 *   </li>
 *   <li>
 *      when the activation period exceeds the timeout configured in
 *      {@code ActivationTimeout}
 *   </li>
 * </ul>
 *
 * <p>
 * If no valid keyword is detected during the activation period, a timeout
 * event will be dispatched.
 * </p>
 *
 * <p>
 * The components in this profile process speech entirely on the local device;
 * no cloud components are involved.
 * </p>
 *
 * <p>
 * Wakeword detection requires configuration to locate the models used for
 * classification; these properties must be set separately from this profile:
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
 * The keyword detector also requires configuration:
 * </p>
 *
 * <ul>
 *   <li>
 *      <b>keyword-filter-path</b> (string): file system path to the
 *      "filter" Tensorflow-Lite model
 *   </li>
 *   <li>
 *      <b>keyword-encode-path</b> (string): file system path to the
 *      "encode" Tensorflow-Lite model
 *   </li>
 *   <li>
 *      <b>keyword-detect-path</b> (string): file system path to the
 *      "detect" Tensorflow-Lite model
 *   </li>
 *   <li>
 *      <b>keyword-metadata-path</b> (string): file system path to the
 *      keyword model's metadata JSON file containing its classes.
 *      Required if {@code keyword-classes} is not supplied.
 *   </li>
 *   <li>
 *      <b>keyword-classes</b> (string): comma-separated ordered
 *      list of class names for the keywords; the name corresponding to the
 *      most likely class will be returned in the transcript field when the
 *      recognition event is raised. Required if {@code keyword-metadata-path}
 *      is not supplied.
 *   </li>
 * </ul>
 *
 * @see io.spokestack.spokestack.wakeword.WakewordTrigger
 * @see io.spokestack.spokestack.asr.KeywordRecognizer
 */
public class TFWakewordKeywordASR implements PipelineProfile {
    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        List<String> stages = new ArrayList<>();
        stages.add("io.spokestack.spokestack.webrtc.AutomaticGainControl");
        stages.add("io.spokestack.spokestack.webrtc.AcousticNoiseSuppressor");
        stages.add("io.spokestack.spokestack.webrtc.VoiceActivityDetector");
        stages.add("io.spokestack.spokestack.wakeword.WakewordTrigger");
        stages.add("io.spokestack.spokestack.ActivationTimeout");
        stages.add("io.spokestack.spokestack.asr.KeywordRecognizer");

        return builder
              .setInputClass(
                    "io.spokestack.spokestack.android.MicrophoneInput")
              .setProperty("ans-policy", "aggressive")
              .setProperty("vad-mode", "very-aggressive")
              .setProperty("vad-fall-delay", 800)
              .setProperty("wake-threshold", 0.9)
              .setProperty("pre-emphasis", 0.97)
              .setProperty("wake-active-min", 1000)
              .setStageClasses(stages);
    }
}
