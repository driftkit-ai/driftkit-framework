package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.annotations.OnInvocationsLimit;
import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import ai.driftkit.workflow.engine.core.StepResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class StepDefinitionRetryTest {
    
    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {
        
        @Test
        @DisplayName("Should build with retry policy")
        void testWithRetryPolicy() {
            // Arrange
            RetryPolicy retryPolicy = RetryPolicyBuilder.retry()
                .withMaxAttempts(5)
                .withDelay(2000)
                .exponentialBackoff()
                .build();
            
            // Act
            StepDefinition step = StepDefinition.of("test-step", (String input) -> StepResult.continueWith(input))
                .withTypes(String.class, String.class)
                .withRetryPolicy(retryPolicy);
            
            // Assert
            assertNotNull(step.getRetryPolicy());
            assertEquals(5, step.getRetryPolicy().maxAttempts());
            assertEquals(2000, step.getRetryPolicy().delay());
            assertEquals(2.0, step.getRetryPolicy().backoffMultiplier());
        }
        
        @Test
        @DisplayName("Should build with invocation limit")
        void testWithInvocationLimit() {
            // Act
            StepDefinition step = StepDefinition.of("test-step", (String input) -> StepResult.continueWith(input))
                .withTypes(String.class, String.class)
                .withInvocationLimit(10)
                .withOnInvocationsLimit(OnInvocationsLimit.STOP);
            
            // Assert
            assertEquals(10, step.getInvocationLimit());
            assertEquals(OnInvocationsLimit.STOP, step.getOnInvocationsLimit());
        }
        
        @Test
        @DisplayName("Should build with invocation control")
        void testWithInvocationControl() {
            // Act
            StepDefinition step = StepDefinition.of("test-step", (String input) -> StepResult.continueWith(input))
                .withTypes(String.class, String.class)
                .withInvocationControl(5, OnInvocationsLimit.CONTINUE);
            
            // Assert
            assertEquals(5, step.getInvocationLimit());
            assertEquals(OnInvocationsLimit.CONTINUE, step.getOnInvocationsLimit());
        }
        
        @Test
        @DisplayName("Should chain retry configuration fluently")
        void testFluentRetryConfiguration() {
            // Arrange
            RetryPolicy retryPolicy = RetryPolicyBuilder.retry()
                .withMaxAttempts(3)
                .withDelay(1000)
                .withRetryOn(new Class[]{IOException.class})
                .build();
            
            // Act
            StepDefinition step = StepDefinition.of("complex-step", (String input) -> StepResult.continueWith(input))
                .withTypes(String.class, String.class)
                .withDescription("A complex step with retry")
                .withRetryPolicy(retryPolicy)
                .withInvocationLimit(20)
                .withOnInvocationsLimit(OnInvocationsLimit.ERROR);
            
            // Assert
            assertEquals("complex-step", step.getId());
            assertEquals("A complex step with retry", step.getDescription());
            assertNotNull(step.getRetryPolicy());
            assertEquals(3, step.getRetryPolicy().maxAttempts());
            assertEquals(20, step.getInvocationLimit());
            assertEquals(OnInvocationsLimit.ERROR, step.getOnInvocationsLimit());
        }
    }
    
    @Nested
    @DisplayName("Default Values Tests")
    class DefaultValuesTests {
        
        @Test
        @DisplayName("Should have default invocation limit")
        void testDefaultInvocationLimit() {
            // Act
            StepDefinition step = StepDefinition.of("test-step", (String input) -> StepResult.continueWith(input))
                .withTypes(String.class, String.class);
            
            // Assert
            assertEquals(100, step.getInvocationLimit());
            assertEquals(OnInvocationsLimit.ERROR, step.getOnInvocationsLimit());
        }
        
        @Test
        @DisplayName("Should have null retry policy by default")
        void testDefaultRetryPolicy() {
            // Act
            StepDefinition step = StepDefinition.of("test-step", (String input) -> StepResult.continueWith(input))
                .withTypes(String.class, String.class);
            
            // Assert
            assertNull(step.getRetryPolicy());
        }
    }
}