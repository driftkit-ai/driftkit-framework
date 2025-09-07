package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.annotations.OnInvocationsLimit;
import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import ai.driftkit.workflow.engine.builder.RetryPolicyBuilder;
import ai.driftkit.workflow.engine.domain.RetryContext;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetryExecutorTest {
    
    private RetryExecutor retryExecutor;
    private RetryStrategy mockRetryStrategy;
    private WorkflowInstance mockInstance;
    private WorkflowContext mockContext;
    
    @BeforeEach
    void setUp() {
        mockRetryStrategy = mock(RetryStrategy.class);
        retryExecutor = new RetryExecutor(mockRetryStrategy);
        mockInstance = mock(WorkflowInstance.class);
        mockContext = mock(WorkflowContext.class);
        when(mockInstance.getContext()).thenReturn(mockContext);
    }
    
    @Nested
    @DisplayName("Invocation Limit Tests")
    class InvocationLimitTests {
        
        @Test
        @DisplayName("Should execute step when under invocation limit")
        void testUnderInvocationLimit() throws Exception {
            // Arrange
            StepNode step = createStepNode("test-step", null, 5, OnInvocationsLimit.ERROR);
            when(mockContext.recordStepExecution("test-step")).thenReturn(1);
            
            AtomicInteger executionCount = new AtomicInteger(0);
            RetryExecutor.StepExecutor executor = (inst, stp) -> {
                executionCount.incrementAndGet();
                return StepResult.continueWith("success");
            };
            
            // Act
            StepResult<?> result = retryExecutor.executeWithRetry(mockInstance, step, executor);
            
            // Assert
            assertEquals(1, executionCount.get());
            assertEquals("success", ((StepResult.Continue<?>) result).data());
        }
        
        @Test
        @DisplayName("Should throw error when limit exceeded with ERROR behavior")
        void testInvocationLimitExceededError() {
            // Arrange
            StepNode step = createStepNode("test-step", null, 2, OnInvocationsLimit.ERROR);
            when(mockContext.recordStepExecution("test-step")).thenReturn(3);
            
            RetryExecutor.StepExecutor executor = (inst, stp) -> StepResult.continueWith("success");
            
            // Act & Assert
            assertThrows(RetryExecutor.InvocationLimitExceededException.class,
                () -> retryExecutor.executeWithRetry(mockInstance, step, executor));
        }
        
        @Test
        @DisplayName("Should return finish when limit exceeded with STOP behavior")
        void testInvocationLimitExceededStop() throws Exception {
            // Arrange
            StepNode step = createStepNode("test-step", null, 2, OnInvocationsLimit.STOP);
            when(mockContext.recordStepExecution("test-step")).thenReturn(3);
            
            AtomicInteger executionCount = new AtomicInteger(0);
            RetryExecutor.StepExecutor executor = (inst, stp) -> {
                executionCount.incrementAndGet();
                return StepResult.continueWith("success");
            };
            
            // Act
            StepResult<?> result = retryExecutor.executeWithRetry(mockInstance, step, executor);
            
            // Assert
            assertEquals(0, executionCount.get()); // Should not execute
            assertTrue(result instanceof StepResult.Finish);
            assertNull(((StepResult.Finish<?>) result).result());
        }
        
        @Test
        @DisplayName("Should continue when limit exceeded with CONTINUE behavior")
        void testInvocationLimitExceededContinue() throws Exception {
            // Arrange
            StepNode step = createStepNode("test-step", null, 2, OnInvocationsLimit.CONTINUE);
            when(mockContext.recordStepExecution("test-step")).thenReturn(3);
            
            AtomicInteger executionCount = new AtomicInteger(0);
            RetryExecutor.StepExecutor executor = (inst, stp) -> {
                executionCount.incrementAndGet();
                return StepResult.continueWith("success");
            };
            
            // Act
            StepResult<?> result = retryExecutor.executeWithRetry(mockInstance, step, executor);
            
            // Assert
            assertEquals(1, executionCount.get());
            assertEquals("success", ((StepResult.Continue<?>) result).data());
        }
    }
    
    @Nested
    @DisplayName("Retry Logic Tests")
    class RetryLogicTests {
        
        @Test
        @DisplayName("Should not retry when no retry policy")
        void testNoRetryPolicy() throws Exception {
            // Arrange
            StepNode step = createStepNode("test-step", null, 100, OnInvocationsLimit.ERROR);
            when(mockContext.recordStepExecution("test-step")).thenReturn(1);
            
            AtomicInteger executionCount = new AtomicInteger(0);
            RetryExecutor.StepExecutor executor = (inst, stp) -> {
                executionCount.incrementAndGet();
                throw new RuntimeException("Test error");
            };
            
            // Act & Assert
            RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> retryExecutor.executeWithRetry(mockInstance, step, executor));
            assertEquals("Test error", thrown.getMessage());
            assertEquals(1, executionCount.get());
        }
        
        @Test
        @DisplayName("Should retry on failure with retry policy")
        void testRetryOnFailure() throws Exception {
            // Arrange
            RetryPolicy retryPolicy = RetryPolicyBuilder.retry()
                .withMaxAttempts(3)
                .withDelay(10)
                .build();
            
            StepNode step = createStepNode("test-step", retryPolicy, 100, OnInvocationsLimit.ERROR);
            when(mockContext.recordStepExecution("test-step")).thenReturn(1);
            when(mockRetryStrategy.shouldRetry(any(), any(), eq(retryPolicy))).thenReturn(true);
            when(mockRetryStrategy.calculateDelay(any(), eq(retryPolicy))).thenReturn(10L);
            
            AtomicInteger executionCount = new AtomicInteger(0);
            RetryExecutor.StepExecutor executor = (inst, stp) -> {
                int count = executionCount.incrementAndGet();
                if (count < 3) {
                    throw new RuntimeException("Attempt " + count);
                }
                return StepResult.continueWith("success");
            };
            
            // Act
            StepResult<?> result = retryExecutor.executeWithRetry(mockInstance, step, executor);
            
            // Assert
            assertEquals(3, executionCount.get());
            assertEquals("success", ((StepResult.Continue<?>) result).data());
            
            // Verify retry context updates
            ArgumentCaptor<RetryContext> contextCaptor = ArgumentCaptor.forClass(RetryContext.class);
            verify(mockContext, atLeastOnce()).updateRetryContext(eq("test-step"), contextCaptor.capture());
            
            RetryContext lastContext = contextCaptor.getValue();
            assertEquals(3, lastContext.getAttemptNumber());
            assertEquals(2, lastContext.getPreviousAttempts().size());
        }
        
        @Test
        @DisplayName("Should not retry on non-retryable error")
        void testNoRetryOnNonRetryableError() throws Exception {
            // Arrange
            RetryPolicy retryPolicy = RetryPolicyBuilder.retry()
                .withMaxAttempts(3)
                .build();
            
            StepNode step = createStepNode("test-step", retryPolicy, 100, OnInvocationsLimit.ERROR);
            when(mockContext.recordStepExecution("test-step")).thenReturn(1);
            
            IOException error = new IOException("Non-retryable");
            when(mockRetryStrategy.shouldRetry(eq(error), any(), eq(retryPolicy))).thenReturn(false);
            
            AtomicInteger executionCount = new AtomicInteger(0);
            RetryExecutor.StepExecutor executor = (inst, stp) -> {
                executionCount.incrementAndGet();
                throw error;
            };
            
            // Act & Assert
            IOException thrown = assertThrows(IOException.class,
                () -> retryExecutor.executeWithRetry(mockInstance, step, executor));
            assertEquals(error, thrown);
            assertEquals(1, executionCount.get());
        }
        
        @Test
        @DisplayName("Should exhaust retries and throw exception")
        void testRetryExhausted() throws Exception {
            // Arrange
            RetryPolicy retryPolicy = RetryPolicyBuilder.retry()
                .withMaxAttempts(2)
                .withDelay(5)
                .build();
            
            StepNode step = createStepNode("test-step", retryPolicy, 100, OnInvocationsLimit.ERROR);
            when(mockContext.recordStepExecution("test-step")).thenReturn(1);
            when(mockRetryStrategy.shouldRetry(any(), any(), eq(retryPolicy))).thenReturn(true);
            when(mockRetryStrategy.calculateDelay(any(), eq(retryPolicy))).thenReturn(5L);
            
            AtomicInteger executionCount = new AtomicInteger(0);
            RetryExecutor.StepExecutor executor = (inst, stp) -> {
                executionCount.incrementAndGet();
                throw new RuntimeException("Always fails");
            };
            
            // Act & Assert
            RetryExecutor.RetryExhaustedException thrown = assertThrows(
                RetryExecutor.RetryExhaustedException.class,
                () -> retryExecutor.executeWithRetry(mockInstance, step, executor)
            );
            
            assertTrue(thrown.getMessage().contains("test-step"));
            assertTrue(thrown.getMessage().contains("2 attempts"));
            assertEquals(2, executionCount.get());
        }
        
        @Test
        @DisplayName("Should retry on fail result when configured")
        void testRetryOnFailResult() throws Exception {
            // Arrange
            RetryPolicy retryPolicy = RetryPolicyBuilder.retry()
                .withMaxAttempts(3)
                .withRetryOnFailResult(true)
                .withDelay(5)
                .build();
            
            StepNode step = createStepNode("test-step", retryPolicy, 100, OnInvocationsLimit.ERROR);
            when(mockContext.recordStepExecution("test-step")).thenReturn(1);
            when(mockRetryStrategy.shouldRetry(any(), any(), eq(retryPolicy))).thenReturn(true);
            when(mockRetryStrategy.calculateDelay(any(), eq(retryPolicy))).thenReturn(5L);
            
            AtomicInteger executionCount = new AtomicInteger(0);
            RetryExecutor.StepExecutor executor = (inst, stp) -> {
                int count = executionCount.incrementAndGet();
                if (count < 3) {
                    return StepResult.fail(new RuntimeException("Fail " + count));
                }
                return StepResult.continueWith("success");
            };
            
            // Act
            StepResult<?> result = retryExecutor.executeWithRetry(mockInstance, step, executor);
            
            // Assert
            assertEquals(3, executionCount.get());
            assertEquals("success", ((StepResult.Continue<?>) result).data());
        }
        
        @Test
        @DisplayName("Should clear retry context on success")
        void testClearRetryContextOnSuccess() throws Exception {
            // Arrange
            RetryPolicy retryPolicy = RetryPolicyBuilder.retry()
                .withMaxAttempts(2)
                .build();
            
            StepNode step = createStepNode("test-step", retryPolicy, 100, OnInvocationsLimit.ERROR);
            when(mockContext.recordStepExecution("test-step")).thenReturn(1);
            
            RetryExecutor.StepExecutor executor = (inst, stp) -> StepResult.continueWith("success");
            
            // Act
            retryExecutor.executeWithRetry(mockInstance, step, executor);
            
            // Assert
            verify(mockContext).clearRetryContext("test-step");
        }
    }
    
    private StepNode createStepNode(String id, RetryPolicy retryPolicy, 
                                   int invocationLimit, OnInvocationsLimit onInvocationsLimit) {
        StepNode.StepExecutor executor = new StepNode.StepExecutor() {
            @Override
            public Object execute(Object input, WorkflowContext context) {
                return null;
            }
            
            @Override
            public Class<?> getInputType() {
                return Object.class;
            }
            
            @Override
            public Class<?> getOutputType() {
                return Object.class;
            }
            
            @Override
            public boolean requiresContext() {
                return false;
            }
        };
        
        return new StepNode(id, id, executor, false, false, 
                           retryPolicy, invocationLimit, onInvocationsLimit);
    }
}