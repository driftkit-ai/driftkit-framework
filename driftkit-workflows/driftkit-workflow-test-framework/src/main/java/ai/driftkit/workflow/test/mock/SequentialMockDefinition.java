package ai.driftkit.workflow.test.mock;

import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.test.core.MockDefinition;
import ai.driftkit.workflow.test.core.StepContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock that returns different results on sequential calls.
 * Useful for testing retry behavior and state changes.
 */
@Slf4j
public class SequentialMockDefinition extends MockDefinition<Object> {
    
    private final List<MockDefinition<?>> sequence;
    private final AtomicInteger callCount = new AtomicInteger(0);
    private final Object lock = new Object();
    private volatile MockDefinition<?> afterSequence;
    
    public SequentialMockDefinition(String workflowId, String stepId) {
        super(workflowId, stepId, Object.class, (input, context) -> {
            throw new IllegalStateException("SequentialMockDefinition.execute should handle execution");
        });
        this.sequence = new ArrayList<>();
    }
    
    /**
     * Adds a mock to the sequence.
     * 
     * @param mock the mock to add
     * @return this for chaining
     */
    public SequentialMockDefinition then(MockDefinition<?> mock) {
        synchronized (lock) {
            sequence.add(mock);
        }
        return this;
    }
    
    /**
     * Sets the mock to use after sequence is exhausted.
     * 
     * @param mock the mock to use
     * @return this for chaining
     */
    public SequentialMockDefinition thenAlways(MockDefinition<?> mock) {
        this.afterSequence = mock;
        return this;
    }
    
    @Override
    public StepResult<?> execute(Object input, StepContext context) {
        int index = callCount.getAndIncrement();
        
        log.debug("Executing sequential mock for {}.{}, call #{}", 
            workflowId, stepId, index + 1);
        
        MockDefinition<?> mockToExecute;
        synchronized (lock) {
            if (index < sequence.size()) {
                mockToExecute = sequence.get(index);
            } else if (afterSequence != null) {
                mockToExecute = afterSequence;
            } else {
                throw new IllegalStateException(
                    "Sequential mock exhausted for " + workflowId + "." + stepId + 
                    " after " + sequence.size() + " calls"
                );
            }
        }
        
        return mockToExecute.execute(input, context);
    }
    
    /**
     * Resets the call count.
     */
    public void reset() {
        callCount.set(0);
    }
    
    /**
     * Creates a builder for sequential mocks.
     */
    public static <I> Builder<I> builder(String workflowId, String stepId, Class<I> inputType) {
        return new Builder<>(workflowId, stepId, inputType);
    }
    
    /**
     * Builder for sequential mocks.
     */
    public static class Builder<I> {
        private final String workflowId;
        private final String stepId;
        private final Class<I> inputType;
        private final SequentialMockDefinition sequential;
        
        Builder(String workflowId, String stepId, Class<I> inputType) {
            this.workflowId = workflowId;
            this.stepId = stepId;
            this.inputType = inputType;
            this.sequential = new SequentialMockDefinition(workflowId, stepId);
        }
        
        /**
         * Adds a successful response to the sequence.
         */
        public Builder<I> thenReturn(java.util.function.Function<I, StepResult<?>> function) {
            sequential.then(MockDefinition.ofAny(workflowId, stepId, inputType, function));
            return this;
        }
        
        /**
         * Adds a failure to the sequence.
         */
        public Builder<I> thenFail(Exception exception) {
            sequential.then(MockDefinition.throwing(workflowId, stepId, inputType, exception));
            return this;
        }
        
        /**
         * Adds multiple failures to the sequence.
         */
        public Builder<I> thenFailTimes(int times, Exception exception) {
            MockDefinition<I> failMock = MockDefinition.throwing(workflowId, stepId, inputType, exception);
            for (int i = 0; i < times; i++) {
                sequential.then(failMock);
            }
            return this;
        }
        
        /**
         * Sets the behavior after sequence is exhausted.
         */
        public Builder<I> thenAlwaysReturn(java.util.function.Function<I, StepResult<?>> function) {
            sequential.thenAlways(MockDefinition.ofAny(workflowId, stepId, inputType, function));
            return this;
        }
        
        /**
         * Sets failure after sequence is exhausted.
         */
        public Builder<I> thenAlwaysFail(Exception exception) {
            sequential.thenAlways(MockDefinition.throwing(workflowId, stepId, inputType, exception));
            return this;
        }
        
        public SequentialMockDefinition build() {
            return sequential;
        }
    }
}