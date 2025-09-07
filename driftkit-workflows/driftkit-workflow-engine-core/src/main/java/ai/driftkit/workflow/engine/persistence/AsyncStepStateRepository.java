package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.domain.AsyncStepState;

import java.util.Optional;

/**
 * Repository interface for managing asynchronous step states.
 * This replaces the incorrect storage of ChatResponse for async operations.
 * Async step states are temporary and should be stored separately from workflow instances.
 */
public interface AsyncStepStateRepository {
    
    /**
     * Save or update an async step state.
     * 
     * @param state The async step state to save
     * @return The saved state
     */
    AsyncStepState save(AsyncStepState state);
    
    /**
     * Find an async step state by message ID.
     * 
     * @param messageId The unique message ID for this async execution
     * @return Optional containing the state if found
     */
    Optional<AsyncStepState> findByMessageId(String messageId);
    
    /**
     * Delete an async step state.
     * 
     * @param messageId The message ID to delete
     */
    void deleteByMessageId(String messageId);
    
    /**
     * Check if an async step state exists.
     * 
     * @param messageId The message ID
     * @return true if exists, false otherwise
     */
    boolean existsByMessageId(String messageId);
    
    /**
     * Delete all states older than the given timestamp.
     * Useful for cleanup of old async states.
     * 
     * @param timestampMillis The timestamp in milliseconds
     * @return Number of deleted states
     */
    int deleteOlderThan(long timestampMillis);
    
    /**
     * Update the progress of an async step state.
     * 
     * @param messageId The message ID
     * @param percentComplete The completion percentage (0-100)
     * @param statusMessage The status message
     * @return true if updated, false if not found
     */
    boolean updateProgress(String messageId, int percentComplete, String statusMessage);
}