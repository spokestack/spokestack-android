package io.spokestack.spokestack.tts;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * A simple wrapper class comprising the options available when making a
 * synthesis request.
 * </p>
 *
 * <p>
 * This class is designed with a request to the Spokestack TTS server in mind,
 * but its {@code metadata} field should make it adaptable to other services.
 * </p>
 *
 * <h2>SSML input supported by Spokestack</h2>
 *
 * <p>
 * <a href="https://www.w3.org/TR/speech-synthesis11/">SSML</a>
 * is an XML-based markup language; the root element must be {@code <speak>}.
 * Aside from {@code speak}, Spokestack supports the following elements, a
 * subset of the SSML spec:
 * </p>
 *
 * <ul>
 *     <li><a href="https://www.w3.org/TR/speech-synthesis11/#edef_sentence">
 *         {@code s}</a></li>
 *     <li><a href="https://www.w3.org/TR/speech-synthesis11/#edef_break">
 *         {@code break}</a></li>
 *     <li><a href="https://www.w3.org/TR/speech-synthesis11/#S3.1.9">
 *         {@code say-as}</a> with an {@code interpret-as} attribute
 *         of {@code "characters"}, {@code "spell-out"},
 *         or {@code "digits"}</li>
 *     <li><a href="https://www.w3.org/TR/speech-synthesis11/#S3.1.10">
 *         {@code phoneme}</a> with the {@code alphabet} attribute
 *         set to "ipa"</li>
 * </ul>
 *
 * <p>
 * Because SSML is based on XML, any characters invalid in XML, such as
 * {@code <}, {@code >}, and {@code &}, must be escaped as HTML entities to be
 * valid.
 * </p>
 *
 * <p>
 * Note that long inputs should be split into separate {@code s} ("sentence")
 * elements for the best performance.
 * </p>
 *
 * <p>
 * Currently, Spokestack is focused on pronunciation of English words and loan
 * words/foreign words common in spoken English and thus restricts its
 * character set from the full range of
 * <a href="https://en.wikipedia.org/wiki/International_Phonetic_Alphabet">
 * IPA</a> characters. Characters valid for an IPA {@code ph} attribute
 * are:
 * </p>
 *
 * <pre>
 *  [' ', ',', 'a', 'b', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
 *   'n', 'o', 'p', 'r', 's', 't', 'u', 'v', 'w', 'z', 'æ', 'ð', 'ŋ', 'ɑ',
 *   'ɔ', 'ə', 'ɛ', 'ɝ', 'ɪ', 'ʃ', 'ʊ', 'ʌ', 'ʒ', 'ˈ', 'ˌ', 'ː', 'θ', 'ɡ',
 *   'x', 'y', 'ɹ', 'ʰ', 'ɜ', 'ɒ', 'ɚ', 'ɱ', 'ʔ', 'ɨ', 'ɾ', 'ɐ', 'ʁ', 'ɵ', 'χ']
 * </pre>
 *
 * <p>
 * and the emphasis symbols {@code ˈ}, {@code ,}, {@code ˌ}, and {@code ː}.
 * </p>
 *
 * <p>
 * Using invalid characters will not cause an error, but it might result in
 * unexpected pronunciation.
 * </p>
 *
 * <p>
 * Failing to enclose SSML text in {@code speak} tags <em>will</em>
 * cause an error, but not until the request reaches the synthesis server.
 * </p>
 *
 */
public class SynthesisRequest {

    /**
     * The types of input available for synthesized speech.
     */
    public enum Mode {

        /**
         * Plain-text synthesis.
         */
        TEXT,

        /**
         * Synthesis using the subset of SSML supported by Spokestack.
         */
        SSML
    }

    /**
     * The text to be synthesized.
     */
    public final CharSequence text;

    /**
     * The synthesis mode, either {@code TEXT} or {@code SSML}.
     */
    public final Mode mode;

    /**
     * The voice in which the text should be synthesized.
     */
    public final String voice;

    /**
     * Any additional data that should be included along with the TTS request.
     */
    public final Map<String, String> metadata;

    /**
     * Construct a fully specified synthesis request object.
     *
     * @param textToSynthesize The text to be synthesized.
     * @param synthesisMode    The synthesis mode, either {@code TEXT} or {@code
     *                         SSML}. If using SSML mode, ensure that {@code
     *                         textToSynthesize} contains valid SSML, including
     *                         the root {@code <speak>} tags.
     * @param ttsVoice         The voice in which the text should be
     *                         synthesized.
     * @param requestData      Any additional data that should be included along
     *                         with the TTS request.
     * @see SSML
     */
    public SynthesisRequest(CharSequence textToSynthesize, Mode synthesisMode,
                            String ttsVoice, Map<String, String> requestData) {
        this.text = textToSynthesize;
        this.mode = synthesisMode;
        this.voice = ttsVoice;
        this.metadata = requestData;
    }

    /**
     * A fluent interface for constructing a {@code TTSInput} object piecemeal.
     */
    public static class Builder {
        private CharSequence textToSynthesize;
        private Mode synthesisMode = Mode.TEXT;
        private String ttsVoice = "demo-male";
        private Map<String, String> metadata = new HashMap<>();

        /**
         * Create a new {@code TTSInput} builder with the only required data,
         * the text to be synthesized. The default synthesis mode (text) and
         * voice will be used.
         *
         * @param text The text to be synthesized.
         */
        public Builder(CharSequence text) {
            this.textToSynthesize = text;
        }

        /**
         * Specify the synthesis mode.
         *
         * @param mode The synthesis mode, {@code TEXT} (default) or {@code
         *             SSML}.
         * @return The current builder.
         */
        public Builder withMode(Mode mode) {
            this.synthesisMode = mode;
            return this;
        }

        /**
         * Specify the voice to be used for synthesis.
         *
         * @param voice The voice to be used for synthesized audio.
         * @return The current builder.
         */
        public Builder withVoice(String voice) {
            this.ttsVoice = voice;
            return this;
        }

        /**
         * Specify additional data to be used for the synthesis request.
         *
         * @param requestData Additional data to be sent as part of the TTS
         *                    request.
         * @return The current builder.
         */
        public Builder withData(Map<String, String> requestData) {
            this.metadata = requestData;
            return this;
        }

        /**
         * Use the state of the builder to construct a fully specified synthesis
         * request.
         *
         * @return A synthesis request constructed from the state of the
         * builder.
         */
        public SynthesisRequest build() {
            return new SynthesisRequest(textToSynthesize, synthesisMode,
                  ttsVoice, metadata);
        }
    }
}
