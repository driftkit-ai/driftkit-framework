package ai.driftkit.workflow.test.assertions;

import ai.driftkit.workflow.test.core.ExecutionTracker;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.core.WorkflowEngine.WorkflowExecution;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance.WorkflowStatus;
import ai.driftkit.workflow.engine.domain.WorkflowEvent;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Enhanced AssertJ style assertions for workflow executions.
 * Provides fluent assertion API for workflow testing.
 */
public class EnhancedWorkflowAssertions extends AbstractAssert<EnhancedWorkflowAssertions, WorkflowExecution<?>> {
    
    private final ExecutionTracker executionTracker;
    
    public EnhancedWorkflowAssertions(WorkflowExecution<?> actual, ExecutionTracker executionTracker) {
        super(actual, EnhancedWorkflowAssertions.class);
        this.executionTracker = Objects.requireNonNull(executionTracker, "executionTracker cannot be null");
    }
    
    /**
     * Entry point for workflow assertions.
     */
    public static EnhancedWorkflowAssertions assertThat(WorkflowExecution<?> execution, ExecutionTracker tracker) {
        return new EnhancedWorkflowAssertions(execution, tracker);
    }
    
    /**
     * Asserts that the workflow has executed specific steps in any order.
     * 
     * @param expectedSteps the expected step IDs
     * @return this assertion
     */
    public EnhancedWorkflowAssertions hasExecutedSteps(String... expectedSteps) {
        isNotNull();
        
        List<String> executedSteps = executionTracker.getExecutedSteps(actual.getWorkflowId());
        
        Assertions.assertThat(executedSteps)
            .as("Executed steps for workflow '%s'", actual.getWorkflowId())
            .contains(expectedSteps);
        
        return this;
    }
    
    /**
     * Asserts that the workflow has executed specific steps in exact order.
     * 
     * @param expectedSteps the expected step IDs in order
     * @return this assertion
     */
    public EnhancedWorkflowAssertions hasExecutedStepsInOrder(String... expectedSteps) {
        isNotNull();
        
        List<String> executedSteps = executionTracker.getExecutedSteps(actual.getWorkflowId());
        
        Assertions.assertThat(executedSteps)
            .as("Executed steps for workflow '%s'", actual.getWorkflowId())
            .containsExactly(expectedSteps);
        
        return this;
    }
    
    /**
     * Asserts that the workflow has no failures.
     * 
     * @return this assertion
     */
    public EnhancedWorkflowAssertions hasNoFailures() {
        isNotNull();
        
        List<ExecutionTracker.ExecutionRecord> failures = executionTracker.getHistory()
            .getRecords().stream()
            .filter(r -> r.getWorkflowId().equals(actual.getWorkflowId()))
            .filter(r -> r.getStatus() == ExecutionTracker.ExecutionStatus.FAILED)
            .collect(Collectors.toList());
        
        Assertions.assertThat(failures)
            .as("Failed executions for workflow '%s'", actual.getWorkflowId())
            .isEmpty();
        
        return this;
    }
    
    /**
     * Asserts that the workflow completed within a specific duration.
     * 
     * @param duration the maximum expected duration
     * @return this assertion
     */
    public EnhancedWorkflowAssertions completedWithin(Duration duration) {
        isNotNull();
        Objects.requireNonNull(duration, "duration cannot be null");
        
        // Get workflow start and end times
        List<ExecutionTracker.ExecutionRecord> workflowRecords = executionTracker.getHistory()
            .getRecords().stream()
            .filter(r -> r.getWorkflowId().equals(actual.getWorkflowId()))
            .filter(r -> r.getType() == ExecutionTracker.RecordType.WORKFLOW)
            .collect(Collectors.toList());
        
        if (workflowRecords.size() < 2) {
            failWithMessage("Cannot determine workflow duration - insufficient records");
        }
        
        long startTime = workflowRecords.stream()
            .filter(r -> r.getStatus() == ExecutionTracker.ExecutionStatus.STARTED)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No workflow start record found"))
            .getTimestamp();
        
        long endTime = workflowRecords.stream()
            .filter(r -> r.getStatus() == ExecutionTracker.ExecutionStatus.COMPLETED || 
                        r.getStatus() == ExecutionTracker.ExecutionStatus.FAILED)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No workflow completion record found"))
            .getTimestamp();
        
        Duration actualDuration = Duration.ofMillis(endTime - startTime);
        
        if (actualDuration.compareTo(duration) > 0) {
            failWithMessage("Expected workflow '%s' to complete within %s but took %s",
                actual.getWorkflowId(), duration, actualDuration);
        }
        
        return this;
    }
    
    /**
     * Asserts that the workflow produced a result matching the predicate.
     * 
     * @param predicate the result predicate
     * @return this assertion
     */
    public EnhancedWorkflowAssertions producedResult(Predicate<Object> predicate) {
        isNotNull();
        Objects.requireNonNull(predicate, "predicate cannot be null");
        
        Object result = getWorkflowResult();
        
        if (!predicate.test(result)) {
            failWithMessage("Expected workflow result to match predicate but it didn't. Actual result: %s", result);
        }
        
        return this;
    }
    
    /**
     * Asserts specific conditions on the workflow result.
     * 
     * @param assertions consumer for result assertions
     * @return this assertion
     */
    public EnhancedWorkflowAssertions hasResultSatisfying(Consumer<Object> assertions) {
        isNotNull();
        Objects.requireNonNull(assertions, "assertions cannot be null");
        
        Object result = getWorkflowResult();
        assertions.accept(result);
        
        return this;
    }
    
    /**
     * Asserts that a specific step was executed.
     * 
     * @param stepId the step ID
     * @return step-specific assertions
     */
    public StepAssertions step(String stepId) {
        isNotNull();
        Objects.requireNonNull(stepId, "stepId cannot be null");
        
        return new StepAssertions(actual.getWorkflowId(), stepId, executionTracker);
    }
    
    /**
     * Asserts that the workflow is in a specific state.
     * 
     * @param expectedState the expected state
     * @return this assertion
     */
    public EnhancedWorkflowAssertions hasState(WorkflowStatus expectedState) {
        isNotNull();
        Objects.requireNonNull(expectedState, "expectedState cannot be null");
        
        // Get the current workflow state from the execution
        WorkflowStatus actualState = actual.getEngine().getWorkflowInstance(actual.getRunId())
            .map(WorkflowInstance::getStatus)
            .orElseThrow(() -> new AssertionError("No workflow instance found for run ID: " + actual.getRunId()));
        
        Assertions.assertThat(actualState)
            .as("Workflow state")
            .isEqualTo(expectedState);
        
        return this;
    }
    
    /**
     * Asserts that the workflow completed successfully.
     * 
     * @return this assertion
     */
    public EnhancedWorkflowAssertions isCompleted() {
        return hasState(WorkflowStatus.COMPLETED);
    }
    
    /**
     * Asserts that the workflow failed.
     * 
     * @return this assertion
     */
    public EnhancedWorkflowAssertions isFailed() {
        return hasState(WorkflowStatus.FAILED);
    }
    
    /**
     * Asserts that the workflow is suspended.
     * 
     * @return this assertion
     */
    public EnhancedWorkflowAssertions isSuspended() {
        return hasState(WorkflowStatus.SUSPENDED);
    }
    
    /**
     * Asserts that the workflow has specific attributes.
     * 
     * @param key the attribute key
     * @param value the expected value
     * @return this assertion
     */
    public EnhancedWorkflowAssertions hasAttribute(String key, Object value) {
        isNotNull();
        Objects.requireNonNull(key, "key cannot be null");
        
        Object actualValue = actual.getEngine().getWorkflowInstance(actual.getRunId())
            .map(instance -> instance.getContext().getContextValue(key, Object.class))
            .orElse(null);
        
        Assertions.assertThat(actualValue)
            .as("Workflow attribute '%s'", key)
            .isEqualTo(value);
        
        return this;
    }
    
    /**
     * Asserts that workflow events were recorded.
     * 
     * @return this assertion
     */
    public EnhancedWorkflowAssertions hasEmittedEvents() {
        isNotNull();
        
        List<WorkflowEvent> emittedEvents = executionTracker.getHistory()
            .getRecords().stream()
            .filter(r -> r.getWorkflowId().equals(actual.getWorkflowId()))
            .filter(r -> r.getData() instanceof WorkflowEvent)
            .map(r -> (WorkflowEvent) r.getData())
            .collect(Collectors.toList());
        
        Assertions.assertThat(emittedEvents)
            .as("Emitted events for workflow '%s'", actual.getWorkflowId())
            .isNotEmpty();
        
        return this;
    }
    
    private Object getWorkflowResult() {
        // Get the final result from execution records
        return executionTracker.getHistory()
            .getRecords().stream()
            .filter(r -> r.getWorkflowId().equals(actual.getWorkflowId()))
            .filter(r -> r.getType() == ExecutionTracker.RecordType.WORKFLOW)
            .filter(r -> r.getStatus() == ExecutionTracker.ExecutionStatus.COMPLETED)
            .findFirst()
            .map(ExecutionTracker.ExecutionRecord::getData)
            .orElseThrow(() -> new AssertionError("No workflow completion result found"));
    }
}