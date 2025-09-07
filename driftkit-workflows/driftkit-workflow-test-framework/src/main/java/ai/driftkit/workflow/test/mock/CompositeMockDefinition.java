package ai.driftkit.workflow.test.mock;

import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.test.core.MockDefinition;
import ai.driftkit.workflow.test.core.StepContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Composite mock that delegates to multiple mocks based on conditions.
 * Enables complex mocking scenarios.
 */
@Slf4j
public class CompositeMockDefinition extends MockDefinition<Object> {
    
    private final List<ConditionalMock> mocks = new ArrayList<>();
    private MockDefinition<?> defaultMock;
    
    public CompositeMockDefinition(String workflowId, String stepId) {
        super(workflowId, stepId, Object.class, (input, context) -> {
            throw new IllegalStateException("CompositeMockDefinition.execute should handle execution");
        });
    }
    
    /**
     * Adds a conditional mock.
     * 
     * @param condition the condition to check
     * @param mock the mock to use when condition is true
     * @param <I> input type
     * @return this for chaining
     */
    public <I> CompositeMockDefinition when(Class<I> inputType, 
                                           java.util.function.Predicate<I> condition, 
                                           MockDefinition<?> mock) {
        Objects.requireNonNull(condition, "condition cannot be null");
        Objects.requireNonNull(mock, "mock cannot be null");
        
        mocks.add(new ConditionalMock(inputType, condition, mock));
        return this;
    }
    
    /**
     * Sets the default mock when no conditions match.
     * 
     * @param mock the default mock
     * @return this for chaining
     */
    public CompositeMockDefinition otherwise(MockDefinition<?> mock) {
        this.defaultMock = Objects.requireNonNull(mock, "mock cannot be null");
        return this;
    }
    
    @Override
    public StepResult<?> execute(Object input, StepContext context) {
        log.debug("Executing composite mock for {}.{}", workflowId, stepId);
        
        // Try each conditional mock
        for (ConditionalMock conditional : mocks) {
            if (conditional.matches(input)) {
                log.debug("Condition matched, delegating to mock");
                return conditional.mock.execute(input, context);
            }
        }
        
        // Use default if no condition matched
        if (defaultMock != null) {
            log.debug("No condition matched, using default mock");
            return defaultMock.execute(input, context);
        }
        
        // No mock matched
        throw new IllegalStateException(
            "No mock matched for input in composite mock: " + workflowId + "." + stepId
        );
    }
    
    /**
     * Creates a builder for composite mocks.
     */
    public static Builder builder(String workflowId, String stepId) {
        return new Builder(workflowId, stepId);
    }
    
    /**
     * Builder for composite mocks.
     */
    public static class Builder {
        private final CompositeMockDefinition composite;
        
        Builder(String workflowId, String stepId) {
            this.composite = new CompositeMockDefinition(workflowId, stepId);
        }
        
        public <I> Builder when(Class<I> inputType, 
                               java.util.function.Predicate<I> condition,
                               java.util.function.Function<I, StepResult<?>> function) {
            MockDefinition<I> mock = MockDefinition.ofAny(
                composite.workflowId, 
                composite.stepId, 
                inputType, 
                function
            );
            composite.when(inputType, condition, mock);
            return this;
        }
        
        public Builder otherwise(java.util.function.Function<Object, StepResult<?>> function) {
            composite.otherwise(MockDefinition.ofAny(
                composite.workflowId,
                composite.stepId,
                Object.class,
                function
            ));
            return this;
        }
        
        public CompositeMockDefinition build() {
            return composite;
        }
    }
    
    /**
     * Internal class for conditional mocks.
     */
    private static class ConditionalMock {
        private final Class<?> inputType;
        private final java.util.function.Predicate<?> condition;
        private final MockDefinition<?> mock;
        
        ConditionalMock(Class<?> inputType, 
                       java.util.function.Predicate<?> condition,
                       MockDefinition<?> mock) {
            this.inputType = inputType;
            this.condition = condition;
            this.mock = mock;
        }
        
        @SuppressWarnings("unchecked")
        boolean matches(Object input) {
            if (!inputType.isInstance(input)) {
                return false;
            }
            
            try {
                return ((java.util.function.Predicate<Object>) condition).test(input);
            } catch (ClassCastException e) {
                log.warn("Failed to apply condition", e);
                return false;
            }
        }
    }
}