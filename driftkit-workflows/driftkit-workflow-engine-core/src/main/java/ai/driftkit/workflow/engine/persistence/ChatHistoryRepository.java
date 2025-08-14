package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.chat.ChatDomain.ChatMessage;
import ai.driftkit.workflow.engine.domain.PageRequest;
import ai.driftkit.workflow.engine.domain.PageResult;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing chat message history.
 * Provides abstraction over storage implementation without Spring dependencies.
 */
public interface ChatHistoryRepository {
    
    /**
     * Add a message to chat history.
     * 
     * @param chatId The chat ID
     * @param message The message to add
     */
    void addMessage(String chatId, ChatMessage message);
    
    /**
     * Add multiple messages to chat history.
     * 
     * @param chatId The chat ID
     * @param messages The messages to add
     */
    void addMessages(String chatId, List<ChatMessage> messages);
    
    /**
     * Get chat history for a specific chat.
     * 
     * @param chatId The chat ID
     * @param pageRequest Pagination information
     * @param includeContext Whether to include context messages
     * @return Page of chat messages
     */
    PageResult<ChatMessage> findByChatId(String chatId, PageRequest pageRequest, boolean includeContext);
    
    /**
     * Get all messages for a chat.
     * 
     * @param chatId The chat ID
     * @return List of all messages
     */
    List<ChatMessage> findAllByChatId(String chatId);
    
    /**
     * Get recent messages for a chat.
     * 
     * @param chatId The chat ID
     * @param limit Maximum number of messages to return
     * @return List of recent messages
     */
    List<ChatMessage> findRecentByChatId(String chatId, int limit);
    
    /**
     * Delete all messages for a chat.
     * 
     * @param chatId The chat ID
     */
    void deleteByChatId(String chatId);
    
    /**
     * Get message count for a chat.
     * 
     * @param chatId The chat ID
     * @return Number of messages
     */
    long countByChatId(String chatId);
    
    /**
     * Find a specific message by its ID.
     * 
     * @param messageId The message ID
     * @return The message if found
     */
    Optional<ChatMessage> findById(String messageId);
}