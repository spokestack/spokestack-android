package io.spokestack.spokestack.android;

/**
 * A simple exception class that wraps error codes from {@link
 * android.media.AudioRecord AudioRecord}.
 */
public class AudioRecordError extends Exception {

    /**
     * Create a new error from a code code provided by the
     * Android system.
     *
     * @param errorCode The Android system error code.
     */
    public AudioRecordError(int errorCode) {
        super("AudioRecord error code " + errorCode + ": "
              + Description.valueOf(errorCode));
    }

    /**
     * An enumeration of the AudioRecord error descriptions aligned with
     * their integer constant values.
     */
    @SuppressWarnings("checkstyle:javadocvariable")
    public enum Description {
        INVALID_OPERATION(-3),
        BAD_VALUE(-2),
        DEAD_OBJECT(-6),
        UNKNOWN_ERROR(-1);

        private final int code;

        Description(int errorCode) {
            this.code = errorCode;
        }

        /**
         * Retrieve an error description by code.
         *
         * @param errorCode The Android error code.
         * @return A description of the error based on its code.
         */
        public static Description valueOf(int errorCode) {
            for (Description description : Description.values()) {
                if (description.code == errorCode) {
                    return description;
                }
            }
            return Description.UNKNOWN_ERROR;
        }
    }
}
