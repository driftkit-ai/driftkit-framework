package ai.driftkit.workflow.test.examples;

import ai.driftkit.workflow.engine.builder.RetryPolicyBuilder;
import ai.driftkit.workflow.engine.builder.WorkflowBuilder;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.test.core.WorkflowTestBase;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Example of using the test framework to test retry functionality.
 * Shows how the test framework simplifies retry testing compared to manual setup.
 */
@Slf4j
public class RetryWorkflowTestExample extends WorkflowTestBase {
    
    
    @Test
    void testRetryWithTestFramework() throws Exception {
        // Create workflow with retry
        var retryPolicy = RetryPolicyBuilder.retry()
            .withMaxAttempts(3)
            .withDelay(100)
            .withRetryOnFailResult(true)
            .build();
            
        WorkflowBuilder<String, String> builder = WorkflowBuilder.define("retry-workflow", String.class, String.class)
            .then((String input, WorkflowContext ctx) -> StepResult.continueWith(input))
            .thenWithRetry("unreliable-step", (String input, WorkflowContext ctx) -> {
                return StepResult.fail("Service unavailable");
            }, retryPolicy)
            .then("process", (String data, WorkflowContext ctx) -> {
                return StepResult.finish("Processed: " + data);
            });
        
        engine.register(builder);
        
        // Setup retry behavior - fail twice, then succeed using new API
        orchestrator.mock().workflow("retry-workflow").step("unreliable-step")
            .times(2).thenFail(new RuntimeException("Service unavailable"))
            .afterwards().thenReturn(Object.class, input -> StepResult.continueWith(input));
        
        // Execute
        var result = executeWorkflow("retry-workflow", "test-data");
        
        // Assert successful completion after retries
        assertEquals("Processed: test-data", result);
        
        // Verify retry behavior - should be 3 total attempts
        assertions.assertStep("retry-workflow", "unreliable-step").wasExecutedTimes(3);
    }
    
    @Test
    void testRetryWithExponentialBackoff() throws Exception {
        var retryPolicy = RetryPolicyBuilder.retry()
            .withMaxAttempts(4)
            .withDelay(100)
            .withBackoffMultiplier(2.0)
            .withRetryOnFailResult(true)
            .build();
            
        WorkflowBuilder<Integer, Integer> builder = WorkflowBuilder.define("backoff-workflow", Integer.class, Integer.class)
            .then((Integer input, WorkflowContext ctx) -> StepResult.continueWith(input))
            .thenWithRetry("flaky-calculation", (Integer num, WorkflowContext ctx) -> {
                // This would normally be an unreliable calculation
                return StepResult.fail("Calculation error");
            }, retryPolicy)
            .then("double", (Integer num, WorkflowContext ctx) -> {
                return StepResult.finish(num * 2);
            });
        
        engine.register(builder);
        
        // Use test framework to control retry behavior - fail twice then succeed
        orchestrator.mock().workflow("backoff-workflow").step("flaky-calculation").times(2)
                .thenFail(new RuntimeException("Temporary failure"))
                .afterwards().thenReturn(Object.class, input -> StepResult.continueWith((Integer) input * 10));
        
        // Execute
        var result = executeWorkflow("backoff-workflow", 5);
        
        // Assert
        assertEquals(100, result); // (5 * 10) * 2
        
        // Verify execution count - when using mocks, the interceptor sees each call
        assertEquals(3, testInterceptor.getExecutionTracker().getExecutionCount("backoff-workflow", "flaky-calculation"));
        // The step was actually attempted 3 times (2 failures + 1 success)
    }
    
    @Test
    void testRetryExhaustion() throws Exception {
        var retryPolicy = RetryPolicyBuilder.retry()
            .withMaxAttempts(3)
            .withDelay(50)
            .withRetryOnFailResult(true)
            .build();
            
        WorkflowBuilder<String, String> builder = WorkflowBuilder.define("exhaustion-workflow", String.class, String.class)
            .then((String input, WorkflowContext ctx) -> StepResult.continueWith(input))
            .thenWithRetry("always-fails", (String input, WorkflowContext ctx) -> {
                return StepResult.fail("Permanent failure");
            }, retryPolicy)
            .then("never-reached", (String data, WorkflowContext ctx) -> {
                return StepResult.finish("Should not reach here");
            });
        
        engine.register(builder);
        
        // No mocking - let it fail naturally
        try {
            executeWorkflow("exhaustion-workflow", "test", Duration.ofSeconds(5));
            // Should not reach here
            fail("Expected workflow to fail");
        } catch (Exception e) {
            log.debug("Expected failure: {}", e.getMessage());
            // Expected - workflow should fail after exhausting retries
            String message = e.getMessage();
            if (e.getCause() != null) {
                message = e.getCause().getMessage();
            }
            // The error message should indicate retry exhaustion
            assertTrue(message.contains("failed after") && message.contains("attempts"), 
                "Expected error message to indicate retry exhaustion but was: " + message);
        }
        
        // Verify all retry attempts were made - without mocks, ExecutionTracker sees actual retries
        assertions.assertStep("exhaustion-workflow", "always-fails").wasExecutedTimes(3);
        // The step was attempted 3 times by RetryExecutor before giving up
        
        // Verify the next step was never executed
        assertions.assertStep("exhaustion-workflow", "never-reached").wasNotExecuted();
    }
}