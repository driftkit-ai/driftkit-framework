package ai.driftkit.workflow.test.core;

import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Context information for a workflow step execution.
 * Immutable container for step execution details.
 */
@Getter
@RequiredArgsConstructor
public class StepContext {
    
    private final WorkflowInstance instance;
    private final StepNode step;
    private final Object input;
    
    /**
     * Gets the workflow ID.
     * 
     * @return the workflow ID
     */
    public String getWorkflowId() {
        return instance.getWorkflowId();
    }
    
    /**
     * Gets the workflow run ID.
     * 
     * @return the workflow run ID
     */
    public String getRunId() {
        return instance.getInstanceId();
    }
    
    /**
     * Gets the step ID.
     * 
     * @return the step ID
     */
    public String getStepId() {
        return step.id();
    }
    
    /**
     * Gets the input type of the step.
     * 
     * @return the input type or null if not available
     */
    public Class<?> getInputType() {
        return input != null ? input.getClass() : null;
    }
    
    /**
     * Creates a unique key for this step context.
     * 
     * @return unique key in format "workflowId.stepId"
     */
    public String createKey() {
        return getWorkflowId() + "." + getStepId();
    }
    
    @Override
    public String toString() {
        return "StepContext{" +
            "workflowId='" + getWorkflowId() + '\'' +
            ", runId='" + getRunId() + '\'' +
            ", stepId='" + getStepId() + '\'' +
            ", inputType=" + getInputType() +
            '}';
    }
}