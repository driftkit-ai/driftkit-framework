package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import ai.driftkit.workflow.engine.domain.RetryContext;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Default implementation of RetryStrategy that supports exponential backoff with jitter.
 */
@Slf4j
public class DefaultRetryStrategy implements RetryStrategy {
    
    @Override
    public boolean shouldRetry(Throwable failure, RetryContext context, RetryPolicy policy) {
        // Check if we have attempts remaining
        if (context.getAttemptNumber() >= policy.maxAttempts()) {
            log.debug("No more retry attempts for step {}: {}/{}", 
                context.getStepId(), context.getAttemptNumber(), policy.maxAttempts());
            return false;
        }
        
        // Check if the exception type should be retried
        if (!isRetryableException(failure, policy)) {
            log.debug("Exception {} is not retryable for step {}", 
                failure.getClass().getName(), context.getStepId());
            return false;
        }
        
        return true;
    }
    
    @Override
    public long calculateDelay(RetryContext context, RetryPolicy policy) {
        // Calculate base delay with exponential backoff
        long baseDelay = policy.delay();
        int retryCount = context.getAttemptNumber() - 1; // Convert to 0-based
        
        if (policy.backoffMultiplier() > 1.0 && retryCount > 0) {
            baseDelay = (long) (baseDelay * Math.pow(policy.backoffMultiplier(), retryCount));
        }
        
        // Apply max delay cap
        baseDelay = Math.min(baseDelay, policy.maxDelay());
        
        // Apply jitter
        if (policy.jitterFactor() > 0) {
            double jitter = policy.jitterFactor() * ThreadLocalRandom.current().nextDouble();
            baseDelay = (long) (baseDelay * (1 + jitter));
        }
        
        return baseDelay;
    }
    
    @Override
    public void beforeRetry(RetryContext context, long delay) {
        log.info("Retrying step {} (attempt {}/{}) after {}ms delay", 
            context.getStepId(), 
            context.getAttemptNumber() + 1, 
            context.getMaxAttempts(),
            delay);
    }
    
    @Override
    public void afterRetry(RetryContext context, boolean success, long duration) {
        if (success) {
            log.info("Retry successful for step {} on attempt {} (took {}ms)", 
                context.getStepId(), context.getAttemptNumber(), duration);
        } else {
            log.warn("Retry failed for step {} on attempt {} (took {}ms)", 
                context.getStepId(), context.getAttemptNumber(), duration);
        }
    }
    
    private boolean isRetryableException(Throwable failure, RetryPolicy policy) {
        Class<? extends Throwable> failureType = failure.getClass();
        
        // Check abort list first (takes precedence)
        Class<? extends Throwable>[] abortOn = policy.abortOn();
        if (abortOn.length > 0) {
            for (Class<? extends Throwable> abortType : abortOn) {
                if (abortType.isAssignableFrom(failureType)) {
                    return false; // Should not retry
                }
            }
        }
        
        // Check retry list
        Class<? extends Throwable>[] retryOn = policy.retryOn();
        if (retryOn.length == 0) {
            // If no specific exceptions specified, retry all (except those in abortOn)
            return true;
        }
        
        // Check if exception is in retry list
        for (Class<? extends Throwable> retryType : retryOn) {
            if (retryType.isAssignableFrom(failureType)) {
                return true;
            }
        }
        
        return false;
    }
}