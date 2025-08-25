package ai.driftkit.workflow.test.examples;

import ai.driftkit.workflow.engine.builder.WorkflowBuilder;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.test.core.FluentWorkflowTest;
import ai.driftkit.workflow.test.assertions.WorkflowTestAssertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple example test using the workflow test framework.
 */
public class SimpleWorkflowTest extends FluentWorkflowTest {
    
    @Override
    protected void registerWorkflows() {
        // Workflows are registered individually in each test method
    }
    
    @Test
    void testSimpleLinearWorkflow() {
        // Create a simple workflow using fluent API
        WorkflowBuilder<String, String> builder = WorkflowBuilder.define("simple-workflow", String.class, String.class)
            .then("initial", (String input) -> StepResult.continueWith("Hello " + input), String.class, String.class)
            .then("concat", (String prev) -> StepResult.continueWith(prev + " World"), String.class, String.class)
            .then("uppercase", (String prev) -> StepResult.finish(prev.toUpperCase()), String.class, String.class);
        
        // Register the workflow
        registerWorkflow(builder);
        
        // Execute the workflow
        WorkflowEngine.WorkflowExecution<String> execution = engine.execute("simple-workflow", "Test");
        
        // Assert the result
        WorkflowTestAssertions.assertWorkflowReturns(execution, "HELLO TEST WORLD", Duration.ofSeconds(5));
        
        // Verify execution history
        WorkflowTestAssertions.assertThat(testInterceptor.getExecutionTracker().getHistory())
            .hasSize(6) // 3 steps x 2 (before/after)
            .executedInOrder("initial", "concat", "uppercase");
    }
    
    @Test
    void testWorkflowWithMocking() {
        // Create workflow with external service call
        WorkflowBuilder<String, String> builder = WorkflowBuilder.define("service-workflow", String.class, String.class)
            .then((String input) -> StepResult.continueWith(input))
            .then("external-service", (String input) -> {
                // This would normally call an external service
                throw new RuntimeException("Should not be called");
            }, String.class, String.class)
            .then("process", (String response) -> StepResult.finish("Processed: " + response), String.class, String.class);
        
        registerWorkflow(builder);
        
        // Mock the external service step using new API
        testContext.configure(config -> config
            .mock().workflow("service-workflow").step("external-service").always()
                .thenReturn(String.class, input -> StepResult.continueWith("Mocked response for: " + input))
        );
        
        // Execute
        WorkflowEngine.WorkflowExecution<String> execution = engine.execute("service-workflow", "test-input");
        
        // Assert
        WorkflowTestAssertions.assertWorkflowReturns(execution, 
            "Processed: Mocked response for: test-input", Duration.ofSeconds(5));
        
        // Verify the mock was called
        assertTrue(testInterceptor.getExecutionTracker().wasExecuted("service-workflow", "external-service"));
        assertEquals(1, testInterceptor.getExecutionTracker().getExecutionCount("service-workflow", "external-service"));
    }
    
    @Test
    void testWorkflowWithConditionalMocking() {
        WorkflowBuilder<Integer, String> builder = WorkflowBuilder.define("conditional-workflow", Integer.class, String.class)
            .then((Integer input) -> StepResult.continueWith(input))
            .then("check", (Integer num) -> {
                if (num > 10) {
                    return StepResult.continueWith("Large: " + num);
                }
                return StepResult.continueWith("Small: " + num);
            }, Integer.class, String.class)
            .then("format", (String msg) -> StepResult.finish("[" + msg + "]"), String.class, String.class);
        
        registerWorkflow(builder);
        
        // Add conditional mock that only applies for large numbers
        testContext.configure(config -> config
            .mock().workflow("conditional-workflow").step("check")
                .when(Integer.class, num -> num > 100)
                .thenReturn(Integer.class, num -> StepResult.continueWith("Huge: " + num))
        );
        
        // Test with different inputs
        try {
            var result1 = engine.execute("conditional-workflow", 5).get();
            assertEquals("[Small: 5]", result1);
            
            var result2 = engine.execute("conditional-workflow", 50).get();
            assertEquals("[Large: 50]", result2);
            
            var result3 = engine.execute("conditional-workflow", 150).get();
            assertEquals("[Huge: 150]", result3);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}