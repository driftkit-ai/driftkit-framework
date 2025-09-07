package ai.driftkit.workflow.engine.config;

import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import ai.driftkit.workflow.engine.core.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple configuration manager for retry policies and circuit breakers.
 * Allows runtime updates to retry configurations.
 */
@Slf4j
public class RetryConfigurationManager {
    
    private final Map<String, RetryPolicy> stepRetryPolicies = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreaker.CircuitBreakerConfig> stepCircuitBreakerConfigs = new ConcurrentHashMap<>();
    
    /**
     * Sets retry policy for a specific step.
     */
    public void setStepRetryPolicy(String stepId, RetryPolicy policy) {
        stepRetryPolicies.put(stepId, policy);
        log.debug("Set retry policy for step {}: maxAttempts={}, delay={}", 
                stepId, policy.maxAttempts(), policy.delay());
    }
    
    /**
     * Gets retry policy for a specific step.
     */
    public RetryPolicy getStepRetryPolicy(String stepId) {
        return stepRetryPolicies.get(stepId);
    }
    
    /**
     * Sets circuit breaker config for a specific step.
     */
    public void setStepCircuitBreakerConfig(String stepId, CircuitBreaker.CircuitBreakerConfig config) {
        stepCircuitBreakerConfigs.put(stepId, config);
        log.debug("Set circuit breaker config for step {}: failureThreshold={}", 
                stepId, config.getFailureThreshold());
    }
    
    /**
     * Gets circuit breaker config for a specific step.
     */
    public CircuitBreaker.CircuitBreakerConfig getStepCircuitBreakerConfig(String stepId) {
        return stepCircuitBreakerConfigs.get(stepId);
    }
    
    /**
     * Clears all configurations.
     */
    public void clear() {
        stepRetryPolicies.clear();
        stepCircuitBreakerConfigs.clear();
    }
}