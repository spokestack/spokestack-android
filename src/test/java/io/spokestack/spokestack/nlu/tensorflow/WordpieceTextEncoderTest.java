package io.spokestack.spokestack.nlu.tensorflow;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.nlu.NLUContext;
import io.spokestack.spokestack.util.EventTracer;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WordpieceTextEncoderTest {
    private static final String VOCAB_PATH = "src/test/resources/vocab.txt";

    @Test
    public void loadErrors() throws InterruptedException {
        SpeechConfig config = new SpeechConfig();
        config.put("wordpiece-vocab-path", "invalid/path");
        NLUContext context = new NLUContext(config);
        AtomicBoolean loadError = new AtomicBoolean(false);
        context.addTraceListener((level, message) -> {
            if (level.equals(EventTracer.Level.ERROR)) {
                loadError.set(true);
            }
        });
        ControllableFactory factory = new ControllableFactory();

        new WordpieceTextEncoder(config, context, factory);
        factory.theOneThread.join();
        assertTrue(loadError.get());
    }

    @Test
    public void encodeSingle() {
        SpeechConfig config = new SpeechConfig();
        config.put("wordpiece-vocab-path", VOCAB_PATH);
        NLUContext context = new NLUContext(config);
        WordpieceTextEncoder encoder =
              new WordpieceTextEncoder(config, context);

        assertEquals(1, encoder.encodeSingle("the"));
        assertEquals(0, encoder.encodeSingle("tea"));
        assertEquals(0, encoder.encodeSingle("[UNK]"));
    }

    @Test
    public void encode() {
        SpeechConfig config = new SpeechConfig();
        config.put("wordpiece-vocab-path", VOCAB_PATH);
        NLUContext context = new NLUContext(config);
        WordpieceTextEncoder encoder =
              new WordpieceTextEncoder(config, context);

        String text = " ";
        EncodedTokens encoded = encoder.encode(text);
        List<Integer> expectedIds = new ArrayList<>();
        assertEquals(expectedIds, encoded.getIds());

        text = "I made the WORST decision.";
        encoded = encoder.encode(text);
        expectedIds = Arrays.asList(0, 0, 1, 2, 3, 4, 0);
        assertEquals(expectedIds, encoded.getIds());

        // if any part of the word isn't in the dictionary, the whole token
        // gets the [UNK] id
        // (not going to happen in practice, but this just exercises
        // the tokenizer)
        encoded = encoder.encode("the decisioner");
        expectedIds = Arrays.asList(1, 0);
        assertEquals(expectedIds, encoded.getIds());

        encoded = encoder.encode("I made the WORST decisions.");
        expectedIds = Arrays.asList(0, 0, 1, 2, 3, 4, 5, 0);
        assertEquals(expectedIds, encoded.getIds());

        encoded = encoder.encode("these s e decisions");
        // "s" and "e" are not listed as whole tokens in the vocab
        expectedIds = Arrays.asList(1, 5, 6, 0, 0, 3, 4, 5);
        assertEquals(expectedIds, encoded.getIds());

        text = "thé\t\tdecisions\n.";
        encoded = encoder.encode(text);
        expectedIds = Arrays.asList(1, 3, 4, 5, 0);
        assertEquals(expectedIds, encoded.getIds());

        encoded = encoder.encode("\"(these)\" decisions");
        expectedIds = Arrays.asList(0, 0, 1, 5, 6, 0, 0, 3, 4, 5);
        assertEquals(expectedIds, encoded.getIds());

        // a couple round trips to verify proper index tracking
        assertEquals("\"(these)\"", encoded.decodeRange(0, 2, false));
        assertEquals("these", encoded.decodeRange(0, 2, true));
        assertEquals("\"(these)\" decisions", encoded.decodeRange(0, 9, false));
        assertEquals("these)\" decisions", encoded.decodeRange(0, 9, true));
    }

    static class ControllableFactory implements ThreadFactory {
        private Thread theOneThread;

        @Override
        public Thread newThread(@NotNull Runnable r) {
            theOneThread = new Thread(r);
            return theOneThread;
        }
    }
}