package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.persistence.WorkflowInstance;

/**
 * Listener interface for workflow execution events.
 * Implementations can react to various workflow lifecycle events.
 */
public interface WorkflowExecutionListener {
    
    /**
     * Called when a workflow starts execution.
     * 
     * @param instance The workflow instance that started
     */
    default void onWorkflowStarted(WorkflowInstance instance) {
        // Default no-op implementation
    }
    
    /**
     * Called when a workflow completes successfully.
     * 
     * @param instance The completed workflow instance
     */
    default void onWorkflowCompleted(WorkflowInstance instance) {
        // Default no-op implementation
    }
    
    /**
     * Called when a workflow fails with an error.
     * 
     * @param instance The failed workflow instance
     * @param error The error that caused the failure
     */
    default void onWorkflowFailed(WorkflowInstance instance, Throwable error) {
        // Default no-op implementation
    }
    
    /**
     * Called when a suspended workflow is resumed.
     * 
     * @param instance The resumed workflow instance
     */
    default void onWorkflowResumed(WorkflowInstance instance) {
        // Default no-op implementation
    }
    
    /**
     * Called when a workflow is cancelled.
     * 
     * @param instance The cancelled workflow instance
     */
    default void onWorkflowCancelled(WorkflowInstance instance) {
        // Default no-op implementation
    }
    
    /**
     * Called before a step executes.
     * 
     * @param instance The workflow instance
     * @param stepId The ID of the step about to execute
     */
    default void onStepStarted(WorkflowInstance instance, String stepId) {
        // Default no-op implementation
    }
    
    /**
     * Called after a step completes.
     * 
     * @param instance The workflow instance
     * @param stepId The ID of the completed step
     * @param result The step result
     */
    default void onStepCompleted(WorkflowInstance instance, String stepId, StepResult<?> result) {
        // Default no-op implementation
    }
    
    /**
     * Called when a step fails.
     * 
     * @param instance The workflow instance
     * @param stepId The ID of the failed step
     * @param error The error that occurred
     */
    default void onStepFailed(WorkflowInstance instance, String stepId, Throwable error) {
        // Default no-op implementation
    }
}