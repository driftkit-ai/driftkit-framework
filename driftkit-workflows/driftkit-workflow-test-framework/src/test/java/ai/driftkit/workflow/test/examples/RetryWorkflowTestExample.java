package ai.driftkit.workflow.test.examples;

import ai.driftkit.workflow.engine.builder.RetryPolicyBuilder;
import ai.driftkit.workflow.engine.builder.WorkflowBuilder;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.test.assertions.WorkflowTestAssertions;
import ai.driftkit.workflow.test.core.FluentWorkflowTest;
import ai.driftkit.workflow.test.utils.RetryTestUtils;
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
public class RetryWorkflowTestExample extends FluentWorkflowTest {
    
    @Override
    protected void registerWorkflows() {
        // Workflows are registered individually in each test method
    }
    
    @Test
    void testRetryWithTestFramework() throws Exception {
        // Create workflow with retry
        var retryPolicy = RetryPolicyBuilder.retry()
            .withMaxAttempts(3)
            .withDelay(100)
            .build();
            
        WorkflowBuilder<String, String> builder = WorkflowBuilder.define("retry-workflow", String.class, String.class)
            .then((String input, WorkflowContext ctx) -> StepResult.continueWith(input))
            .thenWithRetry("unreliable-step", (String input, WorkflowContext ctx) -> {
                return StepResult.fail("Service unavailable");
            }, retryPolicy)
            .then("process", (String data, WorkflowContext ctx) -> {
                return StepResult.finish("Processed: " + data);
            });
        
        registerWorkflow(builder);
        
        // Setup retry behavior - fail twice, then succeed using new API
        testContext.configure(config -> config
            .mock().workflow("retry-workflow").step("unreliable-step").times(2)
                .thenFail(new RuntimeException("Service unavailable"))
                .afterwards().thenReturn(Object.class, input -> StepResult.continueWith(input))
        );
        
        // Execute
        var result = executeWorkflow("retry-workflow", "test-data");
        
        // Assert successful completion after retries
        assertEquals("Processed: test-data", result);
        
        // Verify retry behavior - should be 3 total attempts
        assertEquals(3, testInterceptor.getExecutionTracker().getExecutionCount("retry-workflow", "unreliable-step"));
        
        // Verify execution history shows retries
        WorkflowTestAssertions.assertThat(testInterceptor.getExecutionTracker().getHistory())
            .hasExecutionCount("retry-workflow", "unreliable-step", 3);
    }
    
    @Test
    void testRetryWithExponentialBackoff() throws Exception {
        var retryPolicy = RetryPolicyBuilder.retry()
            .withMaxAttempts(4)
            .withDelay(100)
            .withBackoffMultiplier(2.0)
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
        
        registerWorkflow(builder);
        
        // Use test framework to control retry behavior - fail twice then succeed
        testContext.configure(config -> config
            .mock().workflow("backoff-workflow").step("flaky-calculation").times(2)
                .thenFail(new RuntimeException("Temporary failure"))
                .afterwards().thenReturn(Object.class, input -> StepResult.continueWith((Integer) input * 10))
        );
        
        // Execute
        var result = executeWorkflow("backoff-workflow", 5);
        
        // Assert
        assertEquals(50, result); // 5 * 10
        
        // Verify retry count
        assertEquals(3, testInterceptor.getExecutionTracker().getExecutionCount("backoff-workflow", "flaky-calculation"));
    }
    
    @Test
    void testRetryExhaustion() throws Exception {
        var retryPolicy = RetryPolicyBuilder.retry()
            .withMaxAttempts(3)
            .withDelay(50)
            .build();
            
        WorkflowBuilder<String, String> builder = WorkflowBuilder.define("exhaustion-workflow", String.class, String.class)
            .then((String input, WorkflowContext ctx) -> StepResult.continueWith(input))
            .thenWithRetry("always-fails", (String input, WorkflowContext ctx) -> {
                return StepResult.fail("Permanent failure");
            }, retryPolicy)
            .then("never-reached", (String data, WorkflowContext ctx) -> {
                return StepResult.finish("Should not reach here");
            });
        
        registerWorkflow(builder);
        
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
            assertTrue(message.contains("Permanent failure"), 
                "Expected error message to contain 'Permanent failure' but was: " + message);
        }
        
        // Verify all retry attempts were made
        assertEquals(3, testInterceptor.getExecutionTracker().getExecutionCount("exhaustion-workflow", "always-fails"));
        
        // Verify the next step was never executed
        WorkflowTestAssertions.assertThat(testInterceptor.getExecutionTracker().getHistory())
            .hasExecutionCount("exhaustion-workflow", "always-fails", 3)
            .hasExecutionCount("exhaustion-workflow", "never-reached", 0);
    }
}