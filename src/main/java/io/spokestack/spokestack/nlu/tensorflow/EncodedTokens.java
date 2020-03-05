package io.spokestack.spokestack.nlu.tensorflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A simple data class that represents both the text of tokens produced from a
 * full string and identifiers associated with those tokens.
 */
final class EncodedTokens {

    private final List<String> originalTokens;
    private final List<Integer> ids;
    private List<Integer> originalIndices;

    /**
     * Create a new instance.
     *
     * @param spaceSeparated The original string split on whitespace.
     */
    EncodedTokens(String[] spaceSeparated) {
        this.ids = new ArrayList<>();
        this.originalTokens = Arrays.asList(spaceSeparated);
    }

    /**
     * @return The identifiers associated with the tokens.
     */
    public List<Integer> getIds() {
        return ids;
    }

    /**
     * Store the array mapping indices in the token ID list to those tokens'
     * indices in the original token array.
     *
     * @param indices The mapping array.
     * @throws IllegalStateException if the supplied array does not match the
     *                               length of the token ID list.
     */
    public void setOriginalIndices(List<Integer> indices) {
        if (indices.size() != ids.size()) {
            throw new IllegalStateException(
                  "original indices and the token ID list must be the "
                        + "same length!");
        }
        this.originalIndices = indices;
    }

    /**
     * Add a series of token ids.
     *
     * @param tokenIds The token identifiers.
     */
    public void addTokenIds(List<Integer> tokenIds) {
        this.ids.addAll(tokenIds);
    }

    /**
     * Use the index map created during encoding to recover a section of the
     * original string. Like {@link List#subList(int, int)}, this method uses a
     * half-open interval: the range returned includes the token referenced by
     * {@code start} but excludes the one referenced by {@code stop} (unless a
     * previous subtoken was also mapped to that token; see below).
     *
     * <p>
     * For example, if the string {@code "The quick brown fox"} resulted in the
     * token IDs {@code [1, 2, 3, 4]}, a call to {@code decodeRange(0, 2)} would
     * return the text "The quick".
     * </p>
     *
     * <p>
     * If the end of the interval falls in the middle of a token that has been
     * split into wordpieces, the interval will be implicitly expanded to the
     * end of that original token.
     * </p>
     *
     * @param start The position, inclusive, of the first token to recover.
     * @param stop  The position, exclusive, of the last token to recover.
     * @return The portion of the original string represented by the range of
     * supplied token IDs.
     * @throws IndexOutOfBoundsException if a requested index falls outside the
     *                                   length of the token id array.
     */
    public String decodeRange(int start, int stop) {
        if (stop < start || start < 0 || stop > this.ids.size()) {
            String message = String.format(
                  "Invalid token range: (%s, %s] for %s total tokens",
                  start, stop, ids.size());
            throw new IndexOutOfBoundsException(message);
        }

        int firstToken = this.originalIndices.get(start);
        int lastToken = this.originalIndices.get(stop - 1);
        // add one to compensate for the half-open interval of subList
        int toIndex = Math.min(lastToken + 1, this.originalTokens.size());
        return String.join(" ",
              this.originalTokens.subList(firstToken, toIndex));
    }
}
