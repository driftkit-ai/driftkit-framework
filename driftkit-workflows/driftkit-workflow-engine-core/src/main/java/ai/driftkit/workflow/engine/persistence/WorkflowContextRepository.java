package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.core.WorkflowContext;

import java.util.Optional;

/**
 * Repository interface for persisting and retrieving workflow contexts.
 * Implementations should handle the storage of workflow execution contexts
 * in a thread-safe manner.
 */
public interface WorkflowContextRepository {
    
    /**
     * Saves a workflow context.
     * 
     * @param context The workflow context to save
     * @return The saved workflow context
     */
    WorkflowContext save(WorkflowContext context);
    
    /**
     * Finds a workflow context by instance ID.
     * 
     * @param instanceId The instance ID to search for
     * @return Optional containing the context if found, empty otherwise
     */
    Optional<WorkflowContext> findByInstanceId(String instanceId);
    
    /**
     * Deletes a workflow context by instance ID.
     * 
     * @param instanceId The instance ID of the context to delete
     * @return true if deleted, false if not found
     */
    boolean deleteByInstanceId(String instanceId);
    
    /**
     * Checks if a context exists for the given instance ID.
     * 
     * @param instanceId The instance ID to check
     * @return true if exists, false otherwise
     */
    boolean existsByInstanceId(String instanceId);
}