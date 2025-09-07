package ai.driftkit.workflow.engine.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowExceptionTest {
    
    @Test
    @DisplayName("Should create exception with message only")
    void testCreateWithMessage() {
        String message = "Workflow failed";
        WorkflowException ex = new WorkflowException(message);
        
        assertEquals(message, ex.getMessage());
        assertEquals("WORKFLOW_ERROR", ex.getCode());
        assertNull(ex.getCause());
    }
    
    @Test
    @DisplayName("Should create exception with message and code")
    void testCreateWithMessageAndCode() {
        String message = "Step execution failed";
        String code = "STEP_FAILED";
        WorkflowException ex = new WorkflowException(message, code);
        
        assertEquals(message, ex.getMessage());
        assertEquals(code, ex.getCode());
        assertNull(ex.getCause());
    }
    
    @Test
    @DisplayName("Should create exception with message and cause")
    void testCreateWithMessageAndCause() {
        String message = "Database error";
        Exception cause = new RuntimeException("Connection failed");
        WorkflowException ex = new WorkflowException(message, cause);
        
        assertEquals(message, ex.getMessage());
        assertEquals("WORKFLOW_ERROR", ex.getCode());
        assertEquals(cause, ex.getCause());
    }
    
    @Test
    @DisplayName("Should create exception with all parameters")
    void testCreateWithAllParameters() {
        String message = "External service error";
        String code = "EXTERNAL_ERROR";
        Exception cause = new IllegalStateException("Service unavailable");
        WorkflowException ex = new WorkflowException(message, code, cause);
        
        assertEquals(message, ex.getMessage());
        assertEquals(code, ex.getCode());
        assertEquals(cause, ex.getCause());
    }
}