package ai.driftkit.workflow.test.core;

/**
 * Exception thrown when workflow execution fails during testing.
 */
public class WorkflowExecutionException extends WorkflowTestException {
    
    public WorkflowExecutionException(String message) {
        super(message);
    }
    
    public WorkflowExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}