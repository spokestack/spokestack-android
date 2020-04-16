package io.spokestack.spokestack.nlu.tensorflow;

import org.junit.Test;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EncodedTokensTest {

    private static final String INITIAL_SPLIT_RE = "\\s+";

    @Test
    public void testDecode() {
        String[] spacedTokens = new String[]{"test", "text"};
        final EncodedTokens invalid = new EncodedTokens(spacedTokens);
        invalid.addTokenIds(Arrays.asList(0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalStateException.class,
              () -> invalid.setOriginalIndices(new ArrayList<>()));

        String text = "I made the WORST decision.";
        spacedTokens = text.split(INITIAL_SPLIT_RE);
        EncodedTokens encoded = new EncodedTokens(spacedTokens);
        encoded.addTokenIds(Arrays.asList(0, 0, 0, 0, 0, 0, 0));
        List<Integer> originalIndices = Arrays.asList(0, 1, 2, 3, 4, 5, 5);
        encoded.setOriginalIndices(originalIndices);
        // valid range
        assertEquals(text, encoded.decodeRange(0, 7, false));
        String trimmed = "I made the WORST decision";
        assertEquals(trimmed, encoded.decodeRange(0, 7, true));

        // invalid ranges
        final EncodedTokens finalEncoded = encoded;
        assertThrows(IndexOutOfBoundsException.class,
              () -> finalEncoded.decodeRange(-1, 3, false));
        assertThrows(IndexOutOfBoundsException.class,
              () -> finalEncoded.decodeRange(0, 8, false));
        assertThrows(IndexOutOfBoundsException.class,
              () -> finalEncoded.decodeRange(3, 2, false));

        text = "I";
        assertEquals(text, encoded.decodeRange(0, 1, false));
        assertEquals(text, encoded.decodeRange(0, 1, true));

        text = Normalizer.normalize("thé\t\tearl grey", Normalizer.Form.NFD);
        spacedTokens = text.split(INITIAL_SPLIT_RE);
        encoded = new EncodedTokens(spacedTokens);
        encoded.addTokenIds(Arrays.asList(0, 0, 0));
        originalIndices = Arrays.asList(0, 1, 2);
        encoded.setOriginalIndices(originalIndices);
        String expected = text.replaceAll(INITIAL_SPLIT_RE, " ");
        assertEquals(expected, encoded.decodeRange(0, 3, false));
        assertEquals("earl", encoded.decodeRange(1, 2, false));

        text = "\"(the)\" weird-est punctuation";
        spacedTokens = text.split(INITIAL_SPLIT_RE);
        encoded = new EncodedTokens(spacedTokens);
        encoded.addTokenIds(Arrays.asList(0, 0, 0, 0, 0, 0, 0));
        originalIndices = Arrays.asList(0, 0, 0, 0, 0, 1, 2);
        encoded.setOriginalIndices(originalIndices);
        assertEquals("\"(the)\"", encoded.decodeRange(0, 5, false));
        assertEquals("the", encoded.decodeRange(0, 5, true));
    }
}