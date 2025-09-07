package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import ai.driftkit.workflow.engine.core.RetryListener;
import ai.driftkit.workflow.engine.domain.RetryContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Retry listener that persists retry state to enable recovery after failures.
 * This listener integrates with the RetryExecutor to save state at key points.
 */
@Slf4j
@RequiredArgsConstructor
public class RetryStatePersistenceListener implements RetryListener {
    
    private final RetryStateStore stateStore;
    private final String workflowId;
    private final boolean asyncPersistence;
    private final long persistenceTimeoutMs;
    
    /**
     * Creates a persistence listener with default settings.
     * 
     * @param stateStore The state store for persistence
     * @param workflowId The workflow instance ID
     */
    public RetryStatePersistenceListener(RetryStateStore stateStore, String workflowId) {
        this(stateStore, workflowId, true, 5000);
    }
    
    @Override
    public void beforeRetry(String stepId, RetryContext retryContext, RetryPolicy retryPolicy) {
        // Save retry context before each attempt
        persistRetryContext(stepId, retryContext);
    }
    
    @Override
    public void onRetrySuccess(String stepId, RetryContext retryContext, Object result) {
        // Clean up persisted state on success
        cleanupPersistedState(stepId);
    }
    
    @Override
    public void onRetryFailure(String stepId, RetryContext retryContext, Exception exception, boolean willRetry) {
        if (!willRetry) {
            // Clean up if no more retries will be attempted
            cleanupPersistedState(stepId);
        } else {
            // Update persisted context with failure info
            persistRetryContext(stepId, retryContext);
        }
    }
    
    @Override
    public void onRetryExhausted(String stepId, RetryContext retryContext, Exception lastException) {
        // Clean up persisted state when retries are exhausted
        cleanupPersistedState(stepId);
    }
    
    @Override
    public void onRetryAborted(String stepId, RetryContext retryContext, Exception exception) {
        // Clean up persisted state when retry is aborted
        cleanupPersistedState(stepId);
    }
    
    private void persistRetryContext(String stepId, RetryContext context) {
        CompletableFuture<Void> future = stateStore.saveRetryContext(workflowId, stepId, context);
        
        if (!asyncPersistence) {
            try {
                future.get(persistenceTimeoutMs, TimeUnit.MILLISECONDS);
                log.debug("Persisted retry context for step {} at attempt {}", 
                         stepId, context.getAttemptNumber());
            } catch (Exception e) {
                log.error("Failed to persist retry context for step {}", stepId, e);
                // Continue execution even if persistence fails
            }
        } else {
            future.thenRun(() -> 
                log.debug("Async persisted retry context for step {} at attempt {}", 
                         stepId, context.getAttemptNumber())
            ).exceptionally(e -> {
                log.error("Failed to async persist retry context for step {}", stepId, e);
                return null;
            });
        }
    }
    
    private void cleanupPersistedState(String stepId) {
        CompletableFuture<Void> future = stateStore.deleteRetryContext(workflowId, stepId);
        
        if (!asyncPersistence) {
            try {
                future.get(persistenceTimeoutMs, TimeUnit.MILLISECONDS);
                log.debug("Cleaned up persisted state for step {}", stepId);
            } catch (Exception e) {
                log.error("Failed to cleanup persisted state for step {}", stepId, e);
            }
        } else {
            future.thenRun(() -> 
                log.debug("Async cleaned up persisted state for step {}", stepId)
            ).exceptionally(e -> {
                log.error("Failed to async cleanup persisted state for step {}", stepId, e);
                return null;
            });
        }
    }
    
    /**
     * Loads persisted retry context for a step.
     * 
     * @param stepId The step ID
     * @return The persisted retry context, or null if none exists
     */
    public RetryContext loadPersistedContext(String stepId) {
        try {
            return stateStore.loadRetryContext(workflowId, stepId)
                .get(persistenceTimeoutMs, TimeUnit.MILLISECONDS)
                .orElse(null);
        } catch (Exception e) {
            log.error("Failed to load persisted retry context for step {}", stepId, e);
            return null;
        }
    }
}