package io.spokestack.spokestack.util;

import java.io.UnsupportedEncodingException;

/**
 * <p>
 * A simple Base64 encoding class that only supports the operations needed by
 * Spokestack. This custom implementation exists to maintain both backwards
 * compatibility and testability for Spokestack: {@link java.util.Base64} was
 * only introduced in Java 8 and is unavailable for older Android API versions.
 * Using the Android variant makes Base64 encoding, thus, request signing
 * untestable since the Base64 encoding method would have to be mocked.
 * </p>
 */
public final class Base64 {

    private static final char[] ENCODING_TABLE = {
          'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N',
          'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b',
          'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p',
          'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3',
          '4', '5', '6', '7', '8', '9', '+', '/'
    };

    private Base64() { }

    /**
     * Encode a byte array into a Base64-encoded UTF-8 string.
     *
     * <p>
     * The implementation is adapted from OpenJDK's {@code Base64.Encoder}.
     * It does not support:
     * </p>
     *
     * <ul>
     *     <li>URL-safe Base64</li>
     *     <li>line breaks in the encoded string</li>
     *     <li>Base64 without padding</li>
     * </ul>
     *
     * @param bytes The byte array to encode.
     * @return A Base64-encoded string.
     */
    public static String encode(byte[] bytes) {
        int srcPos = 0;
        int destPos = 0;
        int destLength = 4 * ((bytes.length + 2) / 3);
        byte[] encoded = new byte[destLength];

        while (srcPos + 3 <= bytes.length) {
            int bits = chunkBytes(bytes, srcPos);
            appendEncoded(encoded, bits, destPos);
            srcPos += 3;
            destPos += 4;
        }
        if (srcPos == bytes.length - 1) {
            padSingleByte(bytes, encoded);
        } else if (srcPos == bytes.length - 2) {
            padTwoBytes(bytes, encoded);
        }
        try {
            return new String(encoded, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // if UTF-8 isn't supported, we have bigger problems
            throw new IllegalArgumentException("UTF-8 encoding not supported");
        }
    }

    private static int chunkBytes(byte[] bytes, int pos) {
        return (bytes[pos] & 0xff) << 16
              | (bytes[pos + 1] & 0xff) << 8
              | (bytes[pos + 2] & 0xff);
    }

    private static void appendEncoded(byte[] encoded, int bits, int cursor) {
        encoded[cursor] = (byte) ENCODING_TABLE[(bits >>> 18) & 0x3f];
        encoded[cursor + 1] = (byte) ENCODING_TABLE[(bits >>> 12) & 0x3f];
        encoded[cursor + 2] = (byte) ENCODING_TABLE[(bits >>> 6) & 0x3f];
        encoded[cursor + 3] = (byte) ENCODING_TABLE[bits & 0x3f];
    }

    private static void padSingleByte(byte[] source, byte[] encoded) {
        int bits = source[source.length - 1] & 0xff;
        int cursor = encoded.length - 4;
        encoded[cursor] = (byte) ENCODING_TABLE[bits >> 2];
        encoded[cursor + 1] = (byte) ENCODING_TABLE[(bits << 4) & 0x3f];
        encoded[cursor + 2] = '=';
        encoded[cursor + 3] = '=';
    }

    private static void padTwoBytes(byte[] source, byte[] encoded) {
        int bits = source[source.length - 2] & 0xff;
        int cursor = encoded.length - 4;
        encoded[cursor] = (byte) ENCODING_TABLE[bits >> 2];
        int nextBits = source[source.length - 1] & 0xff;
        encoded[cursor + 1] =
              (byte) ENCODING_TABLE[(bits << 4) & 0x3f | (nextBits >> 4)];
        encoded[cursor + 2] = (byte) ENCODING_TABLE[(nextBits << 2) & 0x3f];
        encoded[cursor + 3] = '=';
    }
}
