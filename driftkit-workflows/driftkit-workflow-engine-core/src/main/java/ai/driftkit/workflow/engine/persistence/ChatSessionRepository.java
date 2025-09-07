package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.domain.ChatSession;
import ai.driftkit.workflow.engine.domain.PageRequest;
import ai.driftkit.workflow.engine.domain.PageResult;

import java.util.Optional;

/**
 * Repository interface for managing chat sessions.
 * Provides abstraction over storage implementation without Spring dependencies.
 */
public interface ChatSessionRepository {
    
    /**
     * Save or update a chat session.
     * 
     * @param session The chat session to save
     * @return The saved chat session
     */
    ChatSession save(ChatSession session);
    
    /**
     * Find a chat session by ID.
     * 
     * @param chatId The chat ID
     * @return Optional containing the chat session if found
     */
    Optional<ChatSession> findById(String chatId);
    
    /**
     * Find all chat sessions for a user.
     * 
     * @param userId The user ID
     * @param pageRequest Pagination information
     * @return Page of chat sessions
     */
    PageResult<ChatSession> findByUserId(String userId, PageRequest pageRequest);
    
    /**
     * Find active (non-archived) chat sessions for a user.
     * 
     * @param userId The user ID
     * @param pageRequest Pagination information
     * @return Page of active chat sessions
     */
    PageResult<ChatSession> findActiveByUserId(String userId, PageRequest pageRequest);
    
    /**
     * Delete a chat session.
     * 
     * @param chatId The chat ID to delete
     */
    void deleteById(String chatId);
    
    /**
     * Check if a chat session exists.
     * 
     * @param chatId The chat ID
     * @return true if exists, false otherwise
     */
    boolean existsById(String chatId);
}