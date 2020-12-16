package io.spokestack.spokestack;

import java.nio.ByteBuffer;

/**
 * Test classes related to the speech pipeline used in more than one test
 * suite.
 */
public class SpeechTestUtils {

    public static class FreeInput implements SpeechInput {
        public static int counter;

        public FreeInput(SpeechConfig config) {
            counter = 0;
        }

        public void close() {
            counter = -1;
        }

        public void read(SpeechContext context, ByteBuffer frame) {
            frame.putInt(0, ++counter);
        }
    }
}
