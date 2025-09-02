package ai.driftkit.workflow.engine.core;

/**
 * Listener for internal step executions within composite steps like branches.
 * This allows external components (like test frameworks) to track and potentially
 * intercept internal step executions.
 */
public interface InternalStepListener {
    
    /**
     * Called before an internal step is executed.
     * 
     * @param stepId the ID of the step being executed
     * @param input the input to the step
     * @param context the workflow context
     */
    void beforeInternalStep(String stepId, Object input, WorkflowContext context);
    
    /**
     * Called after an internal step is executed successfully.
     * 
     * @param stepId the ID of the step that was executed
     * @param result the result of the step execution
     * @param context the workflow context
     */
    void afterInternalStep(String stepId, StepResult<?> result, WorkflowContext context);
    
    /**
     * Called if an internal step execution fails.
     * 
     * @param stepId the ID of the step that failed
     * @param error the error that occurred
     * @param context the workflow context
     */
    void onInternalStepError(String stepId, Exception error, WorkflowContext context);
    
    /**
     * Allows the listener to provide an alternative result for the step.
     * This is used for mocking in test frameworks.
     * 
     * @param stepId the ID of the step
     * @param input the input to the step
     * @param context the workflow context
     * @return an optional alternative result, or empty to proceed with normal execution
     */
    default java.util.Optional<StepResult<?>> interceptInternalStep(String stepId, Object input, WorkflowContext context) {
        return java.util.Optional.empty();
    }
}