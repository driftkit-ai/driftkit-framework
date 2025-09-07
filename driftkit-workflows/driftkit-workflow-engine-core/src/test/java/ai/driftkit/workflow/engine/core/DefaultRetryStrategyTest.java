package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import ai.driftkit.workflow.engine.domain.RetryContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.io.IOException;
import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;

class DefaultRetryStrategyTest {
    
    private DefaultRetryStrategy strategy;
    
    @BeforeEach
    void setUp() {
        strategy = new DefaultRetryStrategy();
    }
    
    @Nested
    @DisplayName("Should Retry Tests")
    class ShouldRetryTests {
        
        @Test
        @DisplayName("Should not retry when max attempts reached")
        void testMaxAttemptsReached() {
            // Arrange
            RetryContext context = RetryContext.builder()
                .stepId("test")
                .attemptNumber(3)
                .maxAttempts(3)
                .firstAttemptTime(1000L)
                .currentAttemptTime(3000L)
                .build();
            
            RetryPolicy policy = createRetryPolicy(3, 1000, 1.0);
            RuntimeException failure = new RuntimeException("Test error");
            
            // Act
            boolean shouldRetry = strategy.shouldRetry(failure, context, policy);
            
            // Assert
            assertFalse(shouldRetry, "Should not retry when max attempts reached");
        }
        
        @Test
        @DisplayName("Should retry when attempts remaining")
        void testAttemptsRemaining() {
            // Arrange
            RetryContext context = RetryContext.builder()
                .stepId("test")
                .attemptNumber(2)
                .maxAttempts(5)
                .firstAttemptTime(1000L)
                .currentAttemptTime(2000L)
                .build();
            
            RetryPolicy policy = createRetryPolicy(5, 1000, 1.0);
            RuntimeException failure = new RuntimeException("Test error");
            
            // Act
            boolean shouldRetry = strategy.shouldRetry(failure, context, policy);
            
            // Assert
            assertTrue(shouldRetry, "Should retry when attempts remaining");
        }
        
        @Test
        @DisplayName("Should respect retryOn exceptions")
        void testRetryOnExceptions() {
            // Arrange
            RetryContext context = RetryContext.builder()
                .stepId("test")
                .attemptNumber(1)
                .maxAttempts(3)
                .firstAttemptTime(1000L)
                .currentAttemptTime(1000L)
                .build();
            
            RetryPolicy policy = createRetryPolicyWithExceptions(
                new Class[]{IOException.class, RuntimeException.class},
                new Class[]{}
            );
            
            // Act & Assert
            assertTrue(strategy.shouldRetry(new IOException("IO error"), context, policy));
            assertTrue(strategy.shouldRetry(new RuntimeException("Runtime error"), context, policy));
            assertFalse(strategy.shouldRetry(new ClassNotFoundException("Not in retry list"), context, policy));
        }
        
        @Test
        @DisplayName("Should respect abortOn exceptions")
        void testAbortOnExceptions() {
            // Arrange
            RetryContext context = RetryContext.builder()
                .stepId("test")
                .attemptNumber(1)
                .maxAttempts(3)
                .firstAttemptTime(1000L)
                .currentAttemptTime(1000L)
                .build();
            
            RetryPolicy policy = createRetryPolicyWithExceptions(
                new Class[]{Exception.class},
                new Class[]{IllegalArgumentException.class}
            );
            
            // Act & Assert
            assertTrue(strategy.shouldRetry(new IOException("Should retry"), context, policy));
            assertFalse(strategy.shouldRetry(new IllegalArgumentException("Should abort"), context, policy));
        }
    }
    
    @Nested
    @DisplayName("Calculate Delay Tests")
    class CalculateDelayTests {
        
        @Test
        @DisplayName("Should calculate constant delay without backoff")
        void testConstantDelay() {
            // Arrange
            RetryPolicy policy = createRetryPolicy(3, 1000, 1.0);
            
            // Act & Assert for multiple attempts
            for (int attempt = 1; attempt <= 3; attempt++) {
                RetryContext context = RetryContext.builder()
                    .stepId("test")
                    .attemptNumber(attempt)
                    .maxAttempts(3)
                    .firstAttemptTime(1000L)
                    .currentAttemptTime(1000L + (attempt - 1) * 1000L)
                    .build();
                
                long delay = strategy.calculateDelay(context, policy);
                
                // With 10% jitter, delay should be between 1000 and 1100
                assertTrue(delay >= 1000 && delay <= 1100, 
                    "Delay should be around 1000ms with jitter for attempt " + attempt);
            }
        }
        
        @Test
        @DisplayName("Should calculate exponential backoff")
        void testExponentialBackoff() {
            // Arrange
            RetryPolicy policy = createRetryPolicy(5, 1000, 2.0, 10000, 0.0);
            
            // Act & Assert
            // Attempt 1: 1000ms
            RetryContext context1 = RetryContext.builder()
                .stepId("test")
                .attemptNumber(1)
                .maxAttempts(5)
                .firstAttemptTime(1000L)
                .currentAttemptTime(1000L)
                .build();
            assertEquals(1000L, strategy.calculateDelay(context1, policy));
            
            // Attempt 2: 2000ms
            RetryContext context2 = RetryContext.builder()
                .stepId("test")
                .attemptNumber(2)
                .maxAttempts(5)
                .firstAttemptTime(1000L)
                .currentAttemptTime(2000L)
                .build();
            assertEquals(2000L, strategy.calculateDelay(context2, policy));
            
            // Attempt 3: 4000ms
            RetryContext context3 = RetryContext.builder()
                .stepId("test")
                .attemptNumber(3)
                .maxAttempts(5)
                .firstAttemptTime(1000L)
                .currentAttemptTime(4000L)
                .build();
            assertEquals(4000L, strategy.calculateDelay(context3, policy));
            
            // Attempt 4: 8000ms
            RetryContext context4 = RetryContext.builder()
                .stepId("test")
                .attemptNumber(4)
                .maxAttempts(5)
                .firstAttemptTime(1000L)
                .currentAttemptTime(8000L)
                .build();
            assertEquals(8000L, strategy.calculateDelay(context4, policy));
        }
        
        @Test
        @DisplayName("Should respect max delay cap")
        void testMaxDelayCap() {
            // Arrange
            RetryPolicy policy = createRetryPolicy(5, 1000, 2.0, 5000, 0.0);
            
            // Act - Attempt 4 would be 8000ms but should be capped at 5000ms
            RetryContext context = RetryContext.builder()
                .stepId("test")
                .attemptNumber(4)
                .maxAttempts(5)
                .firstAttemptTime(1000L)
                .currentAttemptTime(10000L)
                .build();
            
            long delay = strategy.calculateDelay(context, policy);
            
            // Assert
            assertEquals(5000L, delay, "Delay should be capped at max delay");
        }
        
        @Test
        @DisplayName("Should apply jitter")
        void testJitter() {
            // Arrange
            RetryPolicy policy = createRetryPolicy(3, 1000, 1.0, 60000, 0.5);
            RetryContext context = RetryContext.builder()
                .stepId("test")
                .attemptNumber(1)
                .maxAttempts(3)
                .firstAttemptTime(1000L)
                .currentAttemptTime(1000L)
                .build();
            
            // Act - Run multiple times to test randomness
            boolean hasVariation = false;
            long firstDelay = strategy.calculateDelay(context, policy);
            
            for (int i = 0; i < 100; i++) {
                long delay = strategy.calculateDelay(context, policy);
                if (delay != firstDelay) {
                    hasVariation = true;
                    break;
                }
            }
            
            // Assert
            assertTrue(hasVariation, "Jitter should cause delay variation");
        }
    }
    
    // Helper methods to create RetryPolicy instances
    private RetryPolicy createRetryPolicy(int maxAttempts, long delay, double backoffMultiplier) {
        return createRetryPolicy(maxAttempts, delay, backoffMultiplier, 60000, 0.1);
    }
    
    private RetryPolicy createRetryPolicy(int maxAttempts, long delay, double backoffMultiplier, 
                                        long maxDelay, double jitterFactor) {
        return new RetryPolicy() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return RetryPolicy.class;
            }
            
            @Override
            public int maxAttempts() {
                return maxAttempts;
            }
            
            @Override
            public long delay() {
                return delay;
            }
            
            @Override
            public double backoffMultiplier() {
                return backoffMultiplier;
            }
            
            @Override
            public long maxDelay() {
                return maxDelay;
            }
            
            @Override
            public double jitterFactor() {
                return jitterFactor;
            }
            
            @Override
            public Class<? extends Throwable>[] retryOn() {
                return new Class[0];
            }
            
            @Override
            public Class<? extends Throwable>[] abortOn() {
                return new Class[0];
            }
            
            @Override
            public boolean retryOnFailResult() {
                return false;
            }
        };
    }
    
    private RetryPolicy createRetryPolicyWithExceptions(Class<? extends Throwable>[] retryOn,
                                                       Class<? extends Throwable>[] abortOn) {
        return new RetryPolicy() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return RetryPolicy.class;
            }
            
            @Override
            public int maxAttempts() {
                return 3;
            }
            
            @Override
            public long delay() {
                return 1000;
            }
            
            @Override
            public double backoffMultiplier() {
                return 1.0;
            }
            
            @Override
            public long maxDelay() {
                return 60000;
            }
            
            @Override
            public double jitterFactor() {
                return 0.1;
            }
            
            @Override
            public Class<? extends Throwable>[] retryOn() {
                return retryOn;
            }
            
            @Override
            public Class<? extends Throwable>[] abortOn() {
                return abortOn;
            }
            
            @Override
            public boolean retryOnFailResult() {
                return false;
            }
        };
    }
}