package io.spokestack.spokestack.nlu.tensorflow;

/**
 * A simple interface for translating between raw text and neural model
 * input. In addition to the duties of a typical tokenizer, this component
 * also handles normalization and encoding the tokenized text into integers
 * suitable for sending to a neural network.
 */
public interface TextEncoder {

    /**
     * Encode a raw string into identifiers for its constituent tokens. This
     * represents the combination of typical normalization and tokenization
     * string processing tasks and includes the identifier lookup as well for
     * sake of efficiency. Implementors are free to subdivide this task however
     * they see fit, but they should not expect pre-tokenized input.
     *
     * @param text The raw text to encode.
     * @return The text and identifiers of the tokenized text enclosed in an
     * {@link EncodedTokens} object.
     */
    EncodedTokens encode(String text);

    /**
     * Retrieves the identifier for the specified token without performing any
     * tokenization. This method is designed to be used with special tokens
     * like padding and input separators, so the provided token is expected to
     * map directly to an ID.
     *
     * <p>
     * If an unknown token is passed to this method, the implementation should
     * map it to an identifier reserved for unknown tokens, and performance of
     * the model will suffer accordingly.
     * </p>
     *
     * @param token The token to encode.
     * @return The token's identifier in the encoder's vocabulary.
     */
    int encodeSingle(String token);
}
