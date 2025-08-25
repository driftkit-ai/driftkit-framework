package ai.driftkit.workflow.test.core;

/**
 * Base exception for workflow test framework errors.
 */
public class WorkflowTestException extends RuntimeException {
    
    /**
     * Creates a new workflow test exception.
     * 
     * @param message the error message
     */
    public WorkflowTestException(String message) {
        super(message);
    }
    
    /**
     * Creates a new workflow test exception with a cause.
     * 
     * @param message the error message
     * @param cause the underlying cause
     */
    public WorkflowTestException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Creates a new workflow test exception with a cause.
     * 
     * @param cause the underlying cause
     */
    public WorkflowTestException(Throwable cause) {
        super(cause);
    }
}