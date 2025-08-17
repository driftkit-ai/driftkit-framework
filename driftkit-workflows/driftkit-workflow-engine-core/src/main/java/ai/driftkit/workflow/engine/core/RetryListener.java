package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import ai.driftkit.workflow.engine.domain.RetryContext;

/**
 * Listener interface for retry events.
 * Allows monitoring and reacting to retry attempts, successes, and failures.
 */
public interface RetryListener {
    
    /**
     * Called before a retry attempt is made.
     * 
     * @param stepId The step being retried
     * @param retryContext The current retry context
     * @param retryPolicy The retry policy being applied
     */
    default void beforeRetry(String stepId, RetryContext retryContext, RetryPolicy retryPolicy) {
        // Default no-op implementation
    }
    
    /**
     * Called after a retry attempt completes successfully.
     * 
     * @param stepId The step that was retried
     * @param retryContext The final retry context
     * @param result The successful result
     */
    default void onRetrySuccess(String stepId, RetryContext retryContext, Object result) {
        // Default no-op implementation
    }
    
    /**
     * Called after a retry attempt fails.
     * 
     * @param stepId The step that failed
     * @param retryContext The current retry context
     * @param exception The exception that occurred
     * @param willRetry Whether another retry will be attempted
     */
    default void onRetryFailure(String stepId, RetryContext retryContext, 
                               Exception exception, boolean willRetry) {
        // Default no-op implementation
    }
    
    /**
     * Called when all retry attempts have been exhausted.
     * 
     * @param stepId The step that exhausted retries
     * @param retryContext The final retry context
     * @param lastException The last exception that occurred
     */
    default void onRetryExhausted(String stepId, RetryContext retryContext, Exception lastException) {
        // Default no-op implementation
    }
    
    /**
     * Called when retry is aborted due to a non-retryable exception.
     * 
     * @param stepId The step that was aborted
     * @param retryContext The retry context at abort time
     * @param exception The non-retryable exception
     */
    default void onRetryAborted(String stepId, RetryContext retryContext, Exception exception) {
        // Default no-op implementation
    }
}