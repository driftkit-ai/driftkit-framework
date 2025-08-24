package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating WorkflowEngine instances with persistence capabilities.
 * This factory configures the engine with persistent retry executors and circuit breakers.
 */
public class PersistentWorkflowEngineFactory {
    
    /**
     * Configuration for persistent workflow engine.
     */
    @Getter
    @Builder
    public static class PersistenceConfig {
        @Builder.Default
        private RetryStateStore stateStore = new InMemoryRetryStateStore();
        
        @Builder.Default
        private boolean asyncPersistence = true;
        
        @Builder.Default
        private long persistenceTimeoutMs = 5000;
        
        @Builder.Default
        private boolean enableCircuitBreakerPersistence = true;
        
        @Builder.Default
        private boolean enableRetryContextPersistence = true;
        
        @Builder.Default
        private CircuitBreaker.CircuitBreakerConfig circuitBreakerConfig = 
            CircuitBreaker.CircuitBreakerConfig.defaultConfig();
    }
    
    /**
     * Creates a WorkflowEngineConfig with persistence enabled.
     * 
     * @param workflowId The workflow instance ID
     * @param persistenceConfig The persistence configuration
     * @return The configured WorkflowEngineConfig
     */
    public static WorkflowEngineConfig createPersistentConfig(String workflowId, 
                                                            PersistenceConfig persistenceConfig) {
        // Create circuit breaker
        CircuitBreaker circuitBreaker;
        if (persistenceConfig.enableCircuitBreakerPersistence) {
            circuitBreaker = new PersistentCircuitBreaker(
                persistenceConfig.circuitBreakerConfig,
                persistenceConfig.stateStore,
                workflowId,
                persistenceConfig.asyncPersistence,
                persistenceConfig.persistenceTimeoutMs
            );
        } else {
            circuitBreaker = new CircuitBreaker(persistenceConfig.circuitBreakerConfig);
        }
        
        // Create retry listeners
        List<RetryListener> listeners = new ArrayList<>();
        if (persistenceConfig.enableRetryContextPersistence) {
            listeners.add(new RetryStatePersistenceListener(
                persistenceConfig.stateStore,
                workflowId,
                persistenceConfig.asyncPersistence,
                persistenceConfig.persistenceTimeoutMs
            ));
        }
        
        // Create retry executor
        RetryExecutor retryExecutor = new RetryExecutor(
            new ConditionalRetryStrategy(),
            circuitBreaker,
            new RetryMetrics(),
            listeners
        );
        
        return WorkflowEngineConfig.builder()
            .retryExecutor(retryExecutor)
            .build();
    }
    
    /**
     * Creates a WorkflowEngine with persistence enabled using default settings.
     * 
     * @param workflowId The workflow instance ID
     * @return The configured WorkflowEngine
     */
    public static WorkflowEngine createPersistentEngine(String workflowId) {
        return createPersistentEngine(workflowId, PersistenceConfig.builder().build());
    }
    
    /**
     * Creates a WorkflowEngine with persistence enabled.
     * 
     * @param workflowId The workflow instance ID
     * @param persistenceConfig The persistence configuration
     * @return The configured WorkflowEngine
     */
    public static WorkflowEngine createPersistentEngine(String workflowId, 
                                                       PersistenceConfig persistenceConfig) {
        WorkflowEngineConfig config = createPersistentConfig(workflowId, persistenceConfig);
        return new WorkflowEngine(config);
    }
    
    /**
     * Recovers retry state for a workflow from persistence.
     * 
     * @param workflowId The workflow instance ID
     * @param persistenceConfig The persistence configuration
     * @return The RetryStatePersistenceListener that can be used to load persisted contexts
     */
    public static RetryStatePersistenceListener recoverRetryState(String workflowId,
                                                                 PersistenceConfig persistenceConfig) {
        return new RetryStatePersistenceListener(
            persistenceConfig.stateStore,
            workflowId,
            persistenceConfig.asyncPersistence,
            persistenceConfig.persistenceTimeoutMs
        );
    }
}