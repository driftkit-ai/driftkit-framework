package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyBuilderTest {
    
    @Test
    @DisplayName("Should build with default values")
    void testDefaultValues() {
        // Act
        RetryPolicy policy = RetryPolicyBuilder.retry().build();
        
        // Assert
        assertEquals(3, policy.maxAttempts());
        assertEquals(1000, policy.delay());
        assertEquals(1.0, policy.backoffMultiplier());
        assertEquals(60000, policy.maxDelay());
        assertEquals(0.1, policy.jitterFactor());
        assertEquals(0, policy.retryOn().length);
        assertEquals(0, policy.abortOn().length);
        assertFalse(policy.retryOnFailResult());
    }
    
    @Test
    @DisplayName("Should build with custom values")
    void testCustomValues() {
        // Act
        RetryPolicy policy = RetryPolicyBuilder.retry()
            .withMaxAttempts(5)
            .withDelay(2000)
            .withBackoffMultiplier(2.5)
            .withMaxDelay(30000)
            .withJitterFactor(0.2)
            .withRetryOn(new Class[]{IOException.class, SQLException.class})
            .withAbortOn(new Class[]{IllegalArgumentException.class})
            .withRetryOnFailResult(true)
            .build();
        
        // Assert
        assertEquals(5, policy.maxAttempts());
        assertEquals(2000, policy.delay());
        assertEquals(2.5, policy.backoffMultiplier());
        assertEquals(30000, policy.maxDelay());
        assertEquals(0.2, policy.jitterFactor());
        assertEquals(2, policy.retryOn().length);
        assertArrayEquals(new Class[]{IOException.class, SQLException.class}, policy.retryOn());
        assertEquals(1, policy.abortOn().length);
        assertArrayEquals(new Class[]{IllegalArgumentException.class}, policy.abortOn());
        assertTrue(policy.retryOnFailResult());
    }
    
    @Test
    @DisplayName("Should configure exponential backoff")
    void testExponentialBackoff() {
        // Act
        RetryPolicy policy = RetryPolicyBuilder.retry()
            .exponentialBackoff()
            .build();
        
        // Assert
        assertEquals(2.0, policy.backoffMultiplier());
        assertEquals(30000, policy.maxDelay());
    }
    
    @Test
    @DisplayName("Should configure linear backoff")
    void testLinearBackoff() {
        // Act
        RetryPolicy policy = RetryPolicyBuilder.retry()
            .withBackoffMultiplier(3.0) // Set to something else first
            .linearBackoff()
            .build();
        
        // Assert
        assertEquals(1.0, policy.backoffMultiplier());
    }
    
    @Test
    @DisplayName("Should support fluent chaining")
    void testFluentChaining() {
        // Act
        RetryPolicy policy = RetryPolicyBuilder.retry()
            .withMaxAttempts(10)
            .exponentialBackoff()
            .withJitterFactor(0.5)
            .withRetryOn(new Class[]{RuntimeException.class})
            .build();
        
        // Assert
        assertEquals(10, policy.maxAttempts());
        assertEquals(2.0, policy.backoffMultiplier());
        assertEquals(0.5, policy.jitterFactor());
        assertEquals(1, policy.retryOn().length);
        assertEquals(RuntimeException.class, policy.retryOn()[0]);
    }
}