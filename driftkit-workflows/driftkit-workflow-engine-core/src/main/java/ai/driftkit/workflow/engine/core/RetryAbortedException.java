package ai.driftkit.workflow.engine.core;

/**
 * Exception thrown when retry is aborted due to specific exception types.
 * This is different from RetryExhaustedException which is thrown when all retry attempts are used up.
 */
public class RetryAbortedException extends Exception {
    
    /**
     * Creates a new retry aborted exception.
     * 
     * @param message The error message
     */
    public RetryAbortedException(String message) {
        super(message);
    }
    
    /**
     * Creates a new retry aborted exception with a cause.
     * 
     * @param message The error message
     * @param cause The underlying cause
     */
    public RetryAbortedException(String message, Throwable cause) {
        super(message, cause);
    }
}