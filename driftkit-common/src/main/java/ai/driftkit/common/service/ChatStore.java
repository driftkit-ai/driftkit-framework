package ai.driftkit.common.service;

import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.chat.ChatMessage.MessageType;

import java.util.List;
import java.util.Map;

/**
 * Unified chat storage interface that combines:
 * - Message persistence
 * - Token-based memory management
 * - Simple storage operations
 * 
 * Replaces ChatMemory, ChatMemoryStore, ChatHistoryRepository and adapters.
 */
public interface ChatStore {
    
    /**
     * Add a simple text message to the chat.
     */
    void add(String chatId, String content, MessageType type);
    
    /**
     * Add a message with properties to the chat.
     */
    void add(String chatId, Map<String, String> properties, MessageType type);
    
    /**
     * Add a ChatMessage object to the chat.
     */
    void add(ChatMessage message);
    
    /**
     * Update an existing message.
     */
    void update(ChatMessage message);
    
    /**
     * Get recent messages within token limit.
     */
    List<ChatMessage> getRecentWithinTokens(String chatId, int maxTokens);
    
    /**
     * Get recent messages with default token limit (4096).
     */
    List<ChatMessage> getRecent(String chatId);
    
    /**
     * Get recent messages with count limit.
     */
    List<ChatMessage> getRecent(String chatId, int limit);
    
    /**
     * Get all messages for a chat.
     */
    List<ChatMessage> getAll(String chatId);
    
    /**
     * Delete a specific message.
     */
    void delete(String messageId);
    
    /**
     * Delete all messages for a chat.
     */
    void deleteAll(String chatId);
    
    /**
     * Get total token count for a chat.
     */
    int getTotalTokens(String chatId);
    
    /**
     * Check if chat exists.
     */
    boolean chatExists(String chatId);
    
    /**
     * Get a message by ID.
     */
    ChatMessage getById(String messageId);
    
    /**
     * Prune old messages to stay within token limit.
     */
    void pruneToTokenLimit(String chatId, int maxTokens);
}