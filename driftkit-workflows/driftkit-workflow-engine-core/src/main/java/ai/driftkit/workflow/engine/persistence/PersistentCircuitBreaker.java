package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.core.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Circuit breaker wrapper that adds persistence capabilities.
 * Automatically saves and loads circuit breaker state to/from a RetryStateStore.
 */
@Slf4j
public class PersistentCircuitBreaker extends CircuitBreaker {
    
    private final RetryStateStore stateStore;
    private final String workflowId;
    private final Set<String> loadedSteps = ConcurrentHashMap.newKeySet();
    private final boolean asyncPersistence;
    private final long persistenceTimeoutMs;
    
    /**
     * Creates a persistent circuit breaker with default configuration.
     * 
     * @param stateStore The state store for persistence
     * @param workflowId The workflow instance ID
     */
    public PersistentCircuitBreaker(RetryStateStore stateStore, String workflowId) {
        this(CircuitBreakerConfig.defaultConfig(), stateStore, workflowId, true, 5000);
    }
    
    /**
     * Creates a persistent circuit breaker with custom configuration.
     * 
     * @param config Circuit breaker configuration
     * @param stateStore The state store for persistence
     * @param workflowId The workflow instance ID
     * @param asyncPersistence Whether to persist state asynchronously
     * @param persistenceTimeoutMs Timeout for persistence operations
     */
    public PersistentCircuitBreaker(CircuitBreakerConfig config,
                                  RetryStateStore stateStore,
                                  String workflowId,
                                  boolean asyncPersistence,
                                  long persistenceTimeoutMs) {
        super(config);
        this.stateStore = stateStore;
        this.workflowId = workflowId;
        this.asyncPersistence = asyncPersistence;
        this.persistenceTimeoutMs = persistenceTimeoutMs;
    }
    
    @Override
    public boolean allowExecution(String stepId) {
        // Load state on first access
        ensureStateLoaded(stepId);
        
        boolean allowed = super.allowExecution(stepId);
        
        // Persist state changes
        persistState(stepId);
        
        return allowed;
    }
    
    @Override
    public void recordSuccess(String stepId) {
        ensureStateLoaded(stepId);
        super.recordSuccess(stepId);
        persistState(stepId);
    }
    
    @Override
    public void recordFailure(String stepId, Exception exception) {
        ensureStateLoaded(stepId);
        super.recordFailure(stepId, exception);
        persistState(stepId);
    }
    
    @Override
    public void reset(String stepId) {
        super.reset(stepId);
        loadedSteps.remove(stepId);
        
        // Delete persisted state
        CompletableFuture<Void> future = stateStore.deleteRetryContext(workflowId, stepId);
        
        if (!asyncPersistence) {
            try {
                future.get(persistenceTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("Failed to delete persisted state for step {}", stepId, e);
            }
        }
    }
    
    @Override
    public void resetAll() {
        super.resetAll();
        loadedSteps.clear();
        
        // Delete all persisted state for this workflow
        CompletableFuture<Void> future = stateStore.deleteWorkflowState(workflowId);
        
        if (!asyncPersistence) {
            try {
                future.get(persistenceTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("Failed to delete all persisted state for workflow {}", workflowId, e);
            }
        }
    }
    
    private void ensureStateLoaded(String stepId) {
        if (loadedSteps.contains(stepId)) {
            return;
        }
        
        try {
            stateStore.loadCircuitBreakerState(workflowId, stepId)
                .get(persistenceTimeoutMs, TimeUnit.MILLISECONDS)
                .ifPresent(state -> {
                    importState(stepId, state);
                    log.info("Loaded circuit breaker state for step {}: {}", stepId, state.state());
                });
            loadedSteps.add(stepId);
        } catch (Exception e) {
            log.error("Failed to load circuit breaker state for step {}", stepId, e);
            // Continue without loaded state
            loadedSteps.add(stepId);
        }
    }
    
    private void persistState(String stepId) {
        CircuitStateSnapshot snapshot = exportState(stepId);
        if (snapshot == null) {
            return;
        }
        
        CompletableFuture<Void> future = stateStore.saveCircuitBreakerState(workflowId, stepId, snapshot);
        
        if (!asyncPersistence) {
            try {
                future.get(persistenceTimeoutMs, TimeUnit.MILLISECONDS);
                log.debug("Persisted circuit breaker state for step {}: {}", stepId, snapshot.state());
            } catch (Exception e) {
                log.error("Failed to persist circuit breaker state for step {}", stepId, e);
            }
        } else {
            future.thenRun(() -> 
                log.debug("Async persisted circuit breaker state for step {}: {}", stepId, snapshot.state())
            ).exceptionally(e -> {
                log.error("Failed to async persist circuit breaker state for step {}", stepId, e);
                return null;
            });
        }
    }
    
    /**
     * Loads all persisted states for this workflow.
     * Useful for pre-loading state on workflow recovery.
     * 
     * @return Future that completes when loading is done
     */
    public CompletableFuture<Void> loadAllStates() {
        log.info("Loading all circuit breaker states for workflow {}", workflowId);
        // In a full implementation, this would iterate through all persisted states
        // For now, states are loaded on-demand
        return CompletableFuture.completedFuture(null);
    }
}