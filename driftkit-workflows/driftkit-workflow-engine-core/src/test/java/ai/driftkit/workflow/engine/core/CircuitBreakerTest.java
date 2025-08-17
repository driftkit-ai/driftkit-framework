package ai.driftkit.workflow.engine.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CircuitBreaker functionality.
 */
class CircuitBreakerTest {
    
    private CircuitBreaker circuitBreaker;
    
    @BeforeEach
    void setUp() {
        CircuitBreaker.CircuitBreakerConfig config = CircuitBreaker.CircuitBreakerConfig.builder()
            .failureThreshold(3)
            .successThreshold(2)
            .openDurationMs(1000)
            .halfOpenDurationMs(500)
            .halfOpenMaxAttempts(2)
            .build();
        circuitBreaker = new CircuitBreaker(config);
    }
    
    @Nested
    @DisplayName("State Transition Tests")
    class StateTransitionTests {
        
        @Test
        @DisplayName("Should start in CLOSED state")
        void shouldStartClosed() {
            assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState("test-step"));
            assertTrue(circuitBreaker.allowExecution("test-step"));
        }
        
        @Test
        @DisplayName("Should transition to OPEN after failure threshold")
        void shouldOpenAfterFailures() {
            String stepId = "failing-step";
            
            // Record failures up to threshold
            for (int i = 0; i < 3; i++) {
                assertTrue(circuitBreaker.allowExecution(stepId));
                circuitBreaker.recordFailure(stepId, new RuntimeException("Test failure " + i));
            }
            
            // Circuit should now be open
            assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState(stepId));
            assertFalse(circuitBreaker.allowExecution(stepId));
        }
        
        @Test
        @DisplayName("Should transition to HALF_OPEN after timeout")
        void shouldTransitionToHalfOpen() throws InterruptedException {
            String stepId = "timeout-step";
            
            // Open the circuit
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure(stepId, new RuntimeException());
            }
            
            assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState(stepId));
            assertFalse(circuitBreaker.allowExecution(stepId));
            
            // Wait for open duration
            Thread.sleep(1100);
            
            // Should transition to half-open and allow one attempt
            assertTrue(circuitBreaker.allowExecution(stepId));
            assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState(stepId));
        }
        
        @Test
        @DisplayName("Should close after success threshold in HALF_OPEN")
        void shouldCloseAfterSuccessInHalfOpen() throws InterruptedException {
            String stepId = "recovery-step";
            
            // Open the circuit
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure(stepId, new RuntimeException());
            }
            
            // Wait to transition to half-open
            Thread.sleep(1100);
            assertTrue(circuitBreaker.allowExecution(stepId));
            
            // Record successes
            circuitBreaker.recordSuccess(stepId);
            assertTrue(circuitBreaker.allowExecution(stepId));
            circuitBreaker.recordSuccess(stepId);
            
            // Should be closed now
            assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState(stepId));
            assertTrue(circuitBreaker.allowExecution(stepId));
        }
    }
    
    @Nested
    @DisplayName("Behavior Tests")
    class BehaviorTests {
        
        @Test
        @DisplayName("Should reset failure count on success in CLOSED state")
        void shouldResetFailuresOnSuccess() {
            String stepId = "reset-step";
            
            // Record some failures (but not enough to open)
            circuitBreaker.recordFailure(stepId, new RuntimeException());
            circuitBreaker.recordFailure(stepId, new RuntimeException());
            
            // Record a success
            circuitBreaker.recordSuccess(stepId);
            
            // Should still be closed and failures reset
            assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState(stepId));
            
            // Can now fail 3 more times before opening
            for (int i = 0; i < 2; i++) {
                circuitBreaker.recordFailure(stepId, new RuntimeException());
                assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState(stepId));
            }
            
            // Third failure should open
            circuitBreaker.recordFailure(stepId, new RuntimeException());
            assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState(stepId));
        }
        
        @Test
        @DisplayName("Should limit attempts in HALF_OPEN state")
        void shouldLimitHalfOpenAttempts() throws InterruptedException {
            String stepId = "limit-step";
            
            // Open the circuit
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure(stepId, new RuntimeException());
            }
            
            // Wait to transition to half-open
            Thread.sleep(1100);
            
            // Should allow limited attempts (configured as 2)
            assertTrue(circuitBreaker.allowExecution(stepId));
            assertTrue(circuitBreaker.allowExecution(stepId));
            
            // Should not allow more
            assertFalse(circuitBreaker.allowExecution(stepId));
        }
        
        @Test
        @DisplayName("Should reopen on failure in HALF_OPEN state")
        void shouldReopenOnHalfOpenFailure() throws InterruptedException {
            String stepId = "reopen-step";
            
            // Open the circuit
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure(stepId, new RuntimeException());
            }
            
            // Wait to transition to half-open
            Thread.sleep(1100);
            assertTrue(circuitBreaker.allowExecution(stepId));
            
            // Fail in half-open state
            circuitBreaker.recordFailure(stepId, new RuntimeException());
            
            // Should be open again
            assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState(stepId));
            assertFalse(circuitBreaker.allowExecution(stepId));
        }
    }
    
    @Nested
    @DisplayName("Management Tests")
    class ManagementTests {
        
        @Test
        @DisplayName("Should reset individual circuit")
        void shouldResetIndividualCircuit() {
            String stepId = "reset-individual";
            
            // Open circuit
            for (int i = 0; i < 3; i++) {
                circuitBreaker.recordFailure(stepId, new RuntimeException());
            }
            
            assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState(stepId));
            
            // Reset
            circuitBreaker.reset(stepId);
            
            // Should be closed again
            assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState(stepId));
            assertTrue(circuitBreaker.allowExecution(stepId));
        }
        
        @Test
        @DisplayName("Should reset all circuits")
        void shouldResetAllCircuits() {
            // Open multiple circuits
            for (String step : new String[]{"step1", "step2", "step3"}) {
                for (int i = 0; i < 3; i++) {
                    circuitBreaker.recordFailure(step, new RuntimeException());
                }
                assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState(step));
            }
            
            // Reset all
            circuitBreaker.resetAll();
            
            // All should be closed
            for (String step : new String[]{"step1", "step2", "step3"}) {
                assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState(step));
                assertTrue(circuitBreaker.allowExecution(step));
            }
        }
    }
    
    @Test
    @DisplayName("Should use default configuration")
    void shouldUseDefaultConfig() {
        CircuitBreaker defaultBreaker = new CircuitBreaker();
        assertTrue(defaultBreaker.allowExecution("test"));
        
        // Default threshold is 5
        for (int i = 0; i < 4; i++) {
            defaultBreaker.recordFailure("test", new RuntimeException());
            assertTrue(defaultBreaker.allowExecution("test"));
        }
        
        // 5th failure should open
        defaultBreaker.recordFailure("test", new RuntimeException());
        assertFalse(defaultBreaker.allowExecution("test"));
    }
}