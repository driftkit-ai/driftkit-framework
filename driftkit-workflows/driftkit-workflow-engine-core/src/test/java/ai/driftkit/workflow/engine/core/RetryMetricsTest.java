package ai.driftkit.workflow.engine.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RetryMetrics functionality.
 */
class RetryMetricsTest {
    
    private RetryMetrics metrics;
    
    @BeforeEach
    void setUp() {
        metrics = new RetryMetrics();
    }
    
    @Nested
    @DisplayName("Basic Metrics Recording")
    class BasicMetricsTests {
        
        @Test
        @DisplayName("Should record retry attempts")
        void shouldRecordRetryAttempts() {
            String stepId = "test-step";
            
            metrics.recordRetryAttempt(stepId, 2, 1000);
            metrics.recordRetryAttempt(stepId, 3, 2000);
            
            RetryMetrics.StepMetrics stepMetrics = metrics.getStepMetrics(stepId);
            assertNotNull(stepMetrics);
            assertEquals(2, stepMetrics.getAttemptCount().get());
            assertEquals(3000, stepMetrics.getTotalRetryDelayMs().get());
        }
        
        @Test
        @DisplayName("Should record retry success")
        void shouldRecordRetrySuccess() {
            String stepId = "success-step";
            
            metrics.recordRetryAttempt(stepId, 2, 1000);
            metrics.recordRetrySuccess(stepId, 2, 5000);
            
            RetryMetrics.StepMetrics stepMetrics = metrics.getStepMetrics(stepId);
            assertEquals(1, stepMetrics.getSuccessCount().get());
            assertEquals(5000, stepMetrics.getTotalRetryDurationMs().get());
            assertEquals(2, stepMetrics.getMaxAttempts().get());
        }
        
        @Test
        @DisplayName("Should record retry failures")
        void shouldRecordRetryFailures() {
            String stepId = "failure-step";
            
            metrics.recordRetryFailure(stepId, 1, new IOException("Network error"));
            metrics.recordRetryFailure(stepId, 2, new IOException("Network error"));
            metrics.recordRetryFailure(stepId, 3, new SQLException("DB error"));
            
            RetryMetrics.StepMetrics stepMetrics = metrics.getStepMetrics(stepId);
            assertEquals(3, stepMetrics.getFailureCount().get());
            assertEquals(2, stepMetrics.getExceptionCounts().get("IOException").get());
            assertEquals(1, stepMetrics.getExceptionCounts().get("SQLException").get());
        }
        
        @Test
        @DisplayName("Should record retry exhausted")
        void shouldRecordRetryExhausted() {
            String stepId = "exhausted-step";
            
            metrics.recordRetryAttempt(stepId, 2, 1000);
            metrics.recordRetryAttempt(stepId, 3, 2000);
            metrics.recordRetryExhausted(stepId, 3);
            
            RetryMetrics.StepMetrics stepMetrics = metrics.getStepMetrics(stepId);
            assertEquals(1, stepMetrics.getExhaustedCount().get());
            assertEquals(3, stepMetrics.getMaxAttempts().get());
        }
    }
    
    @Nested
    @DisplayName("Calculated Metrics")
    class CalculatedMetricsTests {
        
        @Test
        @DisplayName("Should calculate success rate")
        void shouldCalculateSuccessRate() {
            String stepId = "rate-step";
            
            // 3 successes, 1 exhausted = 75% success rate
            metrics.recordRetrySuccess(stepId, 1, 1000);
            metrics.recordRetrySuccess(stepId, 2, 2000);
            metrics.recordRetrySuccess(stepId, 1, 1500);
            metrics.recordRetryExhausted(stepId, 3);
            
            RetryMetrics.StepMetrics stepMetrics = metrics.getStepMetrics(stepId);
            assertEquals(75.0, stepMetrics.getSuccessRate(), 0.01);
        }
        
        @Test
        @DisplayName("Should return -1 success rate when no executions")
        void shouldReturnNegativeRateWhenNoExecutions() {
            String stepId = "no-exec-step";
            
            // Only failures, no successes or exhausted
            metrics.recordRetryFailure(stepId, 1, new RuntimeException());
            
            RetryMetrics.StepMetrics stepMetrics = metrics.getStepMetrics(stepId);
            assertEquals(-1, stepMetrics.getSuccessRate());
        }
        
        @Test
        @DisplayName("Should calculate average retry delay")
        void shouldCalculateAverageDelay() {
            String stepId = "delay-step";
            
            metrics.recordRetryAttempt(stepId, 2, 1000);
            metrics.recordRetryAttempt(stepId, 3, 2000);
            metrics.recordRetryAttempt(stepId, 4, 3000);
            
            RetryMetrics.StepMetrics stepMetrics = metrics.getStepMetrics(stepId);
            assertEquals(2000.0, stepMetrics.getAverageRetryDelay(), 0.01);
        }
    }
    
    @Nested
    @DisplayName("Global Metrics")
    class GlobalMetricsTests {
        
        @Test
        @DisplayName("Should aggregate global metrics")
        void shouldAggregateGlobalMetrics() {
            // Step 1: 2 attempts, 1 success
            metrics.recordRetryAttempt("step1", 2, 1000);
            metrics.recordRetrySuccess("step1", 2, 5000);
            
            // Step 2: 3 attempts, 2 failures, 1 exhausted
            metrics.recordRetryAttempt("step2", 2, 1000);
            metrics.recordRetryAttempt("step2", 3, 2000);
            metrics.recordRetryFailure("step2", 2, new RuntimeException());
            metrics.recordRetryFailure("step2", 3, new RuntimeException());
            metrics.recordRetryExhausted("step2", 3);
            
            RetryMetrics.GlobalMetrics global = metrics.getGlobalMetrics();
            assertEquals(3, global.getTotalAttempts());  // 2 + 2 attempts
            assertEquals(1, global.getTotalSuccesses());
            assertEquals(2, global.getTotalFailures());
            assertEquals(1, global.getTotalExhausted());
            assertEquals(2, global.getUniqueSteps());
        }
        
        @Test
        @DisplayName("Should calculate overall success rate")
        void shouldCalculateOverallSuccessRate() {
            // 2 successes
            metrics.recordRetrySuccess("step1", 1, 1000);
            metrics.recordRetrySuccess("step2", 2, 2000);
            
            // 1 exhausted
            metrics.recordRetryExhausted("step3", 3);
            
            RetryMetrics.GlobalMetrics global = metrics.getGlobalMetrics();
            // 2 successes out of 3 total (2 success + 1 exhausted) = 66.67%
            assertEquals(66.67, global.getOverallSuccessRate(), 0.01);
        }
    }
    
    @Nested
    @DisplayName("Reset Operations")
    class ResetTests {
        
        @Test
        @DisplayName("Should reset individual step metrics")
        void shouldResetStepMetrics() {
            String stepId = "reset-step";
            
            metrics.recordRetryAttempt(stepId, 2, 1000);
            metrics.recordRetrySuccess(stepId, 2, 5000);
            
            assertNotNull(metrics.getStepMetrics(stepId));
            
            metrics.resetStep(stepId);
            
            assertNull(metrics.getStepMetrics(stepId));
        }
        
        @Test
        @DisplayName("Should reset all metrics")
        void shouldResetAllMetrics() {
            // Record various metrics
            metrics.recordRetryAttempt("step1", 2, 1000);
            metrics.recordRetrySuccess("step2", 1, 2000);
            metrics.recordRetryExhausted("step3", 3);
            
            RetryMetrics.GlobalMetrics before = metrics.getGlobalMetrics();
            assertTrue(before.getTotalAttempts() > 0);
            assertTrue(before.getUniqueSteps() > 0);
            
            metrics.resetAll();
            
            RetryMetrics.GlobalMetrics after = metrics.getGlobalMetrics();
            assertEquals(0, after.getTotalAttempts());
            assertEquals(0, after.getTotalSuccesses());
            assertEquals(0, after.getTotalFailures());
            assertEquals(0, after.getTotalExhausted());
            assertEquals(0, after.getUniqueSteps());
        }
    }
    
    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {
        
        @Test
        @DisplayName("Should handle concurrent updates")
        void shouldHandleConcurrentUpdates() throws InterruptedException {
            String stepId = "concurrent-step";
            int threadCount = 10;
            int operationsPerThread = 100;
            
            Thread[] threads = new Thread[threadCount];
            
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < operationsPerThread; j++) {
                        metrics.recordRetryAttempt(stepId, j + 1, 100);
                        if (j % 2 == 0) {
                            metrics.recordRetryFailure(stepId, j + 1, new RuntimeException());
                        }
                    }
                });
                threads[i].start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            RetryMetrics.StepMetrics stepMetrics = metrics.getStepMetrics(stepId);
            assertEquals(threadCount * operationsPerThread, stepMetrics.getAttemptCount().get());
            assertEquals(threadCount * operationsPerThread / 2, stepMetrics.getFailureCount().get());
        }
    }
}