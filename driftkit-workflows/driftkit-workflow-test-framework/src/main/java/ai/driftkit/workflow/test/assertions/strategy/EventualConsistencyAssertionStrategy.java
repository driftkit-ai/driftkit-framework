package ai.driftkit.workflow.test.assertions.strategy;

import ai.driftkit.workflow.test.core.ExecutionTracker;
import org.assertj.core.api.Assertions;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Assertion strategy that verifies eventual consistency.
 * Steps must execute but order doesn't matter.
 */
public class EventualConsistencyAssertionStrategy extends AssertionStrategy {
    
    public EventualConsistencyAssertionStrategy() {
        super("Eventual Consistency Assertion");
    }
    
    @Override
    public void verify(ExecutionTracker.ExecutionHistory history, ExpectedBehavior expectedBehavior) {
        // Extract all executed steps
        List<String> executedSteps = history.getOrderedExecutions().stream()
            .filter(record -> record.getType() == ExecutionTracker.RecordType.STEP)
            .filter(record -> record.getStatus() == ExecutionTracker.ExecutionStatus.STARTED)
            .map(ExecutionTracker.ExecutionRecord::getStepId)
            .collect(Collectors.toList());
        
        // Verify expected steps (any order)
        if (expectedBehavior.getExpectedSteps() != null && !expectedBehavior.getExpectedSteps().isEmpty()) {
            List<String> distinctExecuted = executedSteps.stream()
                .distinct()
                .collect(Collectors.toList());
            
            Assertions.assertThat(distinctExecuted)
                .as("Expected steps (any order)")
                .containsExactlyInAnyOrderElementsOf(expectedBehavior.getExpectedSteps());
        }
        
        // Verify execution counts
        if (expectedBehavior.getExecutionCounts() != null) {
            Map<String, Long> actualCounts = executedSteps.stream()
                .collect(Collectors.groupingBy(
                    stepId -> stepId,
                    Collectors.counting()
                ));
            
            expectedBehavior.getExecutionCounts().forEach((stepId, expectedCount) -> {
                long actualCount = actualCounts.getOrDefault(stepId, 0L);
                
                Assertions.assertThat(actualCount)
                    .as("Execution count for step: %s", stepId)
                    .isEqualTo(expectedCount);
            });
        }
        
        // Verify completion status
        if (expectedBehavior.isShouldComplete()) {
            boolean hasCompletion = history.getOrderedExecutions().stream()
                .anyMatch(record -> 
                    record.getType() == ExecutionTracker.RecordType.WORKFLOW &&
                    record.getStatus() == ExecutionTracker.ExecutionStatus.COMPLETED
                );
            
            Assertions.assertThat(hasCompletion)
                .as("Workflow should have completed")
                .isTrue();
        }
        
        // Verify failure status
        if (expectedBehavior.isShouldFail()) {
            boolean hasFailure = history.getOrderedExecutions().stream()
                .anyMatch(record -> 
                    record.getType() == ExecutionTracker.RecordType.WORKFLOW &&
                    record.getStatus() == ExecutionTracker.ExecutionStatus.FAILED
                );
            
            Assertions.assertThat(hasFailure)
                .as("Workflow should have failed")
                .isTrue();
        }
    }
}