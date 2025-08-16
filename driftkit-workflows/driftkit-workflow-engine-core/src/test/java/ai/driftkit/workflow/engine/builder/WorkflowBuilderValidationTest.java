package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.inmemory.*;
import ai.driftkit.workflow.engine.async.InMemoryProgressTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowBuilderValidationTest {
    
    private WorkflowEngine engine;
    
    @BeforeEach
    void setUp() {
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .stateRepository(new InMemoryWorkflowStateRepository())
            .progressTracker(new InMemoryProgressTracker())
            .chatSessionRepository(new InMemoryChatSessionRepository())
            .chatHistoryRepository(new InMemoryChatHistoryRepository())
            .asyncStepStateRepository(new InMemoryAsyncStepStateRepository())
            .suspensionDataRepository(new InMemorySuspensionDataRepository())
            .build();
        
        engine = new WorkflowEngine(config);
    }
    
    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {
        
        @Test
        @DisplayName("Should validate empty workflow")
        void testEmptyWorkflowValidation() {
            assertThrows(IllegalStateException.class, () ->
                WorkflowBuilder
                    .define("empty", String.class, String.class)
                    .build()
            );
        }
        
        @Test
        @DisplayName("Should validate duplicate step IDs")
        void testDuplicateStepIds() {
            assertThrows(IllegalStateException.class, () ->
                WorkflowBuilder
                    .define("duplicate-ids", String.class, String.class)
                    .then("step1", (String s) -> StepResult.continueWith(s), String.class, String.class)
                    .then("step1", (String s) -> StepResult.finish(s), String.class, String.class)  // Duplicate ID
                    .build()
            );
        }
        
        @Test
        @DisplayName("Should validate null step ID")
        void testNullStepId() {
            assertThrows(IllegalArgumentException.class, () ->
                WorkflowBuilder
                    .define("null-id", String.class, String.class)
                    .then(null, (String s) -> StepResult.continueWith(s), String.class, String.class)
                    .build()
            );
        }
        
        @Test
        @DisplayName("Should fail at runtime with null step function")
        void testNullStepFunctionFailsAtRuntime() throws Exception {
            // Arrange - Create workflow with null function
            WorkflowGraph<String, String> workflow = WorkflowBuilder
                .define("null-function", String.class, String.class)
                .then("step1", null, String.class, String.class)
                .build();
            
            // Act - Register and try to execute
            engine.register(workflow);
            
            // Assert - Should fail during execution
            WorkflowEngine.WorkflowExecution<String> execution = engine.execute("null-function", "test-input");
            
            // Verify execution fails with appropriate error
            ExecutionException exception = assertThrows(ExecutionException.class, 
                () -> execution.get(5, TimeUnit.SECONDS));
            
            // The cause should be related to null function
            assertNotNull(exception.getCause());
            assertTrue(exception.getCause() instanceof NullPointerException || 
                      exception.getCause().getMessage().contains("null"));
        }
        
        @Test
        @DisplayName("Should build workflow but warn about null function")
        void testNullStepFunctionBuildBehavior() {
            // This test documents that the builder allows null functions
            // This is a design choice that should be documented
            WorkflowGraph<String, String> workflow = WorkflowBuilder
                .define("null-function-doc", String.class, String.class)
                .then("step1", null, String.class, String.class)
                .build();
            
            // Verify build succeeds
            assertNotNull(workflow);
            assertEquals(1, workflow.nodes().size());
            
            // Document that this is allowed at build time but will fail at runtime
            // This allows for late binding of functions if needed
        }
    }
}