package ai.driftkit.workflow.engine.integration;

import ai.driftkit.workflow.engine.annotations.*;
import ai.driftkit.workflow.engine.builder.RetryPolicyBuilder;
import ai.driftkit.workflow.engine.builder.StepDefinition;
import ai.driftkit.workflow.engine.builder.WorkflowBuilder;
import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.domain.RetryContext;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.inmemory.InMemoryWorkflowStateRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for advanced retry features including circuit breaker,
 * conditional retry, metrics, and listeners.
 */
@Slf4j
class AdvancedRetryIntegrationTest {
    
    private WorkflowEngine engine;
    private RetryExecutor retryExecutor;
    private TestRetryListener testListener;
    
    @BeforeEach
    void setUp() {
        retryExecutor = new RetryExecutor();
        testListener = new TestRetryListener();
        retryExecutor.addRetryListener(testListener);
        
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .stateRepository(new InMemoryWorkflowStateRepository())
            .retryExecutor(retryExecutor)
            .build();
            
        engine = new WorkflowEngine(config);
    }
    
    @Test
    @DisplayName("Should use conditional retry based on exception types")
    void testConditionalRetry() throws Exception {
        AtomicInteger networkAttempts = new AtomicInteger(0);
        AtomicInteger dbAttempts = new AtomicInteger(0);
        
        // Build workflow with conditional retry
        WorkflowGraph<String, String> conditionalWorkflow = WorkflowBuilder
            .define("conditional-retry-workflow", String.class, String.class)
            .then(StepDefinition.of("unreliableNetworkCall", (String input) -> {
                    int attempt = networkAttempts.incrementAndGet();
                    log.info("Network call attempt: {}", attempt);
                    
                    if (attempt == 1) {
                        throw new IllegalArgumentException("Bad input - should abort");
                    }
                    
                    // This shouldn't be reached due to abort
                    return StepResult.finish("Success");
                })
                .withRetryPolicy(new RetryPolicyBuilder()
                    .withMaxAttempts(5)
                    .withDelay(100)
                    .withRetryOn(IOException.class, SQLException.class)
                    .withAbortOn(IllegalArgumentException.class)
                    .build()
                )
            )
            .build();
        
        engine.register(conditionalWorkflow);
        
        // Test abort on IllegalArgumentException
        var exec1 = engine.execute("conditional-retry-workflow", "test1");
        ExecutionException ee = assertThrows(ExecutionException.class, () -> exec1.get(5, TimeUnit.SECONDS));
        Throwable rootCause = ee.getCause();
        // IllegalArgumentException is wrapped in RuntimeException from lambda
        if (rootCause instanceof RuntimeException && rootCause.getCause() instanceof IllegalArgumentException) {
            // Expected - wrapped from lambda
        } else if (rootCause instanceof IllegalArgumentException) {
            // Also ok - direct exception
        } else {
            fail("Expected IllegalArgumentException but got: " + rootCause);
        }
        assertEquals(1, networkAttempts.get()); // Should abort immediately
        
        // Reset counter
        networkAttempts.set(0);
        
        // Test no retry on non-listed exception
        WorkflowGraph<String, String> dbWorkflow = WorkflowBuilder
            .define("db-workflow", String.class, String.class)
            .then(StepDefinition.of("dbOp", (String input) -> {
                    dbAttempts.incrementAndGet();
                    throw new RuntimeException("Not a SQLException");
                })
                .withRetryPolicy(new RetryPolicyBuilder()
                    .withMaxAttempts(3)
                    .withRetryOn(SQLException.class)
                    .build()
                )
            )
            .build();
        
        engine.register(dbWorkflow);
        var exec2 = engine.execute("db-workflow", "test2");
        ExecutionException ee2 = assertThrows(ExecutionException.class, () -> exec2.get(5, TimeUnit.SECONDS));
        assertInstanceOf(RuntimeException.class, ee2.getCause());
        assertEquals(1, dbAttempts.get()); // Should not retry
    }
    
    @Test
    @DisplayName("Should trigger circuit breaker after repeated failures")
    void testCircuitBreaker() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        
        // Configure circuit breaker to open after 3 failures
        CircuitBreaker.CircuitBreakerConfig cbConfig = CircuitBreaker.CircuitBreakerConfig.builder()
            .failureThreshold(3)
            .openDurationMs(2000)
            .build();
        CircuitBreaker circuitBreaker = new CircuitBreaker(cbConfig);
        
        RetryExecutor customRetryExecutor = new RetryExecutor(
            new DefaultRetryStrategy(),
            circuitBreaker,
            new RetryMetrics(),
            new ArrayList<>()
        );
        
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .stateRepository(new InMemoryWorkflowStateRepository())
            .retryExecutor(customRetryExecutor)
            .build();
            
        WorkflowEngine customEngine = new WorkflowEngine(config);
        
        WorkflowGraph<String, String> circuitBreakerWorkflow = WorkflowBuilder
            .define("circuit-breaker-workflow", String.class, String.class)
            .then(StepDefinition.of("failingStep", (String input) -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("Always fails");
                })
                .withRetryPolicy(new RetryPolicyBuilder()
                    .withMaxAttempts(2)
                    .withDelay(100)
                    .build()
                )
            )
            .build();
        
        customEngine.register(circuitBreakerWorkflow);
        
        // First execution - should fail after retries
        var exec1 = customEngine.execute("circuit-breaker-workflow", "test1");
        assertThrows(Exception.class, () -> exec1.get(5, TimeUnit.SECONDS));
        assertEquals(2, attempts.get()); // maxAttempts = 2
        
        // Second execution - should fail after retries
        attempts.set(0);
        var exec2 = customEngine.execute("circuit-breaker-workflow", "test2");
        assertThrows(Exception.class, () -> exec2.get(5, TimeUnit.SECONDS));
        assertEquals(2, attempts.get());
        
        // Third execution - circuit should open
        attempts.set(0);
        var exec3 = customEngine.execute("circuit-breaker-workflow", "test3");
        ExecutionException ee3 = assertThrows(ExecutionException.class, 
            () -> exec3.get(5, TimeUnit.SECONDS));
        assertInstanceOf(RetryExecutor.CircuitBreakerOpenException.class, ee3.getCause());
        assertEquals(0, attempts.get()); // Should not even attempt
        
        // Verify circuit is open
        assertEquals(CircuitBreaker.State.OPEN, 
            circuitBreaker.getState("failingStep"));
    }
    
    @Test
    @DisplayName("Should collect retry metrics and notify listeners")
    void testMetricsAndListeners() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        
        WorkflowGraph<String, String> metricsWorkflow = WorkflowBuilder
            .define("metrics-workflow", String.class, String.class)
            .then(StepDefinition.of("retriableStep", (String input) -> {
                    try {
                        int attempt = attempts.incrementAndGet();
                        if (attempt < 3) {
                            throw new IOException("Temporary failure");
                        }
                        return StepResult.finish("Success after " + attempt + " attempts");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .withRetryPolicy(new RetryPolicyBuilder()
                    .withMaxAttempts(3)
                    .withDelay(50)
                    .withBackoffMultiplier(2.0)
                    .build()
                )
            )
            .build();
        
        engine.register(metricsWorkflow);
        
        var execution = engine.execute("metrics-workflow", "test");
        Object result = execution.get(5, TimeUnit.SECONDS);
        
        assertEquals("Success after 3 attempts", result);
        
        // Verify metrics
        RetryMetrics metrics = retryExecutor.getRetryMetrics();
        RetryMetrics.StepMetrics stepMetrics = metrics.getStepMetrics("retriableStep");
        
        assertNotNull(stepMetrics);
        assertEquals(2, stepMetrics.getAttemptCount().get()); // 2 retry attempts (not counting first)
        assertEquals(1, stepMetrics.getSuccessCount().get());
        assertEquals(2, stepMetrics.getFailureCount().get());
        assertEquals(100.0, stepMetrics.getSuccessRate(), 0.01);
        
        // Verify listeners were called
        assertEquals(2, testListener.beforeRetryCount); // Called before retry 2 and 3
        assertEquals(1, testListener.successCount);
        assertEquals(2, testListener.failureCount);
        assertEquals(0, testListener.exhaustedCount);
        assertEquals(0, testListener.abortedCount);
        
        // Verify listener received correct information
        assertEquals("retriableStep", testListener.lastStepId);
        assertTrue(testListener.lastResult.toString().contains("Success after 3 attempts"));
    }
    
    @Test
    @DisplayName("Should handle retry exhaustion with metrics and listeners")
    void testRetryExhaustion() throws Exception {
        WorkflowGraph<String, String> exhaustionWorkflow = WorkflowBuilder
            .define("exhaustion-workflow", String.class, String.class)
            .then(StepDefinition.of("alwaysFailingStep", (String input) -> {
                    throw new RuntimeException("Persistent failure");
                })
                .withRetryPolicy(new RetryPolicyBuilder()
                    .withMaxAttempts(3)
                    .withDelay(50)
                    .build()
                )
            )
            .build();
        
        engine.register(exhaustionWorkflow);
        
        var execution = engine.execute("exhaustion-workflow", "test");
        ExecutionException ee = assertThrows(ExecutionException.class, 
            () -> execution.get(5, TimeUnit.SECONDS));
        // RetryExhaustedException should be the cause
        Throwable cause = ee.getCause();
        // Since we don't specify retryOn conditions, ConditionalRetryStrategy will retry all exceptions
        // So we expect RetryExhaustedException after max attempts
        assertInstanceOf(RetryExecutor.RetryExhaustedException.class, cause,
                        "Expected RetryExhaustedException after retry exhaustion");
        
        // Verify metrics
        RetryMetrics metrics = retryExecutor.getRetryMetrics();
        RetryMetrics.StepMetrics stepMetrics = metrics.getStepMetrics("alwaysFailingStep");
        
        assertEquals(2, stepMetrics.getAttemptCount().get()); // 2 retry attempts (not counting first)
        assertEquals(3, stepMetrics.getFailureCount().get()); // 3 total failures
        assertEquals(1, stepMetrics.getExhaustedCount().get());
        assertEquals(0.0, stepMetrics.getSuccessRate(), 0.01);
        
        // Verify listeners
        assertEquals(1, testListener.exhaustedCount);
        assertEquals(2, testListener.failureCount); // Only retry failures are counted, not the final one
        assertEquals("alwaysFailingStep", testListener.lastStepId);
    }
    
    @Test
    @DisplayName("Should demonstrate fluent API with advanced retry features")
    void testFluentApiWithAdvancedRetry() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        
        WorkflowGraph<String, String> workflow = WorkflowBuilder
            .define("advanced-fluent", String.class, String.class)
            .then(StepDefinition.of("processWithRetry", (String input) -> {
                    try {
                        int attempt = attempts.incrementAndGet();
                        if (attempt < 2) {
                            throw new SQLException("Database unavailable");
                        }
                        return StepResult.continueWith(input + " processed");
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                })
                .withRetryPolicy(new RetryPolicyBuilder()
                    .withMaxAttempts(3)
                    .withDelay(100)
                    .withBackoffMultiplier(1.5)
                    .withRetryOn(RuntimeException.class, SQLException.class, IOException.class)
                    .withAbortOn(SecurityException.class)
                    .build()
                )
            )
            .then(StepDefinition.of("finalStep", (String input) -> 
                StepResult.finish(input + " completed")
            ))
            .build();
        
        engine.register(workflow);
        
        var execution = engine.execute("advanced-fluent", "test");
        Object result = execution.get(5, TimeUnit.SECONDS);
        
        assertEquals("test processed completed", result);
        assertEquals(2, attempts.get());
        
        // Verify metrics show the retry
        RetryMetrics.StepMetrics stepMetrics = 
            retryExecutor.getRetryMetrics().getStepMetrics("processWithRetry");
        assertEquals(1, stepMetrics.getSuccessCount().get());
        assertEquals(1, stepMetrics.getFailureCount().get());
    }
    
    /**
     * Test retry listener implementation.
     */
    private static class TestRetryListener implements RetryListener {
        int beforeRetryCount = 0;
        int successCount = 0;
        int failureCount = 0;
        int exhaustedCount = 0;
        int abortedCount = 0;
        String lastStepId;
        Object lastResult;
        Exception lastException;
        
        @Override
        public void beforeRetry(String stepId, RetryContext retryContext, RetryPolicy retryPolicy) {
            beforeRetryCount++;
            lastStepId = stepId;
            log.info("Before retry {} for step {}", retryContext.getAttemptNumber(), stepId);
        }
        
        @Override
        public void onRetrySuccess(String stepId, RetryContext retryContext, Object result) {
            successCount++;
            lastStepId = stepId;
            lastResult = result;
            log.info("Retry success for step {} after {} attempts", stepId, retryContext.getAttemptNumber());
        }
        
        @Override
        public void onRetryFailure(String stepId, RetryContext retryContext, 
                                   Exception exception, boolean willRetry) {
            failureCount++;
            lastStepId = stepId;
            lastException = exception;
            log.info("Retry failure for step {}, will retry: {}", stepId, willRetry);
        }
        
        @Override
        public void onRetryExhausted(String stepId, RetryContext retryContext, Exception lastException) {
            exhaustedCount++;
            lastStepId = stepId;
            this.lastException = lastException;
            log.info("Retry exhausted for step {} after {} attempts", stepId, retryContext.getAttemptNumber());
        }
        
        @Override
        public void onRetryAborted(String stepId, RetryContext retryContext, Exception exception) {
            abortedCount++;
            lastStepId = stepId;
            lastException = exception;
            log.info("Retry aborted for step {} due to non-retryable exception", stepId);
        }
    }
}