package ai.driftkit.workflow.engine.spring.repository;

import ai.driftkit.common.domain.chat.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Repository interface for managing chat message history.
 * Provides abstraction over storage implementation.
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
     * @param pageable Pagination information
     * @param includeContext Whether to include context messages
     * @return Page of chat messages
     */
    Page<ChatMessage> findByChatId(String chatId, Pageable pageable, boolean includeContext);
    
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
}