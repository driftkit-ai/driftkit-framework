package ai.driftkit.workflow.engine.annotations;

/**
 * Defines the behavior when a step reaches its invocation limit.
 * This is used to control what happens when a step has been executed
 * too many times within a single workflow execution.
 */
public enum OnInvocationsLimit {
    /**
     * Throw an error when the invocation limit is reached.
     * This will cause the workflow to fail with an exception.
     */
    ERROR,
    
    /**
     * Stop the workflow gracefully when the limit is reached.
     * The workflow will complete with the last successful result.
     */
    STOP,
    
    /**
     * Continue execution despite reaching the limit.
     * The step will continue to execute, but a warning will be logged.
     * Use with caution as this may lead to infinite loops.
     */
    CONTINUE
}