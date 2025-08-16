package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import ai.driftkit.workflow.engine.domain.RetryContext;

/**
 * Strategy interface for determining retry behavior.
 * Implementations can provide different retry algorithms such as
 * exponential backoff, linear delay, or custom strategies.
 */
public interface RetryStrategy {
    
    /**
     * Determines whether a retry should be attempted based on the failure and context.
     * 
     * @param failure The exception that caused the failure
     * @param context The current retry context
     * @param policy The retry policy configuration
     * @return True if retry should be attempted, false otherwise
     */
    boolean shouldRetry(Throwable failure, RetryContext context, RetryPolicy policy);
    
    /**
     * Calculates the delay before the next retry attempt.
     * 
     * @param context The current retry context
     * @param policy The retry policy configuration
     * @return The delay in milliseconds before the next attempt
     */
    long calculateDelay(RetryContext context, RetryPolicy policy);
    
    /**
     * Called before a retry attempt is made.
     * Can be used for logging or metrics collection.
     * 
     * @param context The current retry context
     * @param delay The calculated delay before this retry
     */
    default void beforeRetry(RetryContext context, long delay) {
        // Default implementation does nothing
    }
    
    /**
     * Called after a retry attempt completes (success or failure).
     * 
     * @param context The current retry context
     * @param success True if the retry succeeded, false if it failed
     * @param duration The duration of the retry attempt in milliseconds
     */
    default void afterRetry(RetryContext context, boolean success, long duration) {
        // Default implementation does nothing
    }
}