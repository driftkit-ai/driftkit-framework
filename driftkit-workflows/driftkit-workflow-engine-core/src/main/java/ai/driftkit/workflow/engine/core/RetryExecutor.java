package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.annotations.OnInvocationsLimit;
import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import ai.driftkit.workflow.engine.domain.RetryContext;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Handles retry logic for workflow step execution.
 * Implements exponential backoff with jitter and invocation limit control.
 * Supports circuit breaker, metrics collection, and retry listeners.
 */
@Slf4j
@RequiredArgsConstructor
public class RetryExecutor {
    
    private final RetryStrategy retryStrategy;
    private final CircuitBreaker circuitBreaker;
    private final RetryMetrics retryMetrics;
    private final List<RetryListener> retryListeners;
    
    public RetryExecutor() {
        this(new ConditionalRetryStrategy(), 
             new CircuitBreaker(), 
             new RetryMetrics(),
             new CopyOnWriteArrayList<>());
    }
    
    public RetryExecutor(RetryStrategy retryStrategy) {
        this(retryStrategy, 
             new CircuitBreaker(), 
             new RetryMetrics(),
             new CopyOnWriteArrayList<>());
    }
    
    /**
     * Adds a retry listener.
     * 
     * @param listener The listener to add
     */
    public void addRetryListener(RetryListener listener) {
        retryListeners.add(listener);
    }
    
    /**
     * Removes a retry listener.
     * 
     * @param listener The listener to remove
     */
    public void removeRetryListener(RetryListener listener) {
        retryListeners.remove(listener);
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
        
        // Check circuit breaker first
        if (!circuitBreaker.allowExecution(stepId)) {
            log.warn("Circuit breaker is open for step '{}', failing fast", stepId);
            throw new CircuitBreakerOpenException(
                String.format("Circuit breaker is open for step '%s'", stepId));
        }
        
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
                // Calculate delay for this attempt (used in metrics)
                long delay = attempt > 1 ? retryStrategy.calculateDelay(
                    RetryContext.builder()
                        .attemptNumber(attempt - 1)
                        .build(), 
                    retryPolicy) : 0;
                
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
                
                // Notify listeners before retry
                if (attempt > 1) {
                    notifyBeforeRetry(stepId, retryContext, retryPolicy);
                    retryMetrics.recordRetryAttempt(stepId, attempt, delay);
                }
                
                // Execute step
                StepResult<?> result = executor.execute(instance, step);
                
                // Check if we should retry on fail result
                if (retryPolicy.retryOnFailResult() && result instanceof StepResult.Fail) {
                    throw new RetryableStepFailure("Step returned fail result", ((StepResult.Fail<?>) result).error());
                }
                
                // Success - clear retry context and return
                context.clearRetryContext(stepId);
                circuitBreaker.recordSuccess(stepId);
                
                if (attempt > 1) {
                    long totalDuration = System.currentTimeMillis() - firstAttemptTime;
                    retryMetrics.recordRetrySuccess(stepId, attempt, totalDuration);
                    notifyRetrySuccess(stepId, retryContext, result);
                }
                
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
                
                // Record failure metrics
                circuitBreaker.recordFailure(stepId, e);
                retryMetrics.recordRetryFailure(stepId, attempt, e);
                
                // Check if we should retry
                if (!retryStrategy.shouldRetry(e, retryContext, retryPolicy)) {
                    // Check if this is because we've exhausted attempts
                    if (attempt >= retryPolicy.maxAttempts()) {
                        // Break out of loop to handle exhaustion properly
                        lastException = e;
                        break;
                    }
                    // Otherwise it's a non-retryable error
                    log.error("Step '{}' failed on attempt {} (non-retryable error)", stepId, attempt, e);
                    context.clearRetryContext(stepId);
                    notifyRetryAborted(stepId, retryContext, e);
                    throw e;
                }
                
                lastException = e;
                
                if (attempt < retryPolicy.maxAttempts()) {
                    // Calculate delay
                    long delay = retryStrategy.calculateDelay(retryContext, retryPolicy);
                    
                    log.warn("Step '{}' failed on attempt {} of {}, retrying in {}ms", 
                        stepId, attempt, retryPolicy.maxAttempts(), delay, e);
                    
                    // Notify listeners
                    notifyRetryFailure(stepId, retryContext, e, true);
                    
                    // Wait before next attempt
                    try {
                        TimeUnit.MILLISECONDS.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                } else {
                    log.error("Step '{}' failed after {} attempts", stepId, retryPolicy.maxAttempts(), e);
                    notifyRetryFailure(stepId, retryContext, e, false);
                }
            }
        }
        
        // All retries exhausted
        context.clearRetryContext(stepId);
        retryMetrics.recordRetryExhausted(stepId, retryPolicy.maxAttempts());
        
        RetryContext finalContext = RetryContext.builder()
            .stepId(stepId)
            .attemptNumber(retryPolicy.maxAttempts())
            .maxAttempts(retryPolicy.maxAttempts())
            .previousAttempts(attempts)
            .firstAttemptTime(firstAttemptTime)
            .currentAttemptTime(System.currentTimeMillis())
            .build();
            
        notifyRetryExhausted(stepId, finalContext, lastException);
        
        throw new RetryExhaustedException(
            String.format("Step '%s' failed after %d attempts", stepId, retryPolicy.maxAttempts()),
            lastException);
    }
    
    // Notification methods for listeners
    
    private void notifyBeforeRetry(String stepId, RetryContext context, RetryPolicy policy) {
        if (CollectionUtils.isEmpty(retryListeners)) {
            return;
        }
        
        for (RetryListener listener : retryListeners) {
            try {
                listener.beforeRetry(stepId, context, policy);
            } catch (Exception e) {
                log.error("Error in retry listener beforeRetry", e);
            }
        }
    }
    
    private void notifyRetrySuccess(String stepId, RetryContext context, Object result) {
        if (CollectionUtils.isEmpty(retryListeners)) {
            return;
        }
        
        for (RetryListener listener : retryListeners) {
            try {
                listener.onRetrySuccess(stepId, context, result);
            } catch (Exception e) {
                log.error("Error in retry listener onRetrySuccess", e);
            }
        }
    }
    
    private void notifyRetryFailure(String stepId, RetryContext context, Exception exception, boolean willRetry) {
        if (CollectionUtils.isEmpty(retryListeners)) {
            return;
        }
        
        for (RetryListener listener : retryListeners) {
            try {
                listener.onRetryFailure(stepId, context, exception, willRetry);
            } catch (Exception e) {
                log.error("Error in retry listener onRetryFailure", e);
            }
        }
    }
    
    private void notifyRetryExhausted(String stepId, RetryContext context, Exception lastException) {
        if (CollectionUtils.isEmpty(retryListeners)) {
            return;
        }
        
        for (RetryListener listener : retryListeners) {
            try {
                listener.onRetryExhausted(stepId, context, lastException);
            } catch (Exception e) {
                log.error("Error in retry listener onRetryExhausted", e);
            }
        }
    }
    
    private void notifyRetryAborted(String stepId, RetryContext context, Exception exception) {
        if (CollectionUtils.isEmpty(retryListeners)) {
            return;
        }
        
        for (RetryListener listener : retryListeners) {
            try {
                listener.onRetryAborted(stepId, context, exception);
            } catch (Exception e) {
                log.error("Error in retry listener onRetryAborted", e);
            }
        }
    }
    
    /**
     * Internal step executor interface.
     */
    @FunctionalInterface
    public interface StepExecutor {
        StepResult<?> execute(WorkflowInstance instance, StepNode step) throws Exception;
    }
    
    /**
     * Gets access to the retry metrics.
     * 
     * @return The retry metrics instance
     */
    public RetryMetrics getRetryMetrics() {
        return retryMetrics;
    }
    
    /**
     * Gets access to the circuit breaker.
     * 
     * @return The circuit breaker instance
     */
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
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
     * Exception thrown when circuit breaker is open.
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
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