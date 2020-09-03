package io.spokestack.spokestack.dialogue;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class PromptTest {

    @Test
    public void testBuild() {
        ConversationData data = new InMemoryConversationData();
        Prompt prompt = new Prompt.Builder("id", "text").build();

        // empty voice prompt copies text content
        assertEquals("id", prompt.getId());
        assertEquals("text", prompt.getVoice(data));
        assertNull(prompt.getProposal());
        assertEquals(0, prompt.getReprompts().length);
    }

    @Test
    public void testTemplateFilling() {
        ConversationData data = new InMemoryConversationData();
        Prompt prompt = new Prompt.Builder("id", "{{text}}")
              .withVoice("{{voice}}")
              .withProposal(new Proposal())
              .endsConversation()
              .build();

        data.set("text", "123");

        assertEquals("123", prompt.getText(data));
        assertTrue(prompt.endsConversation());
        assertNotNull(prompt.getProposal());
        assertNull(prompt.getProposal().getAccept());
        assertNull(prompt.getProposal().getReject());
        // an absent key leaves the placeholder in the prompt
        assertEquals("voice", prompt.getVoice(data));

        // set the key, and the prompt expands correctly
        data.set("voice", "one two three");
        assertEquals("one two three", prompt.getVoice(data));

        prompt = new Prompt.Builder("id", "text")
              .withReprompts(Collections.singletonList(prompt))
              .build();

        assertEquals(1, prompt.getReprompts().length);
        Prompt reprompt = prompt.getReprompts()[0];
        assertEquals("123", reprompt.getText(data));
        assertEquals("one two three", reprompt.getVoice(data));
        assertTrue(reprompt.endsConversation());
    }
}