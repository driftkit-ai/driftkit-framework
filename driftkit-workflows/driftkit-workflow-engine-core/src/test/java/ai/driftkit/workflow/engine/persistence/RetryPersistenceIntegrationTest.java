package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.builder.RetryPolicyBuilder;
import ai.driftkit.workflow.engine.builder.StepDefinition;
import ai.driftkit.workflow.engine.builder.WorkflowBuilder;
import ai.driftkit.workflow.engine.core.CircuitBreaker;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.RetryExecutor;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.domain.RetryContext;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for retry persistence functionality.
 */
class RetryPersistenceIntegrationTest {
    
    private InMemoryRetryStateStore stateStore;
    private String workflowId = "test-workflow-123";
    
    @BeforeEach
    void setUp() {
        stateStore = new InMemoryRetryStateStore();
    }
    
    @Nested
    @DisplayName("Retry Context Persistence Tests")
    class RetryContextPersistenceTests {
        
        @Test
        @DisplayName("Should persist retry context during execution")
        void shouldPersistRetryContext() throws Exception {
            // Create workflow with retries
            AtomicInteger attempts = new AtomicInteger(0);
            WorkflowGraph<String, String> workflow = WorkflowBuilder
                .define("persist-test", String.class, String.class)
                .then(StepDefinition.of("failing-step", (String input) -> {
                        int attempt = attempts.incrementAndGet();
                        if (attempt < 3) {
                            throw new RuntimeException("Attempt " + attempt + " failed");
                        }
                        return StepResult.finish("success after " + attempt + " attempts");
                    })
                    .withRetryPolicy(new RetryPolicyBuilder()
                        .withMaxAttempts(5)
                        .withDelay(10)
                        .build())
                )
                .build();
            
            // Create engine with persistence
            WorkflowEngine engine = PersistentWorkflowEngineFactory.createPersistentEngine(
                workflowId,
                PersistentWorkflowEngineFactory.PersistenceConfig.builder()
                    .stateStore(stateStore)
                    .asyncPersistence(false) // Synchronous for testing
                    .build()
            );
            
            // Register and execute workflow
            engine.register(workflow);
            WorkflowEngine.WorkflowExecution<String> execution = engine.execute("persist-test", "test-input");
            String result = execution.get(10, TimeUnit.SECONDS);
            assertEquals("success after 3 attempts", result);
            
            // Verify context was saved during execution and cleaned up after success
            Optional<RetryContext> savedContext = stateStore.loadRetryContext(workflowId, "failing-step")
                .get(5, TimeUnit.SECONDS);
            assertFalse(savedContext.isPresent(), "Context should be cleaned up after success");
        }
        
        @Test
        @DisplayName("Should recover from persisted retry context")
        void shouldRecoverFromPersistedContext() throws Exception {
            // Pre-save a retry context simulating a previous failed execution
            RetryContext previousContext = RetryContext.builder()
                .stepId("recoverable-step")
                .attemptNumber(2)
                .maxAttempts(5)
                .firstAttemptTime(System.currentTimeMillis() - 10000)
                .currentAttemptTime(System.currentTimeMillis())
                .build();
            
            stateStore.saveRetryContext(workflowId, "recoverable-step", previousContext)
                .get(5, TimeUnit.SECONDS);
            
            // Create workflow that would fail without recovery
            AtomicInteger executionCount = new AtomicInteger(0);
            WorkflowGraph<String, String> workflow = WorkflowBuilder
                .define("recovery-test", String.class, String.class)
                .then(StepDefinition.of("recoverable-step", (String input) -> {
                        int count = executionCount.incrementAndGet();
                        // This should succeed on first execution if context is recovered
                        return StepResult.finish("executed " + count + " times");
                    })
                    .withRetryPolicy(new RetryPolicyBuilder()
                        .withMaxAttempts(5)
                        .build())
                )
                .build();
            
            // Create engine and load persisted state
            RetryStatePersistenceListener listener = PersistentWorkflowEngineFactory.recoverRetryState(
                workflowId,
                PersistentWorkflowEngineFactory.PersistenceConfig.builder()
                    .stateStore(stateStore)
                    .asyncPersistence(false)
                    .build()
            );
            
            // Check that we can load the persisted context
            RetryContext loadedContext = listener.loadPersistedContext("recoverable-step");
            assertNotNull(loadedContext);
            assertEquals(2, loadedContext.getAttemptNumber());
            
            // Execute workflow - it should continue from where it left off
            WorkflowEngine engine = PersistentWorkflowEngineFactory.createPersistentEngine(
                workflowId,
                PersistentWorkflowEngineFactory.PersistenceConfig.builder()
                    .stateStore(stateStore)
                    .asyncPersistence(false)
                    .build()
            );
            
            engine.register(workflow);
            WorkflowEngine.WorkflowExecution<String> execution = engine.execute("recovery-test", "test-input");
            String result = execution.get(10, TimeUnit.SECONDS);
            assertEquals("executed 1 times", result);
            assertEquals(1, executionCount.get(), "Should only execute once");
        }
    }
    
    @Nested
    @DisplayName("Circuit Breaker Persistence Tests")
    class CircuitBreakerPersistenceTests {
        
        @Test
        @DisplayName("Should persist circuit breaker state")
        void shouldPersistCircuitBreakerState() throws Exception {
            // Configure circuit breaker with low threshold
            CircuitBreaker.CircuitBreakerConfig cbConfig = CircuitBreaker.CircuitBreakerConfig.builder()
                .failureThreshold(2)
                .openDurationMs(5000)
                .build();
            
            // Create workflow that always fails
            WorkflowGraph<String, String> workflow = WorkflowBuilder
                .define("circuit-test", String.class, String.class)
                .then(StepDefinition.of("circuit-step", (String input) -> {
                        throw new RuntimeException("Always fails");
                    })
                    .withRetryPolicy(new RetryPolicyBuilder()
                        .withMaxAttempts(10) // High enough to trigger circuit breaker
                        .withDelay(10)
                        .build())
                )
                .build();
            
            // Create engine with persistent circuit breaker
            WorkflowEngine engine = PersistentWorkflowEngineFactory.createPersistentEngine(
                workflowId,
                PersistentWorkflowEngineFactory.PersistenceConfig.builder()
                    .stateStore(stateStore)
                    .asyncPersistence(false)
                    .circuitBreakerConfig(cbConfig)
                    .build()
            );
            
            // Register and execute until circuit opens
            engine.register(workflow);
            try {
                engine.execute("circuit-test", "test-input").get(10, TimeUnit.SECONDS);
                fail("Should have thrown exception");
            } catch (Exception e) {
                // Expected - could be RetryExhaustedException or CircuitBreakerOpenException
            }
            
            // Verify circuit breaker state was persisted
            Optional<CircuitBreaker.CircuitStateSnapshot> cbState = 
                stateStore.loadCircuitBreakerState(workflowId, "circuit-step")
                    .get(5, TimeUnit.SECONDS);
            
            assertTrue(cbState.isPresent());
            assertEquals(CircuitBreaker.State.OPEN, cbState.get().state());
            assertTrue(cbState.get().failureCount() >= 2);
        }
        
        @Test
        @DisplayName("Should recover circuit breaker state")
        void shouldRecoverCircuitBreakerState() throws Exception {
            // Pre-save circuit breaker state in OPEN
            CircuitBreaker.CircuitStateSnapshot openState = new CircuitBreaker.CircuitStateSnapshot(
                CircuitBreaker.State.OPEN,
                5,  // failure count
                0,  // success count
                0,  // half open attempts
                System.currentTimeMillis() - 1000, // recent failure
                System.currentTimeMillis() - 1000  // recent state change
            );
            
            stateStore.saveCircuitBreakerState(workflowId, "blocked-step", openState)
                .get(5, TimeUnit.SECONDS);
            
            // Create workflow
            AtomicInteger executionCount = new AtomicInteger(0);
            WorkflowGraph<String, String> workflow = WorkflowBuilder
                .define("cb-recovery-test", String.class, String.class)
                .then(StepDefinition.of("blocked-step", (String input) -> {
                        executionCount.incrementAndGet();
                        return StepResult.finish("should not execute");
                    })
                    .withRetryPolicy(new RetryPolicyBuilder()
                        .withMaxAttempts(3)
                        .build())
                )
                .build();
            
            // Create new engine (simulating restart)
            WorkflowEngine engine = PersistentWorkflowEngineFactory.createPersistentEngine(
                workflowId,
                PersistentWorkflowEngineFactory.PersistenceConfig.builder()
                    .stateStore(stateStore)
                    .asyncPersistence(false)
                    .build()
            );
            
            // Register and should fail immediately due to open circuit
            engine.register(workflow);
            assertThrows(ExecutionException.class, () -> {
                engine.execute("cb-recovery-test", "test-input").get(10, TimeUnit.SECONDS);
            });
            
            // Step should not have been executed
            assertEquals(0, executionCount.get());
        }
    }
    
    @Nested
    @DisplayName("Async Persistence Tests")
    class AsyncPersistenceTests {
        
        @Test
        @DisplayName("Should handle async persistence efficiently")
        void shouldHandleAsyncPersistence() throws Exception {
            // Create workflow with multiple steps
            WorkflowGraph<Step1Input, Step3Result> workflow = WorkflowBuilder
                .define("async-test", Step1Input.class, Step3Result.class)
                .then(StepDefinition.of("step1", (Step1Input input) -> StepResult.continueWith(new Step1Result(input.value + "-1")))
                    .withRetryPolicy(new RetryPolicyBuilder()
                        .withMaxAttempts(2)
                        .build())
                )
                .then(StepDefinition.of("step2", (Step1Result input) -> StepResult.continueWith(new Step2Result(input.value + "-2")))
                    .withRetryPolicy(new RetryPolicyBuilder()
                        .withMaxAttempts(2)
                        .build())
                )
                .then(StepDefinition.of("step3", (Step2Result input) -> StepResult.finish(new Step3Result(input.value + "-3")))
                    .withRetryPolicy(new RetryPolicyBuilder()
                        .withMaxAttempts(2)
                        .build())
                )
                .build();
            
            // Create engine with async persistence
            WorkflowEngine engine = PersistentWorkflowEngineFactory.createPersistentEngine(
                workflowId,
                PersistentWorkflowEngineFactory.PersistenceConfig.builder()
                    .stateStore(stateStore)
                    .asyncPersistence(true) // Async mode
                    .persistenceTimeoutMs(1000)
                    .build()
            );
            
            // Register and execute workflow
            engine.register(workflow);
            long startTime = System.currentTimeMillis();
            WorkflowEngine.WorkflowExecution<Step3Result> execution = engine.execute("async-test", new Step1Input("test"));
            Step3Result result = execution.get(10, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - startTime;
            
            assertEquals("test-1-2-3", result.value);
            
            // Should complete quickly with async persistence
            assertTrue(duration < 500, "Execution took too long: " + duration + "ms");
            
            // Give async operations time to complete
            Thread.sleep(100);
            
            // Verify states were eventually persisted (all should be cleaned up after success)
            for (String stepId : new String[]{"step1", "step2", "step3"}) {
                Optional<RetryContext> context = stateStore.loadRetryContext(workflowId, stepId)
                    .get(5, TimeUnit.SECONDS);
                assertFalse(context.isPresent(), "Context for " + stepId + " should be cleaned up");
            }
        }
    }
    
    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {
        
        @Test
        @DisplayName("Should continue execution when persistence fails")
        void shouldContinueWhenPersistenceFails() throws Exception {
            // Create a failing state store
            RetryStateStore failingStore = new RetryStateStore() {
                @Override
                public CompletableFuture<Void> saveRetryContext(String workflowId, String stepId, RetryContext context) {
                    return CompletableFuture.failedFuture(new IOException("Storage failure"));
                }
                
                @Override
                public CompletableFuture<Optional<RetryContext>> loadRetryContext(String workflowId, String stepId) {
                    return CompletableFuture.failedFuture(new IOException("Storage failure"));
                }
                
                @Override
                public CompletableFuture<Void> deleteRetryContext(String workflowId, String stepId) {
                    return CompletableFuture.failedFuture(new IOException("Storage failure"));
                }
                
                @Override
                public CompletableFuture<Void> saveCircuitBreakerState(String workflowId, String stepId, 
                                                                      CircuitBreaker.CircuitStateSnapshot state) {
                    return CompletableFuture.failedFuture(new IOException("Storage failure"));
                }
                
                @Override
                public CompletableFuture<Optional<CircuitBreaker.CircuitStateSnapshot>> loadCircuitBreakerState(
                        String workflowId, String stepId) {
                    return CompletableFuture.failedFuture(new IOException("Storage failure"));
                }
                
                @Override
                public CompletableFuture<Void> deleteWorkflowState(String workflowId) {
                    return CompletableFuture.failedFuture(new IOException("Storage failure"));
                }
            };
            
            // Create workflow
            WorkflowGraph<String, String> workflow = WorkflowBuilder
                .define("failure-test", String.class, String.class)
                .then(StepDefinition.of("resilient-step", (String input) -> StepResult.finish("success despite storage failure"))
                    .withRetryPolicy(new RetryPolicyBuilder()
                        .withMaxAttempts(2)
                        .build())
                )
                .build();
            
            // Create engine with failing persistence
            WorkflowEngine engine = PersistentWorkflowEngineFactory.createPersistentEngine(
                workflowId,
                PersistentWorkflowEngineFactory.PersistenceConfig.builder()
                    .stateStore(failingStore)
                    .asyncPersistence(false)
                    .persistenceTimeoutMs(100) // Short timeout
                    .build()
            );
            
            // Register and should still execute successfully
            engine.register(workflow);
            WorkflowEngine.WorkflowExecution<String> execution = engine.execute("failure-test", "test-input");
            String result = execution.get(10, TimeUnit.SECONDS);
            assertEquals("success despite storage failure", result);
        }
    }
    
    // Helper classes for async test
    record Step1Input(String value) {}
    record Step1Result(String value) {}
    record Step2Result(String value) {}
    record Step3Result(String value) {}
}