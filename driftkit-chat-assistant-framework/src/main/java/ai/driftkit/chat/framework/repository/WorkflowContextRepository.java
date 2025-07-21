package ai.driftkit.chat.framework.repository;

import ai.driftkit.chat.framework.model.WorkflowContext;

import java.util.Optional;

/**
 * Repository interface for storing and retrieving workflow contexts.
 * Implementation should be provided by the consuming application.
 */
public interface WorkflowContextRepository {
    
    /**
     * Find a workflow context by its ID
     * @param contextId The context ID
     * @return Optional containing the context if found
     */
    Optional<WorkflowContext> findById(String contextId);
    
    /**
     * Save or update a workflow context
     * @param context The context to save
     * @return The saved context
     */
    WorkflowContext saveOrUpdate(WorkflowContext context);
    
    /**
     * Delete a workflow context by its ID
     * @param contextId The context ID to delete
     */
    void deleteById(String contextId);
}