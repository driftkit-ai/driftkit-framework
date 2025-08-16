package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.function.Function;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

class StepDefinitionTest {
    
    @Test
    @DisplayName("Should create StepDefinition with function")
    void testCreateWithFunction() {
        Function<String, StepResult<String>> stepFn = 
            input -> StepResult.continueWith(input.toUpperCase());
        
        StepDefinition step = StepDefinition.of("uppercase", stepFn);
        
        assertEquals("uppercase", step.getId());
        assertNotNull(step.getExecutor());
        assertEquals("Step: uppercase", step.getDescription());
        // Note: inputType and outputType are null for lambdas without type info
        assertEquals(Object.class, step.getInputType());
        assertEquals(Object.class, step.getOutputType());
    }
    
    @Test
    @DisplayName("Should create StepDefinition with explicit types")
    void testCreateWithExplicitTypes() {
        Function<Integer, StepResult<String>> stepFn = 
            num -> StepResult.continueWith("Number: " + num);
        
        StepDefinition step = StepDefinition.of("converter", stepFn)
            .withTypes(Integer.class, String.class)
            .withDescription("Convert number to string");
        
        assertEquals("converter", step.getId());
        assertEquals("Convert number to string", step.getDescription());
        assertEquals(Integer.class, step.getInputType());
        assertEquals(String.class, step.getOutputType());
    }
    
    @Test
    @DisplayName("Should extract method info from serializable function")
    void testSerializableFunction() {
        // Use SerializableFunction for method references
        StepDefinition.SerializableFunction<String, StepResult<String>> methodRef = 
            this::processString;
        
        StepDefinition step = StepDefinition.of(methodRef);
        
        assertEquals("processString", step.getId());
        assertEquals(String.class, step.getInputType());
        assertEquals(String.class, step.getOutputType());
    }
    
    @Test
    @DisplayName("Should handle bi-function step definitions")
    void testBiFunctionStep() {
        BiFunction<String, WorkflowContext, StepResult<String>> stepFn = 
            (input, ctx) -> StepResult.continueWith(input + "-" + ctx.getRunId());
        
        StepDefinition step = StepDefinition.of("combine", stepFn)
            .withTypes(String.class, String.class);
        
        assertEquals("combine", step.getId());
        assertEquals(String.class, step.getInputType());
        assertEquals(String.class, step.getOutputType());
    }
    
    @Test
    @DisplayName("Should handle context-only steps")
    void testContextOnlyStep() {
        Function<WorkflowContext, StepResult<String>> stepFn = 
            ctx -> StepResult.continueWith("RunId: " + ctx.getRunId());
        
        StepDefinition step = StepDefinition.ofContextOnly("context-step", stepFn)
            .withOutputType(String.class);
        
        assertEquals("context-step", step.getId());
        assertEquals(Void.class, step.getInputType());
        assertEquals(String.class, step.getOutputType());
    }
    
    @Test
    @DisplayName("Should validate null arguments")
    void testNullValidation() {
        // Null ID should throw
        assertThrows(IllegalArgumentException.class, 
            () -> StepDefinition.of(null, s -> StepResult.continueWith(s)));
        
        // Empty ID should throw
        assertThrows(IllegalArgumentException.class, 
            () -> StepDefinition.of("", s -> StepResult.continueWith(s)));
    }
    
    private StepResult<String> processString(String input) {
        return StepResult.continueWith(input.trim());
    }
}