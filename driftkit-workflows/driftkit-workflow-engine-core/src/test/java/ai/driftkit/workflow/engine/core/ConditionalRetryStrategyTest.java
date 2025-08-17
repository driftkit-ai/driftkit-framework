package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import ai.driftkit.workflow.engine.builder.RetryPolicyBuilder;
import ai.driftkit.workflow.engine.domain.RetryContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConditionalRetryStrategy.
 */
class ConditionalRetryStrategyTest {
    
    private ConditionalRetryStrategy strategy;
    private RetryContext context;
    
    @BeforeEach
    void setUp() {
        strategy = new ConditionalRetryStrategy();
        context = RetryContext.builder()
            .stepId("test-step")
            .attemptNumber(1)
            .maxAttempts(3)
            .firstAttemptTime(System.currentTimeMillis())
            .currentAttemptTime(System.currentTimeMillis())
            .build();
    }
    
    @Nested
    @DisplayName("Retry On Conditions")
    class RetryOnTests {
        
        @Test
        @DisplayName("Should retry only on specified exceptions")
        void shouldRetryOnlySpecifiedExceptions() {
            RetryPolicy policy = new RetryPolicyBuilder()
                .withMaxAttempts(3)
                .withRetryOn(IOException.class, TimeoutException.class)
                .build();
            
            // Should retry on IOException
            assertTrue(strategy.shouldRetry(new IOException("Network error"), context, policy));
            
            // Should retry on TimeoutException
            assertTrue(strategy.shouldRetry(new TimeoutException("Timeout"), context, policy));
            
            // Should NOT retry on other exceptions
            assertFalse(strategy.shouldRetry(new IllegalArgumentException("Bad input"), context, policy));
            assertFalse(strategy.shouldRetry(new RuntimeException("Generic error"), context, policy));
        }
        
        @Test
        @DisplayName("Should consider inheritance in retry conditions")
        void shouldConsiderInheritance() {
            RetryPolicy policy = new RetryPolicyBuilder()
                .withMaxAttempts(3)
                .withRetryOn(IOException.class)
                .build();
            
            // Should retry on IOException subclasses
            assertTrue(strategy.shouldRetry(new java.io.FileNotFoundException("File not found"), context, policy));
            assertTrue(strategy.shouldRetry(new java.net.SocketException("Socket error"), context, policy));
        }
        
        @Test
        @DisplayName("Should check root cause for retry conditions")
        void shouldCheckRootCause() {
            RetryPolicy policy = new RetryPolicyBuilder()
                .withMaxAttempts(3)
                .withRetryOn(SQLException.class)
                .build();
            
            // Wrapped SQLException should be retried
            Exception wrapped = new RuntimeException("Wrapper", 
                new IllegalStateException("Middle", 
                    new SQLException("Database error")));
            
            assertTrue(strategy.shouldRetry(wrapped, context, policy));
        }
    }
    
    @Nested
    @DisplayName("Abort On Conditions")
    class AbortOnTests {
        
        @Test
        @DisplayName("Should abort on specified exceptions")
        void shouldAbortOnSpecifiedExceptions() {
            RetryPolicy policy = new RetryPolicyBuilder()
                .withMaxAttempts(3)
                .withAbortOn(IllegalArgumentException.class, SecurityException.class)
                .build();
            
            // Should abort on IllegalArgumentException
            assertFalse(strategy.shouldRetry(new IllegalArgumentException("Bad input"), context, policy));
            
            // Should abort on SecurityException
            assertFalse(strategy.shouldRetry(new SecurityException("Access denied"), context, policy));
            
            // Should retry other exceptions
            assertTrue(strategy.shouldRetry(new IOException("Network error"), context, policy));
        }
        
        @Test
        @DisplayName("Abort conditions should take precedence over retry conditions")
        void abortShouldTakePrecedence() {
            RetryPolicy policy = new RetryPolicyBuilder()
                .withMaxAttempts(3)
                .withRetryOn(Exception.class)  // Retry all exceptions
                .withAbortOn(IllegalArgumentException.class)  // But abort on this one
                .build();
            
            // Should abort even though Exception is in retry list
            assertFalse(strategy.shouldRetry(new IllegalArgumentException("Bad input"), context, policy));
            
            // Should retry other exceptions
            assertTrue(strategy.shouldRetry(new IOException("Network error"), context, policy));
        }
    }
    
    @Nested
    @DisplayName("Combined Conditions")
    class CombinedConditionsTests {
        
        @Test
        @DisplayName("Should handle complex retry/abort combinations")
        void shouldHandleComplexConditions() {
            RetryPolicy policy = new RetryPolicyBuilder()
                .withMaxAttempts(5)
                .withRetryOn(IOException.class, SQLException.class, TimeoutException.class)
                .withAbortOn(SecurityException.class, IllegalArgumentException.class)
                .build();
            
            // Should retry on listed exceptions
            assertTrue(strategy.shouldRetry(new IOException(), context, policy));
            assertTrue(strategy.shouldRetry(new SQLException(), context, policy));
            assertTrue(strategy.shouldRetry(new TimeoutException(), context, policy));
            
            // Should abort on abort list
            assertFalse(strategy.shouldRetry(new SecurityException(), context, policy));
            assertFalse(strategy.shouldRetry(new IllegalArgumentException(), context, policy));
            
            // Should not retry unlisted exceptions
            assertFalse(strategy.shouldRetry(new RuntimeException(), context, policy));
            assertFalse(strategy.shouldRetry(new NullPointerException(), context, policy));
        }
        
        @Test
        @DisplayName("Should use default behavior when no conditions specified")
        void shouldUseDefaultWithNoConditions() {
            RetryPolicy policy = new RetryPolicyBuilder()
                .withMaxAttempts(3)
                .build();
            
            // Should retry any exception by default
            assertTrue(strategy.shouldRetry(new RuntimeException(), context, policy));
            assertTrue(strategy.shouldRetry(new IOException(), context, policy));
            assertTrue(strategy.shouldRetry(new IllegalArgumentException(), context, policy));
        }
    }
    
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("Should handle empty arrays")
        void shouldHandleEmptyArrays() {
            RetryPolicy policy = new RetryPolicyBuilder()
                .withMaxAttempts(3)
                .withRetryOn()  // Empty array
                .withAbortOn()  // Empty array
                .build();
            
            // Should use default behavior (retry all)
            assertTrue(strategy.shouldRetry(new RuntimeException(), context, policy));
        }
        
        @Test
        @DisplayName("Should still respect max attempts")
        void shouldRespectMaxAttempts() {
            RetryPolicy policy = new RetryPolicyBuilder()
                .withMaxAttempts(2)
                .withRetryOn(IOException.class)
                .build();
            
            // First attempt should retry
            assertTrue(strategy.shouldRetry(new IOException(), context, policy));
            
            // Beyond max attempts should not retry
            RetryContext maxedContext = RetryContext.builder()
                .stepId("test-step")
                .attemptNumber(2)
                .maxAttempts(2)
                .firstAttemptTime(System.currentTimeMillis())
                .currentAttemptTime(System.currentTimeMillis())
                .build();
            
            assertFalse(strategy.shouldRetry(new IOException(), maxedContext, policy));
        }
    }
}