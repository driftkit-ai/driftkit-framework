package ai.driftkit.workflow.test.assertions;

import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.domain.*;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.test.core.TestExecutionInterceptor;
import ai.driftkit.workflow.test.core.ExecutionTracker;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * AssertJ-style assertions for workflow testing.
 * Provides fluent assertions for workflow states, step executions, and results.
 */
@Slf4j
public class WorkflowTestAssertions {
    
    /**
     * Create assertions for a workflow instance.
     */
    public static WorkflowInstanceAssert assertThat(WorkflowInstance instance) {
        return new WorkflowInstanceAssert(instance);
    }
    
    /**
     * Create assertions for a step result.
     */
    public static StepResultAssert assertThat(StepResult<?> result) {
        return new StepResultAssert(result);
    }
    
    /**
     * Create assertions for execution history.
     */
    public static ExecutionTrackerAssert assertThat(ExecutionTracker.ExecutionHistory history) {
        return new ExecutionTrackerAssert(history);
    }
    
    /**
     * Create assertions for execution records.
     */
    public static ExecutionHistoryAssert assertThat(List<TestExecutionInterceptor.ExecutionRecord> history) {
        return new ExecutionHistoryAssert(history);
    }
    
    /**
     * Assertions for WorkflowInstance.
     */
    public static class WorkflowInstanceAssert extends AbstractAssert<WorkflowInstanceAssert, WorkflowInstance> {
        
        public WorkflowInstanceAssert(WorkflowInstance actual) {
            super(actual, WorkflowInstanceAssert.class);
        }
        
        public WorkflowInstanceAssert hasStatus(WorkflowInstance.WorkflowStatus expected) {
            isNotNull();
            if (actual.getStatus() != expected) {
                failWithMessage("Expected workflow status to be <%s> but was <%s>",
                    expected, actual.getStatus());
            }
            return this;
        }
        
        public WorkflowInstanceAssert isRunning() {
            return hasStatus(WorkflowInstance.WorkflowStatus.RUNNING);
        }
        
        public WorkflowInstanceAssert isSuspended() {
            return hasStatus(WorkflowInstance.WorkflowStatus.SUSPENDED);
        }
        
        public WorkflowInstanceAssert isCompleted() {
            return hasStatus(WorkflowInstance.WorkflowStatus.COMPLETED);
        }
        
        public WorkflowInstanceAssert isFailed() {
            return hasStatus(WorkflowInstance.WorkflowStatus.FAILED);
        }
        
        public WorkflowInstanceAssert hasInstanceId(String expectedInstanceId) {
            isNotNull();
            Assertions.assertThat(actual.getInstanceId())
                .as("Instance ID")
                .isEqualTo(expectedInstanceId);
            return this;
        }
        
        public WorkflowInstanceAssert hasWorkflowId(String expectedWorkflowId) {
            isNotNull();
            Assertions.assertThat(actual.getWorkflowId())
                .as("Workflow ID")
                .isEqualTo(expectedWorkflowId);
            return this;
        }
        
        public WorkflowInstanceAssert hasCurrentStep(String expectedStepId) {
            isNotNull();
            Assertions.assertThat(actual.getCurrentStepId())
                .as("Current step ID")
                .isEqualTo(expectedStepId);
            return this;
        }
        
        public WorkflowInstanceAssert hasNoError() {
            isNotNull();
            Assertions.assertThat(actual.getErrorInfo())
                .as("Workflow error")
                .isNull();
            return this;
        }
        
        public WorkflowInstanceAssert hasError(String expectedError) {
            isNotNull();
            Assertions.assertThat(actual.getErrorInfo())
                .as("Workflow error info")
                .isNotNull();
            Assertions.assertThat(actual.getErrorInfo().errorMessage())
                .as("Workflow error message")
                .contains(expectedError);
            return this;
        }
    }
    
    /**
     * Assertions for StepResult.
     */
    public static class StepResultAssert extends AbstractAssert<StepResultAssert, StepResult<?>> {
        
        public StepResultAssert(StepResult<?> actual) {
            super(actual, StepResultAssert.class);
        }
        
        public StepResultAssert isContinue() {
            isNotNull();
            isInstanceOf(StepResult.Continue.class);
            return this;
        }
        
        public StepResultAssert isSuspend() {
            isNotNull();
            isInstanceOf(StepResult.Suspend.class);
            return this;
        }
        
        public StepResultAssert isBranch() {
            isNotNull();
            isInstanceOf(StepResult.Branch.class);
            return this;
        }
        
        public StepResultAssert isFinish() {
            isNotNull();
            isInstanceOf(StepResult.Finish.class);
            return this;
        }
        
        public StepResultAssert isFail() {
            isNotNull();
            isInstanceOf(StepResult.Fail.class);
            return this;
        }
        
        public StepResultAssert isAsync() {
            isNotNull();
            isInstanceOf(StepResult.Async.class);
            return this;
        }
        
        @SuppressWarnings("unchecked")
        public <T> StepResultAssert hasData(T expectedData) {
            isNotNull();
            if (actual instanceof StepResult.Continue) {
                StepResult.Continue<T> cont = (StepResult.Continue<T>) actual;
                Assertions.assertThat(cont.data())
                    .as("Continue data")
                    .isEqualTo(expectedData);
            } else {
                failWithMessage("Expected Continue result but was <%s>", 
                    actual.getClass().getSimpleName());
            }
            return this;
        }
        
        @SuppressWarnings("unchecked")
        public <T> StepResultAssert hasResult(T expectedResult) {
            isNotNull();
            if (actual instanceof StepResult.Finish) {
                StepResult.Finish<T> finish = (StepResult.Finish<T>) actual;
                Assertions.assertThat(finish.result())
                    .as("Finish result")
                    .isEqualTo(expectedResult);
            } else {
                failWithMessage("Expected Finish result but was <%s>", 
                    actual.getClass().getSimpleName());
            }
            return this;
        }
        
        public StepResultAssert hasError(String expectedError) {
            isNotNull();
            if (actual instanceof StepResult.Fail) {
                StepResult.Fail<?> fail = (StepResult.Fail<?>) actual;
                String errorMessage = fail.error() instanceof Throwable 
                    ? ((Throwable) fail.error()).getMessage() 
                    : fail.error().toString();
                assertTrue(errorMessage != null && errorMessage.contains(expectedError),
                    "Expected error to contain '" + expectedError + "' but was: " + errorMessage);
            } else {
                failWithMessage("Expected Fail result but was <%s>", 
                    actual.getClass().getSimpleName());
            }
            return this;
        }
        
        public StepResultAssert hasBranchEvent(Class<?> expectedEventType) {
            isNotNull();
            if (actual instanceof StepResult.Branch) {
                StepResult.Branch<?> branch = (StepResult.Branch<?>) actual;
                Assertions.assertThat(branch.event())
                    .as("Branch event")
                    .isInstanceOf(expectedEventType);
            } else {
                failWithMessage("Expected Branch result but was <%s>", 
                    actual.getClass().getSimpleName());
            }
            return this;
        }
    }
    
    /**
     * Assertions for execution history.
     */
    public static class ExecutionHistoryAssert 
            extends AbstractAssert<ExecutionHistoryAssert, List<TestExecutionInterceptor.ExecutionRecord>> {
        
        public ExecutionHistoryAssert(List<TestExecutionInterceptor.ExecutionRecord> actual) {
            super(actual, ExecutionHistoryAssert.class);
        }
        
        public ExecutionHistoryAssert hasSize(int expectedSize) {
            isNotNull();
            Assertions.assertThat(actual)
                .as("Execution history size")
                .hasSize(expectedSize);
            return this;
        }
        
        public ExecutionHistoryAssert isEmpty() {
            isNotNull();
            Assertions.assertThat(actual)
                .as("Execution history")
                .isEmpty();
            return this;
        }
        
        public ExecutionHistoryAssert isNotEmpty() {
            isNotNull();
            Assertions.assertThat(actual)
                .as("Execution history")
                .isNotEmpty();
            return this;
        }
        
        public ExecutionHistoryAssert containsStep(String workflowId, String stepId) {
            isNotNull();
            boolean found = actual.stream()
                .anyMatch(record -> 
                    record.workflowId().equals(workflowId) && 
                    record.stepId().equals(stepId));
            
            if (!found) {
                failWithMessage("Expected execution history to contain step <%s.%s>",
                    workflowId, stepId);
            }
            return this;
        }
        
        public ExecutionHistoryAssert containsStepWithInput(String workflowId, 
                                                           String stepId, 
                                                           Object expectedInput) {
            isNotNull();
            boolean found = actual.stream()
                .anyMatch(record -> 
                    record.workflowId().equals(workflowId) && 
                    record.stepId().equals(stepId) &&
                    record.input().equals(expectedInput));
            
            if (!found) {
                failWithMessage("Expected execution history to contain step <%s.%s> with input <%s>",
                    workflowId, stepId, expectedInput);
            }
            return this;
        }
        
        public ExecutionHistoryAssert hasExecutionCount(String workflowId, 
                                                       String stepId, 
                                                       int expectedCount) {
            isNotNull();
            long count = actual.stream()
                .filter(record -> 
                    record.workflowId().equals(workflowId) && 
                    record.stepId().equals(stepId) &&
                    record.isStart())
                .count();
            
            if (count != expectedCount) {
                failWithMessage("Expected step <%s.%s> to be executed <%d> times but was <%d>",
                    workflowId, stepId, expectedCount, count);
            }
            return this;
        }
        
        public ExecutionHistoryAssert hasSuccessfulExecution(String workflowId, String stepId) {
            isNotNull();
            boolean found = actual.stream()
                .anyMatch(record -> 
                    record.workflowId().equals(workflowId) && 
                    record.stepId().equals(stepId) &&
                    !record.isStart() &&
                    record.result() != null &&
                    !(record.result() instanceof StepResult.Fail));
            
            if (!found) {
                failWithMessage("Expected successful execution of step <%s.%s>",
                    workflowId, stepId);
            }
            return this;
        }
        
        public ExecutionHistoryAssert hasFailedExecution(String workflowId, String stepId) {
            isNotNull();
            boolean found = actual.stream()
                .anyMatch(record -> 
                    record.workflowId().equals(workflowId) && 
                    record.stepId().equals(stepId) &&
                    !record.isStart() &&
                    record.result() instanceof StepResult.Fail);
            
            if (!found) {
                failWithMessage("Expected failed execution of step <%s.%s>",
                    workflowId, stepId);
            }
            return this;
        }
        
        public ExecutionHistoryAssert executedInOrder(String... stepIds) {
            isNotNull();
            List<String> executedSteps = actual.stream()
                .filter(TestExecutionInterceptor.ExecutionRecord::isStart)
                .map(TestExecutionInterceptor.ExecutionRecord::stepId)
                .collect(Collectors.toList());
            
            int lastIndex = -1;
            for (String stepId : stepIds) {
                int index = executedSteps.indexOf(stepId);
                if (index == -1) {
                    failWithMessage("Step <%s> was not executed", stepId);
                }
                if (index <= lastIndex) {
                    failWithMessage("Step <%s> was not executed in expected order", stepId);
                }
                lastIndex = index;
            }
            return this;
        }
        
        public ExecutionHistoryAssert allExecutionsMatch(Predicate<TestExecutionInterceptor.ExecutionRecord> predicate) {
            isNotNull();
            Assertions.assertThat(actual)
                .as("All executions")
                .allMatch(predicate);
            return this;
        }
        
        public ExecutionHistoryAssert anyExecutionMatches(Predicate<TestExecutionInterceptor.ExecutionRecord> predicate) {
            isNotNull();
            Assertions.assertThat(actual)
                .as("Any execution")
                .anyMatch(predicate);
            return this;
        }
    }
    
    /**
     * Additional utility assertions.
     */
    
    public static void assertWorkflowCompletes(WorkflowEngine.WorkflowExecution<?> execution,
                                              Duration timeout) {
        try {
            execution.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Assertions.fail("Workflow did not complete within " + timeout + ": " + e.getMessage());
        }
    }
    
    public static <T> void assertWorkflowReturns(WorkflowEngine.WorkflowExecution<T> execution,
                                                 T expectedResult,
                                                 Duration timeout) {
        try {
            T actualResult = execution.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            Assertions.assertThat(actualResult)
                .as("Workflow result")
                .isEqualTo(expectedResult);
        } catch (Exception e) {
            Assertions.fail("Workflow did not complete within " + timeout + ": " + e.getMessage());
        }
    }
    
    public static void assertWorkflowFails(WorkflowEngine.WorkflowExecution<?> execution,
                                          String expectedError,
                                          Duration timeout) {
        Assertions.assertThatThrownBy(() -> 
                execution.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS))
            .hasMessageContaining(expectedError);
    }
    
    /**
     * Assertions for ExecutionTracker ExecutionHistory.
     */
    public static class ExecutionTrackerAssert 
            extends AbstractAssert<ExecutionTrackerAssert, ExecutionTracker.ExecutionHistory> {
        
        public ExecutionTrackerAssert(ExecutionTracker.ExecutionHistory actual) {
            super(actual, ExecutionTrackerAssert.class);
        }
        
        public ExecutionTrackerAssert hasSize(int expectedSize) {
            isNotNull();
            Assertions.assertThat(actual.getOrderedExecutions())
                .as("Execution history size")
                .hasSize(expectedSize);
            return this;
        }
        
        public ExecutionTrackerAssert executedInOrder(String... stepIds) {
            isNotNull();
            List<String> executedSteps = actual.getOrderedExecutions().stream()
                .filter(record -> record.getType() == ExecutionTracker.RecordType.STEP)
                .filter(record -> record.getStatus() == ExecutionTracker.ExecutionStatus.STARTED)
                .map(ExecutionTracker.ExecutionRecord::getStepId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            Assertions.assertThat(executedSteps)
                .as("Executed steps in order")
                .containsExactly(stepIds);
            return this;
        }
        
        public ExecutionTrackerAssert containsStep(String workflowId, String stepId) {
            isNotNull();
            boolean found = actual.getOrderedExecutions().stream()
                .anyMatch(record -> 
                    Objects.equals(record.getWorkflowId(), workflowId) &&
                    Objects.equals(record.getStepId(), stepId)
                );
            
            if (!found) {
                failWithMessage("Expected to find step <%s> in workflow <%s> but it was not executed",
                    stepId, workflowId);
            }
            return this;
        }
        
        public ExecutionTrackerAssert hasExecutionCount(String workflowId, String stepId, int expectedCount) {
            isNotNull();
            String key = workflowId + "." + stepId;
            Integer actualCount = actual.getExecutionCounts().get(key);
            
            Assertions.assertThat(actualCount)
                .as("Execution count for %s.%s", workflowId, stepId)
                .isEqualTo(expectedCount);
            return this;
        }
    }
}