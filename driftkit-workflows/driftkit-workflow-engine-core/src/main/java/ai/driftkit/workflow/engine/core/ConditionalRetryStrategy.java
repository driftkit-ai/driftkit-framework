package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import ai.driftkit.workflow.engine.domain.RetryContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Retry strategy that applies conditions based on exception types.
 * Extends the default strategy with conditional retry logic.
 */
@Slf4j
public class ConditionalRetryStrategy extends DefaultRetryStrategy {
    
    @Override
    public boolean shouldRetry(Throwable exception, RetryContext context, RetryPolicy policy) {
        // Check if we have attempts remaining
        if (context.getAttemptNumber() >= policy.maxAttempts()) {
            log.debug("No more retry attempts for step {}: {}/{}", 
                context.getStepId(), context.getAttemptNumber(), policy.maxAttempts());
            return false;
        }
        
        // Extract the root cause
        Throwable rootCause = getRootCause(exception);
        Class<? extends Throwable> exceptionClass = rootCause.getClass();
        
        // Check abort conditions first (takes precedence)
        Class<? extends Throwable>[] abortOn = policy.abortOn();
        if (abortOn.length > 0) {
            Set<Class<? extends Throwable>> abortSet = new HashSet<>(Arrays.asList(abortOn));
            if (matchesAnyInChain(exception, abortSet)) {
                log.debug("Exception {} or its cause matches abort condition, not retrying", exceptionClass.getSimpleName());
                return false;
            }
        }
        
        // Check retry conditions
        Class<? extends Throwable>[] retryOn = policy.retryOn();
        if (retryOn.length > 0) {
            Set<Class<? extends Throwable>> retrySet = new HashSet<>(Arrays.asList(retryOn));
            boolean shouldRetry = matchesAnyInChain(exception, retrySet);
            if (!shouldRetry) {
                log.debug("Exception {} and its causes do not match retry conditions {}", 
                    exceptionClass.getSimpleName(), retrySet);
                // Debug: print exception chain
                Throwable current = exception;
                while (current != null) {
                    log.debug("  - Exception in chain: {}", current.getClass().getName());
                    current = current.getCause();
                }
            }
            return shouldRetry;
        }
        
        // If no specific conditions, allow retry (default behavior)
        return true;
    }
    
    /**
     * Gets the root cause of an exception chain.
     * 
     * @param throwable The throwable to analyze
     * @return The root cause
     */
    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
    
    /**
     * Checks if an exception class matches any in the set, considering inheritance.
     * Also checks the entire exception chain for matches.
     * 
     * @param exceptionClass The exception class to check
     * @param targetClasses The set of target classes
     * @return true if there's a match
     */
    private boolean matchesAny(Class<? extends Throwable> exceptionClass, 
                              Set<Class<? extends Throwable>> targetClasses) {
        // Check direct match
        for (Class<? extends Throwable> targetClass : targetClasses) {
            if (targetClass.isAssignableFrom(exceptionClass)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if any exception in the chain matches the target classes.
     * 
     * @param throwable The throwable to check (including its causes)
     * @param targetClasses The set of target classes
     * @return true if there's a match in the chain
     */
    private boolean matchesAnyInChain(Throwable throwable, 
                                     Set<Class<? extends Throwable>> targetClasses) {
        Throwable current = throwable;
        while (current != null) {
            if (matchesAny(current.getClass(), targetClasses)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}