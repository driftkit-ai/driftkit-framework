package ai.driftkit.workflow.test.assertions;

import ai.driftkit.workflow.test.core.ExecutionTracker;
import ai.driftkit.workflow.test.core.WorkflowTestException;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Main assertion engine for workflow tests.
 * Provides fluent assertions for workflows and steps.
 */
@Slf4j
public class AssertionEngine {
    
    private final ExecutionTracker executionTracker;
    
    /**
     * Creates an assertion engine with the given execution tracker.
     * 
     * @param executionTracker the execution tracker
     */
    public AssertionEngine(ExecutionTracker executionTracker) {
        this.executionTracker = Objects.requireNonNull(executionTracker, "executionTracker cannot be null");
    }
    
    /**
     * Creates assertions for a specific step.
     * 
     * @param workflowId the workflow ID
     * @param stepId the step ID
     * @return step assertions
     */
    public StepAssertions assertStep(String workflowId, String stepId) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");
        
        return new StepAssertions(workflowId, stepId, executionTracker);
    }
    
    // Note: Workflow and execution assertions are now handled by EnhancedWorkflowAssertions
    // and can be accessed through the static factory methods
}