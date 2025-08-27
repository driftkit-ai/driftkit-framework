package ai.driftkit.workflow.test.assertions.strategy;

import ai.driftkit.workflow.test.core.ExecutionTracker;
import org.assertj.core.api.Assertions;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Assertion strategy that verifies strict execution order.
 */
public class StrictOrderAssertionStrategy extends AssertionStrategy {
    
    public StrictOrderAssertionStrategy() {
        super("Strict Order Assertion");
    }
    
    @Override
    public void verify(ExecutionTracker.ExecutionHistory history, ExpectedBehavior expectedBehavior) {
        if (expectedBehavior.getStepOrder() == null || expectedBehavior.getStepOrder().isEmpty()) {
            return; // Nothing to verify
        }
        
        // Extract actual step execution order
        List<String> actualOrder = history.getOrderedExecutions().stream()
            .filter(record -> record.getType() == ExecutionTracker.RecordType.STEP)
            .filter(record -> record.getStatus() == ExecutionTracker.ExecutionStatus.STARTED)
            .map(ExecutionTracker.ExecutionRecord::getStepId)
            .collect(Collectors.toList());
        
        // Verify strict order
        Assertions.assertThat(actualOrder)
            .as("Step execution order")
            .containsExactly(expectedBehavior.getStepOrder().toArray(new String[0]));
        
        // Verify execution counts if specified
        if (expectedBehavior.getExecutionCounts() != null) {
            expectedBehavior.getExecutionCounts().forEach((stepId, expectedCount) -> {
                long actualCount = actualOrder.stream()
                    .filter(stepId::equals)
                    .count();
                
                Assertions.assertThat(actualCount)
                    .as("Execution count for step: %s", stepId)
                    .isEqualTo(expectedCount);
            });
        }
        
        // Verify unexpected steps
        if (expectedBehavior.getUnexpectedSteps() != null) {
            List<String> unexpectedFound = actualOrder.stream()
                .filter(expectedBehavior.getUnexpectedSteps()::contains)
                .collect(Collectors.toList());
            
            Assertions.assertThat(unexpectedFound)
                .as("Unexpected steps that were executed")
                .isEmpty();
        }
    }
}