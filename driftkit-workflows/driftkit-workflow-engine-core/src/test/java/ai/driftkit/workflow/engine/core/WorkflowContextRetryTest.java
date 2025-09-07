package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.domain.RetryContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowContextRetryTest {
    
    private WorkflowContext context;
    
    @BeforeEach
    void setUp() {
        context = WorkflowContext.newRun("test-trigger-data");
    }
    
    @Nested
    @DisplayName("Step Execution Tracking")
    class StepExecutionTrackingTests {
        
        @Test
        @DisplayName("Should track step execution counts")
        void testStepExecutionCounting() {
            // Act & Assert
            assertEquals(0, context.getStepExecutionCount("step1"));
            
            assertEquals(1, context.recordStepExecution("step1"));
            assertEquals(1, context.getStepExecutionCount("step1"));
            
            assertEquals(2, context.recordStepExecution("step1"));
            assertEquals(2, context.getStepExecutionCount("step1"));
            
            assertEquals(1, context.recordStepExecution("step2"));
            assertEquals(1, context.getStepExecutionCount("step2"));
        }
        
        @Test
        @DisplayName("Should get all step execution counts")
        void testGetAllStepExecutionCounts() {
            // Arrange
            context.recordStepExecution("step1");
            context.recordStepExecution("step1");
            context.recordStepExecution("step2");
            context.recordStepExecution("step3");
            context.recordStepExecution("step3");
            context.recordStepExecution("step3");
            
            // Act
            Map<String, Integer> counts = context.getAllStepExecutionCounts();
            
            // Assert
            assertEquals(3, counts.size());
            assertEquals(2, counts.get("step1"));
            assertEquals(1, counts.get("step2"));
            assertEquals(3, counts.get("step3"));
        }
        
        @Test
        @DisplayName("Should be thread-safe")
        void testThreadSafetyOfExecutionCounting() throws InterruptedException {
            // Arrange
            int threadCount = 10;
            int incrementsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            
            // Act
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < incrementsPerThread; j++) {
                            context.recordStepExecution("concurrent-step");
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();
            
            // Assert
            assertTrue(completed);
            assertEquals(threadCount * incrementsPerThread, context.getStepExecutionCount("concurrent-step"));
        }
    }
    
    @Nested
    @DisplayName("Retry Context Management")
    class RetryContextManagementTests {
        
        @Test
        @DisplayName("Should store and retrieve retry context")
        void testRetryContextStorage() {
            // Arrange
            RetryContext retryContext = RetryContext.builder()
                .stepId("step1")
                .attemptNumber(2)
                .maxAttempts(3)
                .firstAttemptTime(1000L)
                .currentAttemptTime(2000L)
                .build();
            
            // Act
            assertNull(context.getRetryContext("step1"));
            
            context.updateRetryContext("step1", retryContext);
            
            // Assert
            RetryContext retrieved = context.getRetryContext("step1");
            assertNotNull(retrieved);
            assertEquals("step1", retrieved.getStepId());
            assertEquals(2, retrieved.getAttemptNumber());
            assertEquals(3, retrieved.getMaxAttempts());
        }
        
        @Test
        @DisplayName("Should get current retry context")
        void testGetCurrentRetryContext() {
            // Arrange
            RetryContext retryContext1 = RetryContext.builder()
                .stepId("step1")
                .attemptNumber(2)
                .maxAttempts(3)
                .firstAttemptTime(1000L)
                .currentAttemptTime(2000L)
                .build();
            
            RetryContext retryContext2 = RetryContext.builder()
                .stepId("step2")
                .attemptNumber(1)
                .maxAttempts(5)
                .firstAttemptTime(3000L)
                .currentAttemptTime(3000L)
                .build();
            
            // Act & Assert
            assertNull(context.getCurrentRetryContext()); // No last step
            
            context.updateRetryContext("step1", retryContext1);
            context.updateRetryContext("step2", retryContext2);
            
            // Simulate step execution
            context.setStepOutput("step1", StepOutput.of("result1"));
            RetryContext current = context.getCurrentRetryContext();
            assertNotNull(current);
            assertEquals("step1", current.getStepId());
            
            // Simulate another step execution
            context.setStepOutput("step2", StepOutput.of("result2"));
            current = context.getCurrentRetryContext();
            assertNotNull(current);
            assertEquals("step2", current.getStepId());
        }
        
        @Test
        @DisplayName("Should clear retry context")
        void testClearRetryContext() {
            // Arrange
            RetryContext retryContext = RetryContext.builder()
                .stepId("step1")
                .attemptNumber(2)
                .maxAttempts(3)
                .firstAttemptTime(1000L)
                .currentAttemptTime(2000L)
                .build();
            
            context.updateRetryContext("step1", retryContext);
            assertNotNull(context.getRetryContext("step1"));
            
            // Act
            context.clearRetryContext("step1");
            
            // Assert
            assertNull(context.getRetryContext("step1"));
        }
        
        @Test
        @DisplayName("Should update retry context with attempts")
        void testRetryContextWithAttempts() {
            // Arrange
            RetryContext.RetryAttempt attempt1 = RetryContext.RetryAttempt.builder()
                .attemptNumber(1)
                .attemptTime(1000L)
                .failure(new RuntimeException("First failure"))
                .durationMs(100L)
                .build();
            
            RetryContext retryContext = RetryContext.builder()
                .stepId("step1")
                .attemptNumber(2)
                .maxAttempts(3)
                .previousAttempt(attempt1)
                .firstAttemptTime(1000L)
                .currentAttemptTime(2500L)
                .build();
            
            // Act
            context.updateRetryContext("step1", retryContext);
            
            // Assert
            RetryContext retrieved = context.getRetryContext("step1");
            assertEquals(1, retrieved.getPreviousAttempts().size());
            assertEquals("First failure", retrieved.getPreviousAttempts().get(0).getFailureMessage());
            assertEquals(1500L, retrieved.getTotalElapsedMs());
        }
    }
}