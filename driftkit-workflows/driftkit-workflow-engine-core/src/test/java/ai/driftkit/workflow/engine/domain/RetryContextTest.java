package ai.driftkit.workflow.engine.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class RetryContextTest {
    
    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {
        
        @Test
        @DisplayName("Should build with minimal configuration")
        void testMinimalBuild() {
            // Arrange & Act
            RetryContext context = RetryContext.builder()
                .stepId("test-step")
                .attemptNumber(1)
                .maxAttempts(3)
                .firstAttemptTime(1000L)
                .currentAttemptTime(1000L)
                .build();
            
            // Assert
            assertEquals("test-step", context.getStepId());
            assertEquals(1, context.getAttemptNumber());
            assertEquals(3, context.getMaxAttempts());
            assertEquals(1000L, context.getFirstAttemptTime());
            assertEquals(1000L, context.getCurrentAttemptTime());
            assertTrue(context.getPreviousAttempts().isEmpty());
        }
        
        @Test
        @DisplayName("Should build with previous attempts")
        void testBuildWithPreviousAttempts() {
            // Arrange
            RetryContext.RetryAttempt attempt1 = RetryContext.RetryAttempt.builder()
                .attemptNumber(1)
                .attemptTime(1000L)
                .failure(new RuntimeException("First failure"))
                .durationMs(100L)
                .build();
            
            RetryContext.RetryAttempt attempt2 = RetryContext.RetryAttempt.builder()
                .attemptNumber(2)
                .attemptTime(2500L)
                .failure(new IOException("Second failure"))
                .durationMs(150L)
                .build();
            
            // Act
            RetryContext context = RetryContext.builder()
                .stepId("test-step")
                .attemptNumber(3)
                .maxAttempts(5)
                .previousAttempt(attempt1)
                .previousAttempt(attempt2)
                .firstAttemptTime(1000L)
                .currentAttemptTime(5000L)
                .build();
            
            // Assert
            assertEquals(2, context.getPreviousAttempts().size());
            assertEquals(1, context.getPreviousAttempts().get(0).getAttemptNumber());
            assertEquals(2, context.getPreviousAttempts().get(1).getAttemptNumber());
        }
    }
    
    @Nested
    @DisplayName("Retry State Tests")
    class RetryStateTests {
        
        @Test
        @DisplayName("Should correctly identify first attempt")
        void testFirstAttempt() {
            // Arrange & Act
            RetryContext firstAttempt = RetryContext.builder()
                .stepId("test")
                .attemptNumber(1)
                .maxAttempts(3)
                .firstAttemptTime(1000L)
                .currentAttemptTime(1000L)
                .build();
            
            RetryContext secondAttempt = RetryContext.builder()
                .stepId("test")
                .attemptNumber(2)
                .maxAttempts(3)
                .firstAttemptTime(1000L)
                .currentAttemptTime(2000L)
                .build();
            
            // Assert
            assertTrue(firstAttempt.isFirstAttempt());
            assertFalse(secondAttempt.isFirstAttempt());
        }
        
        @Test
        @DisplayName("Should correctly identify last attempt")
        void testLastAttempt() {
            // Arrange & Act
            RetryContext notLast = RetryContext.builder()
                .stepId("test")
                .attemptNumber(2)
                .maxAttempts(3)
                .firstAttemptTime(1000L)
                .currentAttemptTime(2000L)
                .build();
            
            RetryContext last = RetryContext.builder()
                .stepId("test")
                .attemptNumber(3)
                .maxAttempts(3)
                .firstAttemptTime(1000L)
                .currentAttemptTime(3000L)
                .build();
            
            // Assert
            assertFalse(notLast.isLastAttempt());
            assertTrue(last.isLastAttempt());
        }
        
        @Test
        @DisplayName("Should calculate remaining retries correctly")
        void testRemainingRetries() {
            // Arrange & Act
            RetryContext context1 = RetryContext.builder()
                .stepId("test")
                .attemptNumber(1)
                .maxAttempts(5)
                .firstAttemptTime(1000L)
                .currentAttemptTime(1000L)
                .build();
            
            RetryContext context2 = RetryContext.builder()
                .stepId("test")
                .attemptNumber(3)
                .maxAttempts(5)
                .firstAttemptTime(1000L)
                .currentAttemptTime(3000L)
                .build();
            
            RetryContext context3 = RetryContext.builder()
                .stepId("test")
                .attemptNumber(5)
                .maxAttempts(5)
                .firstAttemptTime(1000L)
                .currentAttemptTime(5000L)
                .build();
            
            // Assert
            assertEquals(4, context1.getRemainingRetries());
            assertEquals(2, context2.getRemainingRetries());
            assertEquals(0, context3.getRemainingRetries());
        }
        
        @Test
        @DisplayName("Should calculate total elapsed time")
        void testTotalElapsedTime() {
            // Arrange & Act
            RetryContext context = RetryContext.builder()
                .stepId("test")
                .attemptNumber(3)
                .maxAttempts(5)
                .firstAttemptTime(1000L)
                .currentAttemptTime(5500L)
                .build();
            
            // Assert
            assertEquals(4500L, context.getTotalElapsedMs());
        }
    }
    
    @Nested
    @DisplayName("RetryAttempt Tests")
    class RetryAttemptTests {
        
        @Test
        @DisplayName("Should handle null failure gracefully")
        void testNullFailure() {
            // Arrange & Act
            RetryContext.RetryAttempt attempt = RetryContext.RetryAttempt.builder()
                .attemptNumber(1)
                .attemptTime(1000L)
                .failure(null)
                .durationMs(100L)
                .build();
            
            // Assert
            assertNull(attempt.getFailure());
            assertEquals("Unknown failure", attempt.getFailureMessage());
            assertNull(attempt.getFailureType());
        }
        
        @Test
        @DisplayName("Should extract failure information")
        void testFailureInfo() {
            // Arrange
            IOException failure = new IOException("Network error");
            
            // Act
            RetryContext.RetryAttempt attempt = RetryContext.RetryAttempt.builder()
                .attemptNumber(2)
                .attemptTime(2000L)
                .failure(failure)
                .durationMs(200L)
                .build();
            
            // Assert
            assertEquals(failure, attempt.getFailure());
            assertEquals("Network error", attempt.getFailureMessage());
            assertEquals(IOException.class, attempt.getFailureType());
            assertEquals(200L, attempt.getDurationMs());
        }
    }
}