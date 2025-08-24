package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.domain.RetryContext;
import ai.driftkit.workflow.engine.core.CircuitBreaker;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for persisting retry state to enable recovery after failures.
 * Implementations can use various backends like databases, caches, or files.
 */
public interface RetryStateStore {
    
    /**
     * Saves retry context for a workflow step.
     * 
     * @param workflowId The workflow instance ID
     * @param stepId The step ID
     * @param context The retry context to save
     * @return Future that completes when save is done
     */
    CompletableFuture<Void> saveRetryContext(String workflowId, String stepId, RetryContext context);
    
    /**
     * Loads retry context for a workflow step.
     * 
     * @param workflowId The workflow instance ID
     * @param stepId The step ID
     * @return Optional containing the retry context if found
     */
    CompletableFuture<Optional<RetryContext>> loadRetryContext(String workflowId, String stepId);
    
    /**
     * Deletes retry context for a workflow step.
     * 
     * @param workflowId The workflow instance ID
     * @param stepId The step ID
     * @return Future that completes when delete is done
     */
    CompletableFuture<Void> deleteRetryContext(String workflowId, String stepId);
    
    /**
     * Saves circuit breaker state for a workflow.
     * 
     * @param workflowId The workflow instance ID
     * @param stepId The step ID
     * @param state The circuit breaker state
     * @return Future that completes when save is done
     */
    CompletableFuture<Void> saveCircuitBreakerState(String workflowId, String stepId, 
                                                     CircuitBreaker.CircuitStateSnapshot state);
    
    /**
     * Loads circuit breaker state for a workflow.
     * 
     * @param workflowId The workflow instance ID
     * @param stepId The step ID
     * @return Optional containing the circuit breaker state if found
     */
    CompletableFuture<Optional<CircuitBreaker.CircuitStateSnapshot>> loadCircuitBreakerState(String workflowId, 
                                                                                             String stepId);
    
    /**
     * Deletes all retry state for a workflow.
     * 
     * @param workflowId The workflow instance ID
     * @return Future that completes when delete is done
     */
    CompletableFuture<Void> deleteWorkflowState(String workflowId);
}