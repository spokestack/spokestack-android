package io.spokestack.spokestack.dialogue;

/**
 * <p>
 * This interface must be implemented by clients that wish to provide external
 * data to Spokestack's dialogue manager.
 * </p>
 *
 * <p>
 * "External data" here means any data not available from the user's voice
 * interactions with the app; in other words, account data, or data produced or
 * retrieved by a user action. Anything data required by the dialogue but not
 * explicitly provided by the user should be available via a key-based lookup
 * against a class that implements this interface.
 * </p>
 *
 * <p>
 * If an implementer of this interface is provided to Spokestack's dialogue
 * manager, Spokestack will use its {@code set} method to store conversational
 * data. No persistence is performed by the dialogue manager, so any data
 * intended to be long-lived must be persisted by the app.
 * </p>
 *
 * <p>
 * Keys stored by Spokestack will begin with an underscore ({@code _}), so data
 * stored by the app should not use that prefix unless attempting to override a
 * piece of conversation data.
 * </p>
 *
 * <p>
 * Note that this API stores objects but expects to retrieve strings. This is to
 * allow clients to store any type of data they wish but provide their own
 * formatting for how custom objects should be read to the user in a synthesized
 * response or printed in a chat stream. The dialogue manager does not need to
 * retrieve original objects stored by this interface, so implementation of a
 * {@code get} method is not required, though it may be useful in certain
 * cases.
 * </p>
 */
public interface ConversationData {

    /**
     * <p>
     * Store an object under the specified key.
     * </p>
     *
     * <p>
     * Note that since this API is used by Spokestack to store data provided by
     * the user during voice interactions, {@code key} should not begin with an
     * underscore ({@code _}) unless intentionally overriding a key set by the
     * dialogue manager.
     * </p>
     *
     * @param key   The key under which {@code value} will be available.
     * @param value Data to store for use by the conversation.
     */
    void set(String key, Object value);

    /**
     * <p>
     * Get a version of the data stored at {@code key} suitable for inserting
     * into the conversation.
     * </p>
     *
     * <p>
     * Full objects are stored by the API, but the dialogue manager needs
     * formatted strings to use for prompts. The simplest implementation is to
     * call {@link Object#toString()} on the stored object, but custom
     * formatting logic can be supplied as needed.
     * </p>
     *
     * <p>
     * Occasionally an object (for example, a date) might require different
     * formatting for use in a synthesized voice response than for use in a
     * text-based interface, so a formatting mode is also required.
     * </p>
     *
     * @param key  The key of the data to be retrieved.
     * @param mode The data's destination, either text or voice.
     * @return A version of the data stored at {@code key} suitable for use in
     * the specified medium.
     */
    String getFormatted(String key, Format mode);

    /**
     * A simple enum that expresses the eventual use of retrieved conversation
     * data.
     */
    enum Format {

        /**
         * The data will be printed in a text response.
         */
        TEXT,

        /**
         * The data will be synthesized by a TTS engine.
         */
        VOICE;
    }
}
