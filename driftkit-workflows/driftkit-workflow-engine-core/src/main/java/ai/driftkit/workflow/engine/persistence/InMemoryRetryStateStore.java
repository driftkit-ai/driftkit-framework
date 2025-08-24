package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.domain.RetryContext;
import ai.driftkit.workflow.engine.core.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of RetryStateStore for testing and development.
 * This implementation is not suitable for production as state is lost on restart.
 */
@Slf4j
public class InMemoryRetryStateStore implements RetryStateStore {
    
    private final Map<String, RetryContext> retryContexts = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreaker.CircuitStateSnapshot> circuitBreakerStates = new ConcurrentHashMap<>();
    
    @Override
    public CompletableFuture<Void> saveRetryContext(String workflowId, String stepId, RetryContext context) {
        String key = createKey(workflowId, stepId);
        retryContexts.put(key, context);
        log.debug("Saved retry context for {}", key);
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Optional<RetryContext>> loadRetryContext(String workflowId, String stepId) {
        String key = createKey(workflowId, stepId);
        RetryContext context = retryContexts.get(key);
        log.debug("Loaded retry context for {}: {}", key, context != null ? "found" : "not found");
        return CompletableFuture.completedFuture(Optional.ofNullable(context));
    }
    
    @Override
    public CompletableFuture<Void> deleteRetryContext(String workflowId, String stepId) {
        String key = createKey(workflowId, stepId);
        retryContexts.remove(key);
        log.debug("Deleted retry context for {}", key);
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> saveCircuitBreakerState(String workflowId, String stepId, 
                                                          CircuitBreaker.CircuitStateSnapshot state) {
        String key = createKey(workflowId, stepId);
        circuitBreakerStates.put(key, state);
        log.debug("Saved circuit breaker state for {}: {}", key, state.state());
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Optional<CircuitBreaker.CircuitStateSnapshot>> loadCircuitBreakerState(String workflowId, 
                                                                                                   String stepId) {
        String key = createKey(workflowId, stepId);
        CircuitBreaker.CircuitStateSnapshot state = circuitBreakerStates.get(key);
        log.debug("Loaded circuit breaker state for {}: {}", key, 
                  state != null ? state.state() : "not found");
        return CompletableFuture.completedFuture(Optional.ofNullable(state));
    }
    
    @Override
    public CompletableFuture<Void> deleteWorkflowState(String workflowId) {
        // Remove all entries for this workflow
        retryContexts.entrySet().removeIf(entry -> entry.getKey().startsWith(workflowId + ":"));
        circuitBreakerStates.entrySet().removeIf(entry -> entry.getKey().startsWith(workflowId + ":"));
        log.debug("Deleted all retry state for workflow {}", workflowId);
        return CompletableFuture.completedFuture(null);
    }
    
    private String createKey(String workflowId, String stepId) {
        return workflowId + ":" + stepId;
    }
    
    /**
     * Clear all stored state (useful for testing).
     */
    public void clearAll() {
        retryContexts.clear();
        circuitBreakerStates.clear();
    }
}