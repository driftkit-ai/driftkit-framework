package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.annotations.OnInvocationsLimit;
import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import ai.driftkit.workflow.engine.domain.RetryContext;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Handles retry logic for workflow step execution.
 * Implements exponential backoff with jitter and invocation limit control.
 */
@Slf4j
@RequiredArgsConstructor
public class RetryExecutor {
    
    private final RetryStrategy retryStrategy;
    
    public RetryExecutor() {
        this(new DefaultRetryStrategy());
    }
    
    /**
     * Executes a step with retry logic.
     * 
     * @param instance The workflow instance
     * @param step The step to execute
     * @param executor The underlying step executor
     * @return The step result
     * @throws Exception if all retries are exhausted or non-retryable error occurs
     */
    public StepResult<?> executeWithRetry(WorkflowInstance instance, 
                                         StepNode step,
                                         StepExecutor executor) throws Exception {
        String stepId = step.id();
        WorkflowContext context = instance.getContext();
        
        // Check invocation limit
        int invocationCount = context.recordStepExecution(stepId);
        int invocationLimit = step.invocationLimit();
        
        if (invocationCount > invocationLimit) {
            OnInvocationsLimit behavior = step.onInvocationsLimit();
            switch (behavior) {
                case ERROR -> throw new InvocationLimitExceededException(
                    String.format("Step '%s' exceeded invocation limit of %d", stepId, invocationLimit));
                case STOP -> {
                    log.warn("Step '{}' reached invocation limit of {}, stopping execution", stepId, invocationLimit);
                    return StepResult.finish(null);
                }
                case CONTINUE -> log.warn("Step '{}' exceeded invocation limit of {}, continuing anyway", 
                    stepId, invocationLimit);
            }
        }
        
        // Get retry policy
        RetryPolicy retryPolicy = step.retryPolicy();
        if (retryPolicy == null || retryPolicy.maxAttempts() <= 1) {
            // No retry configured
            return executor.execute(instance, step);
        }
        
        // Initialize retry context
        List<RetryContext.RetryAttempt> attempts = new ArrayList<>();
        long firstAttemptTime = System.currentTimeMillis();
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            long attemptStartTime = System.currentTimeMillis();
            
            try {
                // Update retry context in workflow
                RetryContext retryContext = RetryContext.builder()
                    .stepId(stepId)
                    .attemptNumber(attempt)
                    .maxAttempts(retryPolicy.maxAttempts())
                    .previousAttempts(attempts)
                    .firstAttemptTime(firstAttemptTime)
                    .currentAttemptTime(attemptStartTime)
                    .build();
                
                context.updateRetryContext(stepId, retryContext);
                
                // Execute step
                StepResult<?> result = executor.execute(instance, step);
                
                // Check if we should retry on fail result
                if (retryPolicy.retryOnFailResult() && result instanceof StepResult.Fail) {
                    throw new RetryableStepFailure("Step returned fail result", ((StepResult.Fail<?>) result).error());
                }
                
                // Success - clear retry context and return
                context.clearRetryContext(stepId);
                return result;
                
            } catch (Exception e) {
                long attemptDuration = System.currentTimeMillis() - attemptStartTime;
                
                // Record attempt
                RetryContext.RetryAttempt attemptRecord = RetryContext.RetryAttempt.builder()
                    .attemptNumber(attempt)
                    .attemptTime(attemptStartTime)
                    .failure(e)
                    .durationMs(attemptDuration)
                    .build();
                attempts.add(attemptRecord);
                
                // Update retry context with failure
                RetryContext retryContext = RetryContext.builder()
                    .stepId(stepId)
                    .attemptNumber(attempt)
                    .maxAttempts(retryPolicy.maxAttempts())
                    .previousAttempts(attempts)
                    .firstAttemptTime(firstAttemptTime)
                    .currentAttemptTime(attemptStartTime)
                    .build();
                
                context.updateRetryContext(stepId, retryContext);
                
                // Check if we should retry
                if (!retryStrategy.shouldRetry(e, retryContext, retryPolicy)) {
                    log.error("Step '{}' failed on attempt {} (non-retryable error)", stepId, attempt, e);
                    context.clearRetryContext(stepId);
                    throw e;
                }
                
                lastException = e;
                
                if (attempt < retryPolicy.maxAttempts()) {
                    // Calculate delay
                    long delay = retryStrategy.calculateDelay(retryContext, retryPolicy);
                    
                    log.warn("Step '{}' failed on attempt {} of {}, retrying in {}ms", 
                        stepId, attempt, retryPolicy.maxAttempts(), delay, e);
                    
                    // Wait before next attempt
                    try {
                        TimeUnit.MILLISECONDS.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                } else {
                    log.error("Step '{}' failed after {} attempts", stepId, retryPolicy.maxAttempts(), e);
                }
            }
        }
        
        // All retries exhausted
        context.clearRetryContext(stepId);
        throw new RetryExhaustedException(
            String.format("Step '%s' failed after %d attempts", stepId, retryPolicy.maxAttempts()),
            lastException);
    }
    
    /**
     * Internal step executor interface.
     */
    @FunctionalInterface
    public interface StepExecutor {
        StepResult<?> execute(WorkflowInstance instance, StepNode step) throws Exception;
    }
    
    /**
     * Exception thrown when invocation limit is exceeded.
     */
    public static class InvocationLimitExceededException extends RuntimeException {
        public InvocationLimitExceededException(String message) {
            super(message);
        }
    }
    
    /**
     * Exception thrown when all retries are exhausted.
     */
    public static class RetryExhaustedException extends RuntimeException {
        public RetryExhaustedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Wrapper exception for retryable step failures.
     */
    private static class RetryableStepFailure extends RuntimeException {
        public RetryableStepFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}