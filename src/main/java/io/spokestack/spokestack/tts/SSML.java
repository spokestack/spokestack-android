package io.spokestack.spokestack.tts;

/**
 * <p>
 * A simple wrapper class for an SSML string.
 * </p>
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
 */
public class SSML {
    private final String text;

    /**
     * Designate a string as representing SSML text. No further formatting or
     * validation is performed on this string; it is expected to be enclosed in
     * {@code speak} tags and be otherwise valid SSML.
     *
     * @param ssmlText The text to designate as SSML.
     */
    public SSML(String ssmlText) {
        this.text = ssmlText;
    }

    /**
     * Get the SSML content as a raw string.
     *
     * @return The SSML content as text.
     */
    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return text;
    }
}
