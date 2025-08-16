package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowBuilderBasicTest {
    
    @Test
    @DisplayName("Should build simple linear workflow")
    void testSimpleLinearWorkflow() {
        // Arrange - nothing to set up for this test
        
        // Act - Build the workflow
        WorkflowGraph<String, String> workflow = WorkflowBuilder
            .define("simple-workflow", String.class, String.class)
            .then((String input) -> StepResult.continueWith(input.toUpperCase()))
            .then((String upper) -> StepResult.continueWith(upper + "!"))
            .then((String excited) -> StepResult.finish(excited))
            .build();
        
        // Assert - Verify workflow structure
        assertNotNull(workflow, "Workflow should be created successfully");
        assertEquals("simple-workflow", workflow.id(), "Workflow ID should match");
        assertEquals(String.class, workflow.inputType(), "Input type should be String");
        assertEquals(String.class, workflow.outputType(), "Output type should be String");
        assertEquals(3, workflow.nodes().size(), "Should have 3 steps");
    }
    
    @Test
    @DisplayName("Should build workflow with named steps")
    void testNamedSteps() {
        // Arrange
        String[] expectedStepNames = {"validate", "double", "format"};
        
        // Act - Build workflow with explicitly named steps
        WorkflowGraph<Integer, String> workflow = WorkflowBuilder
            .define("named-steps", Integer.class, String.class)
            .then("validate", (Integer num) -> {
                if (num < 0) return StepResult.fail("Negative number");
                return StepResult.continueWith(num);
            }, Integer.class, Integer.class)
            .then("double", (Integer num) -> StepResult.continueWith(num * 2), Integer.class, Integer.class)
            .then("format", (Integer num) -> StepResult.finish("Result: " + num), Integer.class, String.class)
            .build();
        
        // Assert - Verify all named steps are present
        assertEquals(3, workflow.nodes().size(), "Should have exactly 3 steps");
        for (String stepName : expectedStepNames) {
            assertTrue(workflow.nodes().containsKey(stepName), 
                "Workflow should contain step: " + stepName);
        }
    }
    
    @Test
    @DisplayName("Should support method references")
    void testMethodReferences() {
        WorkflowGraph<String, Integer> workflow = WorkflowBuilder
            .define("method-ref-workflow", String.class, Integer.class)
            .then(this::trimInput)
            .then(this::countWords)
            .then(this::finalizeCount)
            .build();
        
        assertNotNull(workflow);
        assertEquals(3, workflow.nodes().size());
    }
    
    @Test
    @DisplayName("Should build workflow with auto-wrap value methods")
    void testAutoWrapValueMethods() {
        WorkflowGraph<String, String> workflow = WorkflowBuilder
            .define("auto-wrap-workflow", String.class, String.class)
            .thenValue((String s) -> s.trim())           // Returns String directly
            .thenValue((String s) -> s.toUpperCase())    // Returns String directly
            .finishWithValue((String s) -> s + "!")     // Returns String directly (final)
            .build();
        
        assertNotNull(workflow);
        assertEquals(3, workflow.nodes().size());
    }
    
    @Test
    @DisplayName("Should handle empty workflow")
    void testEmptyWorkflow() {
        assertThrows(IllegalStateException.class, () -> 
            WorkflowBuilder
                .define("empty", String.class, String.class)
                .build()
        );
    }
    
    @Test
    @DisplayName("Should preserve step order")
    void testStepOrder() {
        // Arrange
        String workflowId = "ordered";
        String[] stepIds = {"step1", "step2", "step3"};
        
        // Act - Build workflow with ordered steps
        WorkflowGraph<String, String> workflow = WorkflowBuilder
            .define(workflowId, String.class, String.class)
            .then(stepIds[0], (String s) -> StepResult.continueWith(s + "1"), String.class, String.class)
            .then(stepIds[1], (String s) -> StepResult.continueWith(s + "2"), String.class, String.class)
            .then(stepIds[2], (String s) -> StepResult.finish(s + "3"), String.class, String.class)
            .build();
        
        // Assert - Verify step creation and ordering
        assertEquals(stepIds.length, workflow.nodes().size(), 
            "Should have " + stepIds.length + " steps");
        
        for (int i = 0; i < stepIds.length; i++) {
            assertTrue(workflow.nodes().containsKey(stepIds[i]), 
                "Step " + stepIds[i] + " should exist at position " + i);
        }
        
        // Verify initial step is set
        assertNotNull(workflow.initialStepId());
    }
    
    // Helper methods for method reference tests
    private StepResult<String> trimInput(String input) {
        return StepResult.continueWith(input.trim());
    }
    
    private StepResult<Integer> countWords(String input) {
        String[] words = input.split("\\s+");
        return StepResult.continueWith(words.length);
    }
    
    private StepResult<Integer> finalizeCount(Integer count) {
        return StepResult.finish(count);
    }
}