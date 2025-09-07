package ai.driftkit.workflow.engine.annotations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {
    
    @Nested
    @DisplayName("Annotation Declaration Tests")
    class AnnotationDeclarationTests {
        
        @Test
        @DisplayName("Should have default values")
        void testDefaultValues() throws NoSuchMethodException {
            // Arrange
            class TestClass {
                @Step(retryPolicy = @RetryPolicy())
                public void testMethod() {}
            }
            
            // Act
            Step step = TestClass.class.getMethod("testMethod").getAnnotation(Step.class);
            RetryPolicy policy = step.retryPolicy();
            
            // Assert
            assertEquals(3, policy.maxAttempts(), "Default max attempts should be 3");
            assertEquals(1000L, policy.delay(), "Default delay should be 1000ms");
            assertEquals(1.0, policy.backoffMultiplier(), "Default backoff multiplier should be 1.0");
            assertEquals(60000L, policy.maxDelay(), "Default max delay should be 60000ms");
            assertEquals(0.1, policy.jitterFactor(), "Default jitter factor should be 0.1");
            assertEquals(0, policy.retryOn().length, "Default retryOn should be empty");
            assertEquals(0, policy.abortOn().length, "Default abortOn should be empty");
            assertFalse(policy.retryOnFailResult(), "Default retryOnFailResult should be false");
        }
        
        @Test
        @DisplayName("Should accept custom values")
        void testCustomValues() throws NoSuchMethodException {
            // Arrange & Act
            Method method = TestSteps.class.getMethod("customRetryStep");
            Step step = method.getAnnotation(Step.class);
            RetryPolicy policy = step.retryPolicy();
            
            // Assert
            assertEquals(5, policy.maxAttempts());
            assertEquals(2000L, policy.delay());
            assertEquals(2.0, policy.backoffMultiplier());
            assertEquals(30000L, policy.maxDelay());
            assertEquals(0.2, policy.jitterFactor());
            assertEquals(2, policy.retryOn().length);
            assertArrayEquals(new Class[]{IOException.class, RuntimeException.class}, policy.retryOn());
            assertEquals(1, policy.abortOn().length);
            assertArrayEquals(new Class[]{IllegalArgumentException.class}, policy.abortOn());
            assertTrue(policy.retryOnFailResult());
        }
    }
    
    @Nested
    @DisplayName("Step Integration Tests")
    class StepIntegrationTests {
        
        @Test
        @DisplayName("Should work with Step annotation")
        void testStepIntegration() throws NoSuchMethodException {
            // Arrange
            Method method = TestSteps.class.getMethod("simpleRetryStep");
            
            // Act
            Step step = method.getAnnotation(Step.class);
            
            // Assert
            assertNotNull(step, "Step annotation should be present");
            assertNotNull(step.retryPolicy(), "RetryPolicy should not be null");
            assertEquals(3, step.retryPolicy().maxAttempts());
        }
        
        @Test
        @DisplayName("Should work with invocation limit")
        void testInvocationLimit() throws NoSuchMethodException {
            // Arrange
            Method method = TestSteps.class.getMethod("limitedStep");
            
            // Act
            Step step = method.getAnnotation(Step.class);
            
            // Assert
            assertEquals(10, step.invocationLimit());
            assertEquals(OnInvocationsLimit.STOP, step.onInvocationsLimit());
        }
    }
    
    // Test class with various step configurations
    static class TestSteps {
        
        @Step(retryPolicy = @RetryPolicy())
        public void simpleRetryStep() {}
        
        @Step(
            retryPolicy = @RetryPolicy(
                maxAttempts = 5,
                delay = 2000,
                backoffMultiplier = 2.0,
                maxDelay = 30000,
                jitterFactor = 0.2,
                retryOn = {IOException.class, RuntimeException.class},
                abortOn = {IllegalArgumentException.class},
                retryOnFailResult = true
            )
        )
        public void customRetryStep() {}
        
        @Step(
            invocationLimit = 10,
            onInvocationsLimit = OnInvocationsLimit.STOP
        )
        public void limitedStep() {}
    }
}