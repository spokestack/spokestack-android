package io.spokestack.spokestack.nlu.tensorflow.parsers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class SelsetParserTest {
    private static final String CONFIG_JSON =
          "{\"selections\": ["
                + "{\"name\": \"foo\", \"aliases\": [\"bar\"]}, "
                + "{\"name\": \"other\", \"aliases\": []}"
                + "]}";

    private SelsetParser parser;
    private HashMap<String, Object> metadata;

    @Before
    public void setup() {
        parser = new SelsetParser();
        Gson gson = new Gson();
        Type type = new TypeToken<HashMap<String, Object>>(){}.getType();
        metadata = gson.fromJson(CONFIG_JSON, type);
    }

    @Test
    public void invalidParses() {
        // bad config
        HashMap<String, Object> emptyMeta = new HashMap<>();
        assertNull(parser.parse(emptyMeta, "foo"));

        // invalid input
        assertNull(parser.parse(metadata, "invalid"));
        assertNull(parser.parse(metadata, ""));
    }

    @Test
    public void validParses() {
        assertEquals("foo", parser.parse(metadata, "foo"));
        assertEquals("foo", parser.parse(metadata, "bar"));
        assertEquals("other", parser.parse(metadata, "other"));
    }
}