package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.chat.ChatDomain.ChatResponse;

import java.util.Optional;

/**
 * Repository interface for managing async chat responses.
 * Provides abstraction over storage implementation for distributed systems.
 */
public interface AsyncResponseRepository {
    
    /**
     * Save or update an async response.
     * 
     * @param response The chat response to save
     * @return The saved response
     */
    ChatResponse save(ChatResponse response);
    
    /**
     * Find an async response by ID.
     * 
     * @param responseId The response ID
     * @return Optional containing the response if found
     */
    Optional<ChatResponse> findById(String responseId);
    
    /**
     * Delete an async response.
     * 
     * @param responseId The response ID to delete
     */
    void deleteById(String responseId);
    
    /**
     * Check if an async response exists.
     * 
     * @param responseId The response ID
     * @return true if exists, false otherwise
     */
    boolean existsById(String responseId);
    
    /**
     * Delete all responses older than the given timestamp.
     * Useful for cleanup of old async responses.
     * 
     * @param timestampMillis The timestamp in milliseconds
     * @return Number of deleted responses
     */
    int deleteOlderThan(long timestampMillis);
}