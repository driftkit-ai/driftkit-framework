package ai.driftkit.common.service.impl;

import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.chat.ChatMessage.MessageType;
import ai.driftkit.common.service.TextTokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ai.driftkit.common.domain.chat.ChatMessage.PROPERTY_MESSAGE;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryChatStoreTest {
    
    private InMemoryChatStore chatStore;
    private static final String CHAT_ID = "test-chat-123";
    
    @BeforeEach
    void setUp() {
        TextTokenizer tokenizer = new SimpleTextTokenizer();
        chatStore = new InMemoryChatStore(tokenizer, 100); // Small token limit for testing
    }
    
    @Test
    void testAddSimpleMessage() {
        // When
        chatStore.add(CHAT_ID, "Hello, world!", MessageType.USER);
        
        // Then
        List<ChatMessage> messages = chatStore.getAll(CHAT_ID);
        assertEquals(1, messages.size());
        assertEquals("Hello, world!", messages.get(0).getPropertiesMap().get(PROPERTY_MESSAGE));
        assertEquals(MessageType.USER, messages.get(0).getType());
    }
    
    @Test
    void testAddMessageWithProperties() {
        // Given
        Map<String, String> properties = new HashMap<>();
        properties.put(PROPERTY_MESSAGE, "Test message");
        properties.put("intent", "greeting");
        properties.put("confidence", "0.95");
        
        // When
        chatStore.add(CHAT_ID, properties, MessageType.AI);
        
        // Then
        List<ChatMessage> messages = chatStore.getAll(CHAT_ID);
        assertEquals(1, messages.size());
        assertEquals("Test message", messages.get(0).getPropertiesMap().get(PROPERTY_MESSAGE));
        assertEquals("greeting", messages.get(0).getPropertiesMap().get("intent"));
        assertEquals("0.95", messages.get(0).getPropertiesMap().get("confidence"));
    }
    
    @Test
    void testAddChatMessage() {
        // Given
        ChatMessage message = new ChatMessage();
        message.setChatId(CHAT_ID);
        message.setType(MessageType.SYSTEM);
        message.updateOrAddProperty(PROPERTY_MESSAGE, "System message");
        
        // When
        chatStore.add(message);
        
        // Then
        List<ChatMessage> messages = chatStore.getAll(CHAT_ID);
        assertEquals(1, messages.size());
        assertNotNull(messages.get(0).getId());
        assertNotNull(messages.get(0).getTimestamp());
    }
    
    @Test
    void testGetRecentWithLimit() {
        // Given
        for (int i = 0; i < 10; i++) {
            chatStore.add(CHAT_ID, "Message " + i, MessageType.USER);
        }
        
        // When
        List<ChatMessage> recent = chatStore.getRecent(CHAT_ID, 5);
        
        // Then
        assertEquals(5, recent.size());
        assertEquals("Message 5", recent.get(0).getPropertiesMap().get(PROPERTY_MESSAGE));
        assertEquals("Message 9", recent.get(4).getPropertiesMap().get(PROPERTY_MESSAGE));
    }
    
    @Test
    void testGetRecentWithinTokens() {
        // Given - each message is ~20 characters = ~5 tokens
        chatStore.add(CHAT_ID, "Short message one", MessageType.USER);    // ~5 tokens
        chatStore.add(CHAT_ID, "Short message two", MessageType.AI);      // ~5 tokens
        chatStore.add(CHAT_ID, "This is a longer message with more content", MessageType.USER); // ~11 tokens
        
        // When - get messages within 15 tokens
        List<ChatMessage> recent = chatStore.getRecentWithinTokens(CHAT_ID, 15);
        
        // Then - should get last two messages only
        assertEquals(2, recent.size());
        assertEquals("Short message two", recent.get(0).getPropertiesMap().get(PROPERTY_MESSAGE));
        assertEquals("This is a longer message with more content", recent.get(1).getPropertiesMap().get(PROPERTY_MESSAGE));
    }
    
    @Test
    void testAutoPruning() {
        // Given - token limit is 100, each message ~5 tokens
        for (int i = 0; i < 30; i++) {
            chatStore.add(CHAT_ID, "Message number " + i, MessageType.USER);
        }
        
        // Then - should have pruned old messages to stay under 100 tokens
        List<ChatMessage> messages = chatStore.getAll(CHAT_ID);
        assertTrue(messages.size() < 30);
        
        int totalTokens = chatStore.getTotalTokens(CHAT_ID);
        assertTrue(totalTokens <= 100);
    }
    
    @Test
    void testUpdateMessage() {
        // Given
        chatStore.add(CHAT_ID, "Original message", MessageType.USER);
        ChatMessage original = chatStore.getAll(CHAT_ID).get(0);
        
        // When
        original.updateOrAddProperty(PROPERTY_MESSAGE, "Updated message");
        original.updateOrAddProperty("edited", "true");
        chatStore.update(original);
        
        // Then
        ChatMessage updated = chatStore.getAll(CHAT_ID).get(0);
        assertEquals("Updated message", updated.getPropertiesMap().get(PROPERTY_MESSAGE));
        assertEquals("true", updated.getPropertiesMap().get("edited"));
    }
    
    @Test
    void testDeleteMessage() {
        // Given
        chatStore.add(CHAT_ID, "Message 1", MessageType.USER);
        chatStore.add(CHAT_ID, "Message 2", MessageType.AI);
        ChatMessage toDelete = chatStore.getAll(CHAT_ID).get(0);
        
        // When
        chatStore.delete(toDelete.getId());
        
        // Then
        List<ChatMessage> remaining = chatStore.getAll(CHAT_ID);
        assertEquals(1, remaining.size());
        assertEquals("Message 2", remaining.get(0).getPropertiesMap().get(PROPERTY_MESSAGE));
    }
    
    @Test
    void testDeleteAllMessages() {
        // Given
        chatStore.add(CHAT_ID, "Message 1", MessageType.USER);
        chatStore.add(CHAT_ID, "Message 2", MessageType.AI);
        
        // When
        chatStore.deleteAll(CHAT_ID);
        
        // Then
        assertFalse(chatStore.chatExists(CHAT_ID));
        assertEquals(0, chatStore.getAll(CHAT_ID).size());
    }
    
    @Test
    void testMultipleChats() {
        // Given
        String chat1 = "chat-1";
        String chat2 = "chat-2";
        
        // When
        chatStore.add(chat1, "Chat 1 message", MessageType.USER);
        chatStore.add(chat2, "Chat 2 message", MessageType.USER);
        
        // Then
        assertEquals(1, chatStore.getAll(chat1).size());
        assertEquals(1, chatStore.getAll(chat2).size());
        assertEquals("Chat 1 message", chatStore.getAll(chat1).get(0).getPropertiesMap().get(PROPERTY_MESSAGE));
        assertEquals("Chat 2 message", chatStore.getAll(chat2).get(0).getPropertiesMap().get(PROPERTY_MESSAGE));
    }
}