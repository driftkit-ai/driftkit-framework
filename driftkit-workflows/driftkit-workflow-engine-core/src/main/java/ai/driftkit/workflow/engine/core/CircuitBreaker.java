package ai.driftkit.workflow.engine.core;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit breaker implementation for workflow steps.
 * Prevents cascading failures by temporarily blocking execution of failing steps.
 * 
 * States:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Failure threshold exceeded, requests are blocked
 * - HALF_OPEN: Testing if the service has recovered
 */
@Slf4j
public class CircuitBreaker {
    
    private final ConcurrentHashMap<String, CircuitState> circuitStates = new ConcurrentHashMap<>();
    private final CircuitBreakerConfig config;
    
    public CircuitBreaker(CircuitBreakerConfig config) {
        this.config = config;
    }
    
    public CircuitBreaker() {
        this(CircuitBreakerConfig.defaultConfig());
    }
    
    /**
     * Checks if the circuit allows execution for the given step.
     * 
     * @param stepId The step identifier
     * @return true if execution is allowed, false if circuit is open
     */
    public boolean allowExecution(String stepId) {
        CircuitState state = circuitStates.computeIfAbsent(stepId, 
            k -> new CircuitState(config));
        
        return state.allowExecution();
    }
    
    /**
     * Records a successful execution.
     * 
     * @param stepId The step identifier
     */
    public void recordSuccess(String stepId) {
        CircuitState state = circuitStates.get(stepId);
        if (state != null) {
            state.recordSuccess();
        }
    }
    
    /**
     * Records a failed execution.
     * 
     * @param stepId The step identifier
     * @param exception The exception that occurred
     */
    public void recordFailure(String stepId, Exception exception) {
        CircuitState state = circuitStates.computeIfAbsent(stepId, 
            k -> new CircuitState(config));
        state.recordFailure(exception);
    }
    
    /**
     * Gets the current state of a circuit.
     * 
     * @param stepId The step identifier
     * @return The current circuit state
     */
    public State getState(String stepId) {
        CircuitState state = circuitStates.get(stepId);
        return state != null ? state.getState() : State.CLOSED;
    }
    
    /**
     * Resets the circuit breaker for a specific step.
     * 
     * @param stepId The step identifier
     */
    public void reset(String stepId) {
        circuitStates.remove(stepId);
    }
    
    /**
     * Resets all circuit breakers.
     */
    public void resetAll() {
        circuitStates.clear();
    }
    
    /**
     * Circuit breaker states.
     */
    public enum State {
        CLOSED,     // Normal operation
        OPEN,       // Blocking requests
        HALF_OPEN   // Testing recovery
    }
    
    /**
     * Configuration for circuit breaker behavior.
     */
    @Getter
    @Builder
    public static class CircuitBreakerConfig {
        @Builder.Default
        private final int failureThreshold = 5;
        
        @Builder.Default
        private final int successThreshold = 2;
        
        @Builder.Default
        private final long openDurationMs = 60000; // 1 minute
        
        @Builder.Default
        private final long halfOpenDurationMs = 30000; // 30 seconds
        
        @Builder.Default
        private final int halfOpenMaxAttempts = 3;
        
        public static CircuitBreakerConfig defaultConfig() {
            return CircuitBreakerConfig.builder().build();
        }
    }
    
    /**
     * Internal state management for a single circuit.
     */
    private static class CircuitState {
        private final CircuitBreakerConfig config;
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger halfOpenAttempts = new AtomicInteger(0);
        private final AtomicLong lastFailureTime = new AtomicLong(0);
        private final AtomicLong stateChangeTime = new AtomicLong(System.currentTimeMillis());
        private volatile State state = State.CLOSED;
        
        CircuitState(CircuitBreakerConfig config) {
            this.config = config;
        }
        
        synchronized boolean allowExecution() {
            long now = System.currentTimeMillis();
            
            switch (state) {
                case CLOSED:
                    return true;
                    
                case OPEN:
                    // Check if we should transition to half-open
                    if (now - stateChangeTime.get() >= config.openDurationMs) {
                        transitionTo(State.HALF_OPEN);
                        halfOpenAttempts.set(1); // Count this as the first attempt
                        return true; // Allow one attempt
                    }
                    return false;
                    
                case HALF_OPEN:
                    // Allow limited attempts in half-open state
                    int currentAttempts = halfOpenAttempts.get();
                    if (currentAttempts < config.halfOpenMaxAttempts) {
                        halfOpenAttempts.incrementAndGet();
                        return true;
                    }
                    // If we've exhausted half-open attempts without success, go back to open
                    if (now - stateChangeTime.get() >= config.halfOpenDurationMs) {
                        transitionTo(State.OPEN);
                    }
                    return false;
                    
                default:
                    return false;
            }
        }
        
        synchronized void recordSuccess() {
            successCount.incrementAndGet();
            
            switch (state) {
                case HALF_OPEN:
                    if (successCount.get() >= config.successThreshold) {
                        // Recovered successfully
                        transitionTo(State.CLOSED);
                        failureCount.set(0);
                        successCount.set(0);
                    }
                    break;
                    
                case CLOSED:
                    // Reset failure count on success in closed state
                    failureCount.set(0);
                    break;
                    
                default:
                    // No action in OPEN state
                    break;
            }
        }
        
        synchronized void recordFailure(Exception exception) {
            lastFailureTime.set(System.currentTimeMillis());
            failureCount.incrementAndGet();
            
            switch (state) {
                case CLOSED:
                    if (failureCount.get() >= config.failureThreshold) {
                        transitionTo(State.OPEN);
                        log.warn("Circuit breaker opened due to {} failures", failureCount.get());
                    }
                    break;
                    
                case HALF_OPEN:
                    // Any failure in half-open state sends us back to open
                    transitionTo(State.OPEN);
                    log.warn("Circuit breaker reopened due to failure in half-open state");
                    break;
                    
                default:
                    // Already open
                    break;
            }
        }
        
        private void transitionTo(State newState) {
            State oldState = this.state;
            this.state = newState;
            stateChangeTime.set(System.currentTimeMillis());
            
            if (newState == State.CLOSED) {
                failureCount.set(0);
                successCount.set(0);
                halfOpenAttempts.set(0);
            }
            
            log.info("Circuit breaker state transition: {} -> {}", oldState, newState);
        }
        
        State getState() {
            return state;
        }
    }
}