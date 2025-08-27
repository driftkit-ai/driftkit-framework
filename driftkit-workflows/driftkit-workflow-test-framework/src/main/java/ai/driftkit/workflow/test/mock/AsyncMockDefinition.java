package ai.driftkit.workflow.test.mock;

import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.test.core.MockDefinition;
import ai.driftkit.workflow.test.core.StepContext;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

/**
 * Mock definition that returns async results.
 * Supports delayed responses and async workflows.
 */
@Slf4j
public class AsyncMockDefinition<I> extends MockDefinition<I> {
    
    private final Function<I, CompletableFuture<StepResult<?>>> asyncFunction;
    private final Executor executor;
    private final long delayMs;
    
    /**
     * Creates an async mock with custom executor.
     */
    public AsyncMockDefinition(String workflowId, 
                              String stepId,
                              Class<I> inputType,
                              Function<I, CompletableFuture<StepResult<?>>> asyncFunction,
                              Executor executor,
                              long delayMs) {
        super(workflowId, stepId, inputType, (input, context) -> {
            throw new IllegalStateException("AsyncMockDefinition.execute should handle execution");
        });
        this.asyncFunction = asyncFunction;
        this.executor = executor != null ? executor : ForkJoinPool.commonPool();
        this.delayMs = delayMs;
    }
    
    /**
     * Creates an async mock with default executor.
     */
    public static <I> AsyncMockDefinition<I> async(String workflowId,
                                                   String stepId,
                                                   Class<I> inputType,
                                                   Function<I, CompletableFuture<StepResult<?>>> asyncFunction) {
        return new AsyncMockDefinition<>(workflowId, stepId, inputType, asyncFunction, null, 0);
    }
    
    /**
     * Creates an async mock with delay.
     */
    public static <I> AsyncMockDefinition<I> asyncWithDelay(String workflowId,
                                                            String stepId,
                                                            Class<I> inputType,
                                                            Function<I, StepResult<?>> function,
                                                            long delayMs) {
        Function<I, CompletableFuture<StepResult<?>>> asyncFunction = input ->
            CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Async delay interrupted", e);
                }
                return function.apply(input);
            });
            
        return new AsyncMockDefinition<>(workflowId, stepId, inputType, asyncFunction, null, delayMs);
    }
    
    @Override
    public StepResult<?> execute(Object input, StepContext context) {
        log.debug("Executing async mock for {}.{} with delay {}ms", 
            workflowId, stepId, delayMs);
        
        I typedInput = inputType.cast(input);
        
        // For async mocks, we need to block and get the result
        // In a real async scenario, this would return StepResult.async()
        // But for testing, we simulate async behavior and return the result
        try {
            CompletableFuture<StepResult<?>> future = asyncFunction.apply(typedInput);
            return future.get(); // Block for testing purposes
        } catch (Exception e) {
            return StepResult.fail(e);
        }
    }
    
    /**
     * Creates a builder for async mocks.
     */
    public static <I> AsyncMockBuilder<I> builder(String workflowId, 
                                                  String stepId,
                                                  Class<I> inputType) {
        return new AsyncMockBuilder<>(workflowId, stepId, inputType);
    }
    
    /**
     * Builder for async mocks.
     */
    public static class AsyncMockBuilder<I> {
        private final String workflowId;
        private final String stepId;
        private final Class<I> inputType;
        private Executor executor;
        private long delayMs = 0;
        
        AsyncMockBuilder(String workflowId, String stepId, Class<I> inputType) {
            this.workflowId = workflowId;
            this.stepId = stepId;
            this.inputType = inputType;
        }
        
        public AsyncMockBuilder<I> withExecutor(Executor executor) {
            this.executor = executor;
            return this;
        }
        
        public AsyncMockBuilder<I> withDelay(long delayMs) {
            this.delayMs = delayMs;
            return this;
        }
        
        public AsyncMockDefinition<I> thenReturn(Function<I, StepResult<?>> function) {
            Function<I, CompletableFuture<StepResult<?>>> asyncFunction;
            
            if (delayMs > 0) {
                asyncFunction = input ->
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Async delay interrupted", e);
                        }
                        return function.apply(input);
                    }, executor != null ? executor : ForkJoinPool.commonPool());
            } else {
                asyncFunction = input ->
                    CompletableFuture.completedFuture(function.apply(input));
            }
            
            return new AsyncMockDefinition<>(workflowId, stepId, inputType, 
                asyncFunction, executor, delayMs);
        }
        
        public AsyncMockDefinition<I> thenReturnAsync(Function<I, CompletableFuture<StepResult<?>>> asyncFunction) {
            return new AsyncMockDefinition<>(workflowId, stepId, inputType, 
                asyncFunction, executor, delayMs);
        }
    }
}