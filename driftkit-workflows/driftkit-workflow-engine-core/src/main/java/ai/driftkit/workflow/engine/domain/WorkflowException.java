package ai.driftkit.workflow.engine.domain;

/**
 * Exception thrown during workflow execution.
 * Contains error code for categorization and handling.
 */
public class WorkflowException extends RuntimeException {
    private final String code;
    
    public WorkflowException(String message) {
        this(message, "WORKFLOW_ERROR", null);
    }
    
    public WorkflowException(String message, String code) {
        this(message, code, null);
    }
    
    public WorkflowException(String message, Throwable cause) {
        this(message, "WORKFLOW_ERROR", cause);
    }
    
    public WorkflowException(String message, String code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
    
    public String getCode() {
        return code;
    }
}