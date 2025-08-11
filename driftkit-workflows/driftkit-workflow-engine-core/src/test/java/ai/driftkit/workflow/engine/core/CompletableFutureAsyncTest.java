package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.annotations.InitialStep;
import ai.driftkit.workflow.engine.annotations.Step;
import ai.driftkit.workflow.engine.annotations.Workflow;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for CompletableFuture-based async support in workflows.
 */
@Slf4j
public class CompletableFutureAsyncTest {
    
    private WorkflowEngine engine;
    private ExecutorService executorService;
    
    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(4);
        engine = new WorkflowEngine(new WorkflowEngineConfig());
        
        // Register workflow
        engine.register(new CompletableFutureWorkflow());
    }
    
    @Test
    void testCompletableFutureAsync() throws Exception {
        // Execute workflow
        var execution = engine.execute("future-workflow", "test-input");
        
        // Get result
        Object result = execution.get(5, TimeUnit.SECONDS);
        
        // Verify result
        assertEquals("Final result: Processed async: TEST-INPUT", result.toString());
    }
    
    @Test
    void testCompletableFutureError() throws Exception {
        // Execute workflow with error trigger
        var execution = engine.execute("future-workflow", "error");
        
        // Should throw ExecutionException
        assertThrows(Exception.class, () -> execution.get(5, TimeUnit.SECONDS));
    }
    
    @Workflow(id = "future-workflow", version = "1.0")
    public static class CompletableFutureWorkflow {
        
        @InitialStep
        public StepResult<String> processAsync(WorkflowContext context, String input) {
            log.info("Processing input asynchronously: {}", input);
            
            // Create CompletableFuture that returns a StepResult
            CompletableFuture<StepResult<String>> future = CompletableFuture.supplyAsync(() -> {
                log.info("Executing async operation...");
                
                if ("error".equals(input)) {
                    throw new RuntimeException("Test error");
                }
                
                // Simulate async processing
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                
                String result = "Processed async: " + input.toUpperCase();
                return new StepResult.Continue<>(result);
            });
            
            // Return async result with CompletableFuture
            return new StepResult.Async<String>("async-task", future);
        }
        
        @Step(description = "Finalize the result")
        public StepResult.Finish<String> finalizeResult(WorkflowContext context, String asyncResult) {
            log.info("Finalizing result: {}", asyncResult);
            return new StepResult.Finish<>("Final result: " + asyncResult);
        }
    }
}