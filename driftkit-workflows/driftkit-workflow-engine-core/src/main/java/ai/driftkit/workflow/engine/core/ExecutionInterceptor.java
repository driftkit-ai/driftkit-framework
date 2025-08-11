package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;

/**
 * Interceptor interface for workflow step execution.
 * Allows plugins to hook into the execution lifecycle of workflow steps.
 */
public interface ExecutionInterceptor {
    
    /**
     * Called before a step is executed.
     * 
     * @param instance The workflow instance
     * @param step The step about to be executed
     * @param input The input that will be passed to the step
     */
    void beforeStep(WorkflowInstance instance, StepNode step, Object input);
    
    /**
     * Called after a step has been executed successfully.
     * 
     * @param instance The workflow instance
     * @param step The step that was executed
     * @param result The result returned by the step
     */
    void afterStep(WorkflowInstance instance, StepNode step, StepResult<?> result);
    
    /**
     * Called when a step execution fails with an error.
     * 
     * @param instance The workflow instance
     * @param step The step that failed
     * @param error The error that occurred
     */
    void onStepError(WorkflowInstance instance, StepNode step, Exception error);
}