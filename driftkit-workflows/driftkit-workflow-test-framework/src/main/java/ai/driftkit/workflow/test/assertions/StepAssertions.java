package ai.driftkit.workflow.test.assertions;

import ai.driftkit.workflow.test.core.ExecutionTracker;
import ai.driftkit.workflow.engine.core.StepResult;
import org.assertj.core.api.Assertions;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Assertions for workflow steps.
 */
@Slf4j
public class StepAssertions {
    
    private final String workflowId;
    private final String stepId;
    private final ExecutionTracker tracker;
    
    public StepAssertions(String workflowId, String stepId, ExecutionTracker tracker) {
        this.workflowId = workflowId;
        this.stepId = stepId;
        this.tracker = tracker;
    }
    
    /**
     * Assert that the step was executed.
     * 
     * @return this for chaining
     */
    public StepAssertions wasExecuted() {
        boolean executed = tracker.wasExecuted(workflowId, stepId);
        Assertions.assertThat(executed)
            .as("Step %s.%s should have been executed", workflowId, stepId)
            .isTrue();
        return this;
    }
    
    /**
     * Assert that the step was not executed.
     * 
     * @return this for chaining
     */
    public StepAssertions wasNotExecuted() {
        boolean executed = tracker.wasExecuted(workflowId, stepId);
        Assertions.assertThat(executed)
            .as("Step %s.%s should not have been executed", workflowId, stepId)
            .isFalse();
        return this;
    }
    
    /**
     * Assert execution count.
     * 
     * @param expectedCount the expected count
     * @return this for chaining
     */
    public StepAssertions wasExecutedTimes(int expectedCount) {
        int actualCount = tracker.getExecutionCount(workflowId, stepId);
        Assertions.assertThat(actualCount)
            .as("Step %s.%s execution count", workflowId, stepId)
            .isEqualTo(expectedCount);
        return this;
    }
    
    /**
     * Assert that the step succeeded (no Fail result).
     * 
     * @return this for chaining
     */
    public StepAssertions succeeded() {
        List<ExecutionTracker.ExecutionRecord> executions = getStepExecutions();
        
        boolean anyFailed = executions.stream()
            .filter(record -> record.getStatus() == ExecutionTracker.ExecutionStatus.COMPLETED)
            .anyMatch(record -> record.getData() instanceof StepResult.Fail);
            
        Assertions.assertThat(anyFailed)
            .as("Step %s.%s should have succeeded", workflowId, stepId)
            .isFalse();
        return this;
    }
    
    /**
     * Assert that the step failed.
     * 
     * @return this for chaining
     */
    public StepAssertions failed() {
        List<ExecutionTracker.ExecutionRecord> executions = getStepExecutions();
        
        boolean anyFailed = executions.stream()
            .filter(record -> record.getStatus() == ExecutionTracker.ExecutionStatus.COMPLETED)
            .anyMatch(record -> record.getData() instanceof StepResult.Fail);
            
        Assertions.assertThat(anyFailed)
            .as("Step %s.%s should have failed", workflowId, stepId)
            .isTrue();
        return this;
    }
    
    /**
     * Assert that the step completed with specific data.
     * 
     * @param expectedData the expected data
     * @return this for chaining
     */
    public StepAssertions completedWith(Object expectedData) {
        List<ExecutionTracker.ExecutionRecord> completions = getStepExecutions().stream()
            .filter(record -> record.getStatus() == ExecutionTracker.ExecutionStatus.COMPLETED)
            .collect(Collectors.toList());
            
        Assertions.assertThat(completions)
            .as("Step %s.%s should have completed", workflowId, stepId)
            .isNotEmpty();
            
        boolean foundMatch = completions.stream()
            .anyMatch(record -> {
                if (record.getData() instanceof StepResult.Continue<?> cont) {
                    return expectedData.equals(cont.data());
                } else if (record.getData() instanceof StepResult.Finish<?> finish) {
                    return expectedData.equals(finish.result());
                }
                return false;
            });
            
        Assertions.assertThat(foundMatch)
            .as("Step %s.%s should have completed with data: %s", workflowId, stepId, expectedData)
            .isTrue();
            
        return this;
    }
    
    private List<ExecutionTracker.ExecutionRecord> getStepExecutions() {
        return tracker.getHistory().getOrderedExecutions().stream()
            .filter(record -> {
                if (!workflowId.equals(record.getWorkflowId())) {
                    return false;
                }
                
                // First try exact match
                if (stepId.equals(record.getStepId())) {
                    return true;
                }
                
                // Then check if the recorded stepId contains our stepId (for branch-prefixed steps)
                String recordedStepId = record.getStepId();
                if (recordedStepId != null && recordedStepId.contains(stepId)) {
                    return true;
                }
                
                return false;
            })
            .collect(Collectors.toList());
    }
}