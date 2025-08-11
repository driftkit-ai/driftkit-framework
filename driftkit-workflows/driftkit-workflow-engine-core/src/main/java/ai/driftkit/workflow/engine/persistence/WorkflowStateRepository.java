package ai.driftkit.workflow.engine.persistence;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for persisting and retrieving workflow instance state.
 * Implementations can provide various storage backends (in-memory, database, etc.).
 * 
 * <p>This interface supports both synchronous and asynchronous operations
 * to accommodate different storage technologies and performance requirements.</p>
 */
public interface WorkflowStateRepository {
    
    /**
     * Saves or updates a workflow instance.
     * 
     * @param instance The workflow instance to save
     * @throws PersistenceException if the save operation fails
     */
    void save(WorkflowInstance instance);
    
    /**
     * Asynchronously saves or updates a workflow instance.
     * 
     * @param instance The workflow instance to save
     * @return A future that completes when the save is done
     */
    default CompletableFuture<Void> saveAsync(WorkflowInstance instance) {
        return CompletableFuture.runAsync(() -> save(instance));
    }
    
    /**
     * Loads a workflow instance by its ID.
     * 
     * @param instanceId The unique instance ID
     * @return The workflow instance if found, empty otherwise
     */
    Optional<WorkflowInstance> load(String instanceId);
    
    /**
     * Asynchronously loads a workflow instance by its ID.
     * 
     * @param instanceId The unique instance ID
     * @return A future containing the workflow instance if found
     */
    default CompletableFuture<Optional<WorkflowInstance>> loadAsync(String instanceId) {
        return CompletableFuture.supplyAsync(() -> load(instanceId));
    }
    
    /**
     * Deletes a workflow instance.
     * 
     * @param instanceId The instance ID to delete
     * @return true if the instance was deleted, false if it didn't exist
     */
    boolean delete(String instanceId);
    
    /**
     * Finds all workflow instances with the given status.
     * 
     * @param status The workflow status to filter by
     * @return List of matching workflow instances
     */
    List<WorkflowInstance> findByStatus(WorkflowInstance.WorkflowStatus status);
    
    /**
     * Finds all workflow instances for a specific workflow definition.
     * 
     * @param workflowId The workflow definition ID
     * @return List of matching workflow instances
     */
    List<WorkflowInstance> findByWorkflowId(String workflowId);
    
    /**
     * Finds workflow instances by workflow ID and status.
     * 
     * @param workflowId The workflow definition ID
     * @param status The workflow status
     * @return List of matching workflow instances
     */
    List<WorkflowInstance> findByWorkflowIdAndStatus(String workflowId, 
                                                     WorkflowInstance.WorkflowStatus status);
    
    /**
     * Counts workflow instances by status.
     * 
     * @param status The workflow status
     * @return The count of instances with the given status
     */
    long countByStatus(WorkflowInstance.WorkflowStatus status);
    
    /**
     * Deletes all completed workflow instances older than the specified age.
     * Useful for cleanup operations.
     * 
     * @param ageInDays Age threshold in days
     * @return The number of instances deleted
     */
    int deleteCompletedOlderThan(int ageInDays);
    
    /**
     * Checks if a workflow instance exists.
     * 
     * @param instanceId The instance ID to check
     * @return true if the instance exists, false otherwise
     */
    default boolean exists(String instanceId) {
        return load(instanceId).isPresent();
    }
    
    /**
     * Exception thrown when persistence operations fail.
     */
    class PersistenceException extends RuntimeException {
        public PersistenceException(String message) {
            super(message);
        }
        
        public PersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}