package ai.driftkit.workflow.engine.spring.repository;

import ai.driftkit.workflow.engine.domain.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Repository interface for managing chat sessions.
 * Provides abstraction over storage implementation.
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
     * @param pageable Pagination information
     * @return Page of chat sessions
     */
    Page<ChatSession> findByUserId(String userId, Pageable pageable);
    
    /**
     * Find active (non-archived) chat sessions for a user.
     * 
     * @param userId The user ID
     * @param pageable Pagination information
     * @return Page of active chat sessions
     */
    Page<ChatSession> findActiveByUserId(String userId, Pageable pageable);
    
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