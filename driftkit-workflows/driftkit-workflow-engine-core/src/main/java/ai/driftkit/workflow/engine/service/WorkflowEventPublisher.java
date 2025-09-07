package ai.driftkit.workflow.engine.service;

/**
 * Interface for publishing workflow events.
 * This allows the core service to notify about workflow state changes
 * without depending on specific event infrastructure (e.g., WebSocket).
 */
public interface WorkflowEventPublisher {
    
    /**
     * Publish workflow started event.
     * 
     * @param runId The workflow run ID
     * @param workflowId The workflow ID
     */
    void publishWorkflowStarted(String runId, String workflowId);
    
    /**
     * Publish workflow resumed event.
     * 
     * @param runId The workflow run ID
     * @param workflowId The workflow ID
     */
    void publishWorkflowResumed(String runId, String workflowId);
    
    /**
     * Publish workflow completed event.
     * 
     * @param runId The workflow run ID
     * @param workflowId The workflow ID
     * @param result The workflow result
     */
    void publishWorkflowCompleted(String runId, String workflowId, Object result);
    
    /**
     * Publish workflow failed event.
     * 
     * @param runId The workflow run ID
     * @param workflowId The workflow ID
     * @param error The error that caused the failure
     */
    void publishWorkflowFailed(String runId, String workflowId, Throwable error);
    
    /**
     * Publish workflow suspended event.
     * 
     * @param runId The workflow run ID
     * @param workflowId The workflow ID
     * @param suspensionReason The reason for suspension
     */
    void publishWorkflowSuspended(String runId, String workflowId, String suspensionReason);
    
    /**
     * Publish async step progress event.
     * 
     * @param runId The workflow run ID
     * @param stepId The step ID
     * @param percentComplete The completion percentage
     * @param message Progress message
     */
    void publishAsyncProgress(String runId, String stepId, int percentComplete, String message);
}