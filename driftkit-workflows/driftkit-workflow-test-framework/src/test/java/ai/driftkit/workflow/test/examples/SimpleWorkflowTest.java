package ai.driftkit.workflow.test.examples;

import ai.driftkit.workflow.engine.builder.WorkflowBuilder;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.test.core.WorkflowTestBase;
import ai.driftkit.workflow.test.assertions.EnhancedWorkflowAssertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple example test using the workflow test framework.
 */
public class SimpleWorkflowTest extends WorkflowTestBase {

    @Override
    protected WorkflowEngine createEngine() {
        return new WorkflowEngine();
    }
    
    
    @Test
    void testSimpleLinearWorkflow() {
        // Create a simple workflow using fluent API
        WorkflowBuilder<String, String> builder = WorkflowBuilder.define("simple-workflow", String.class, String.class)
            .then("initial", (String input) -> StepResult.continueWith("Hello " + input), String.class, String.class)
            .then("concat", (String prev) -> StepResult.continueWith(prev + " World"), String.class, String.class)
            .then("uppercase", (String prev) -> StepResult.finish(prev.toUpperCase()), String.class, String.class);
        
        // Register the workflow
        engine.register(builder);
        
        // Execute the workflow
        WorkflowEngine.WorkflowExecution<String> execution = engine.execute("simple-workflow", "Test");
        
        // Assert the result using new assertions
        try {
            String result = execution.get(5000, java.util.concurrent.TimeUnit.MILLISECONDS);
            assertEquals("HELLO TEST WORLD", result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        // Verify execution history using new API
        assertions.assertStep("simple-workflow", "initial").wasExecuted();
        assertions.assertStep("simple-workflow", "concat").wasExecuted();
        assertions.assertStep("simple-workflow", "uppercase").wasExecuted();
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
        
        engine.register(builder);
        
        // Mock the external service step using new API
        orchestrator.mock().workflow("service-workflow").step("external-service")
            .always().thenReturn(String.class, input -> StepResult.continueWith("Mocked response for: " + input));
        
        // Execute
        WorkflowEngine.WorkflowExecution<String> execution = engine.execute("service-workflow", "test-input");
        
        // Assert
        try {
            String result = execution.get(5000, java.util.concurrent.TimeUnit.MILLISECONDS);
            assertEquals("Processed: Mocked response for: test-input", result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        // Verify the mock was called
        assertions.assertStep("service-workflow", "external-service").wasExecuted().wasExecutedTimes(1);
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
        
        engine.register(builder);
        
        // Add conditional mock that only applies for large numbers
        orchestrator.mock().workflow("conditional-workflow").step("check")
            .when(Integer.class, num -> num > 100)
            .thenReturn(Integer.class, num -> StepResult.continueWith("Huge: " + num));
        
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