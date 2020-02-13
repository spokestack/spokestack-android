package io.spokestack.spokestack.nlu.tensorflow;

import io.spokestack.spokestack.SpeechConfig;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Normalizes, tokenizes, and encodes text according to the
 * <a href="https://arxiv.org/pdf/1609.08144.pdf">Wordpiece</a> method, using a
 * pre-computed vocabulary for computing token IDs.
 *
 * <p>
 * This version of the Wordpiece tokenizer does not currently have special
 * handling for CJK (Chinese/Japanese/Korean) characters, so do not expect it to
 * produce the same results on CJK input as other Wordpiece tokenizers.
 * </p>
 */
public class WordpieceTextEncoder implements TextEncoder {
    private static final String UNKNOWN = "[UNK]";
    private static final String SUFFIX_MARKER = "##";

    private HashMap<String, Integer> vocabulary;

    /**
     * Creates a new Wordpiece token encoder.
     *
     * @param config Configuration object containing the name of wordpiece
     *               resource file.
     * @throws Exception if there was an error loading the Wordpiece
     *                   vocabulary.
     */
    public WordpieceTextEncoder(SpeechConfig config) throws Exception {
        String vocabFile = config.getString("wordpiece-vocab-path");
        this.vocabulary = loadVocab(vocabFile);
    }

    @Override
    public int encodeSingle(String token) {
        return this.vocabulary.getOrDefault(token,
              this.vocabulary.get(UNKNOWN));
    }

    private HashMap<String, Integer> loadVocab(String fileName)
          throws IOException {
        FileInputStream inputStream = new FileInputStream(fileName);
        BufferedReader reader = new BufferedReader(
              new InputStreamReader(inputStream));
        HashMap<String, Integer> words = new HashMap<>();
        int index = 0;
        String line = reader.readLine();
        while (line != null) {
            words.put(line, index);
            line = reader.readLine();
            index++;
        }
        inputStream.close();
        reader.close();
        return words;
    }

    @Override
    public EncodedTokens encode(String text) {
        String[] spaceSeparated = text.split("[\\s\\p{Space}]+");
        EncodedTokens encoded = new EncodedTokens(spaceSeparated);

        String token;
        String[] punctSeparated;
        List<Integer> pieceToOriginal = new ArrayList<>();
        for (int i = 0; i < spaceSeparated.length; i++) {
            token = spaceSeparated[i];
            punctSeparated = normalizeAndStripPunct(token);
            for (String subToken : punctSeparated) {
                List<Integer> ids = encodeWordpieces(subToken);
                encoded.addTokenIds(ids);
                for (int j = 0; j < ids.size(); j++) {
                    // add the same original token index once for each wordpiece
                    // we've found
                    pieceToOriginal.add(i);
                }
            }
        }
        encoded.setOriginalIndices(pieceToOriginal);
        return encoded;
    }

    private String[] normalizeAndStripPunct(String word) {
        // drop diacritics and split punctuation characters off the main word
        // we do this via iteration in order to do both tasks in a single pass
        // input to this method is expected to have been split on whitespace
        List<String> subTokens = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        String decomposed = Normalizer.normalize(word, Normalizer.Form.NFD);
        for (int i = 0; i < decomposed.length(); i++) {
            char ch = decomposed.charAt(i);
            if (isPunctuation(ch)) {
                if (builder.length() > 0) {
                    subTokens.add(builder.toString().toLowerCase());
                    builder = new StringBuilder();
                }
                subTokens.add(String.valueOf(ch));
            } else if (!isInvalid(ch)) {
                builder.append(ch);
            }
        }
        if (builder.length() > 0) {
            subTokens.add(builder.toString().toLowerCase());
        }

        return subTokens.toArray(new String[0]);
    }

    private boolean isInvalid(char ch) {
        int charType = Character.getType(ch);
        return (charType == Character.NON_SPACING_MARK
              || charType == Character.FORMAT
              || charType == Character.CONTROL);
    }

    private boolean isPunctuation(char ch) {
        int type = Character.getType(ch);
        // all types between DASH and OTHER are also punctuation,
        // but the quote groups are off by themselves
        return (type >= Character.DASH_PUNCTUATION
              && type <= Character.OTHER_PUNCTUATION)
              || type == Character.INITIAL_QUOTE_PUNCTUATION
              || type == Character.FINAL_QUOTE_PUNCTUATION;
    }

    private List<Integer> encodeWordpieces(String word) {
        List<Integer> ids = new ArrayList<>();
        String unencoded = encodeLongestWordpieces(word, "", ids);
        if (unencoded != null) {
            // if we can't encode part of the word, we can't encode any of it;
            // there is no ##[UNK], for good reason
            ids.clear();
            ids.add(this.vocabulary.get(UNKNOWN));
        }
        return ids;
    }

    private String encodeLongestWordpieces(String text,
                                           String prefix,
                                           List<Integer> soFar) {
        String combined = prefix + text;
        if (this.vocabulary.containsKey(combined)) {
            soFar.add(this.vocabulary.get(combined));
            return null;
        }

        String unencoded = combined;
        int minIndex = prefix.isEmpty() ? 0 : prefix.length();
        for (int i = combined.length() - 1; i > minIndex; i--) {
            String subToken = combined.substring(0, i);
            Integer id = this.vocabulary.get(subToken);
            if (id != null) {
                soFar.add(id);
                unencoded = encodeLongestWordpieces(
                      combined.substring(i), SUFFIX_MARKER, soFar);
                break;
            }
        }
        return unencoded;
    }
}
