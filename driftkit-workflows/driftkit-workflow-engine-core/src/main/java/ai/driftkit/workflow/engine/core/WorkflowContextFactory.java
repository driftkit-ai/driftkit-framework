package ai.driftkit.workflow.engine.core;

/**
 * Factory interface for creating WorkflowContext instances.
 * Allows customization of context creation, such as injecting additional services.
 */
public interface WorkflowContextFactory {
    
    /**
     * Creates a new WorkflowContext with the provided parameters.
     * 
     * @param runId The unique identifier for this workflow run
     * @param triggerData The initial data that triggered the workflow
     * @param instanceId The workflow instance ID (may be null)
     * @return A new WorkflowContext instance
     */
    WorkflowContext create(String runId, Object triggerData, String instanceId);
    
    /**
     * Default factory implementation that creates standard WorkflowContext instances.
     */
    WorkflowContextFactory DEFAULT = WorkflowContext::new;
}