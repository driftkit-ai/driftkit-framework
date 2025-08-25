package ai.driftkit.workflow.test.core;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Registry for managing mock definitions.
 * Thread-safe implementation for concurrent test scenarios.
 */
@Slf4j
public class MockRegistry {
    
    private final Map<String, List<MockDefinition<?>>> mocks = new ConcurrentHashMap<>();
    private final Map<String, List<ConditionalMock<?>>> conditionalMocks = new ConcurrentHashMap<>();
    
    /**
     * Registers a mock for a specific workflow step.
     * 
     * @param mock the mock definition to register
     */
    public void register(MockDefinition<?> mock) {
        Objects.requireNonNull(mock, "mock cannot be null");
        
        String key = createKey(mock.getWorkflowId(), mock.getStepId());
        log.debug("Registering mock for {}", key);
        
        mocks.compute(key, (k, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(mock);
            return list;
        });
    }
    
    /**
     * Registers a conditional mock for a specific workflow step.
     * 
     * @param workflowId the workflow ID
     * @param stepId the step ID
     * @param condition the condition to evaluate
     * @param mock the mock to use when condition is true
     * @param <I> input type
     */
    public <I> void registerConditional(String workflowId, String stepId, 
                                       Predicate<I> condition, MockDefinition<?> mock) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");
        Objects.requireNonNull(condition, "condition cannot be null");
        Objects.requireNonNull(mock, "mock cannot be null");
        
        String key = createKey(workflowId, stepId);
        log.debug("Registering conditional mock for {}", key);
        
        ConditionalMock<I> conditionalMock = new ConditionalMock<>(condition, mock);
        
        conditionalMocks.compute(key, (k, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(conditionalMock);
            return list;
        });
    }
    
    /**
     * Finds a mock for the given step context.
     * 
     * @param context the step context
     * @return the mock definition or empty if not found
     */
    public Optional<MockDefinition<?>> findMock(StepContext context) {
        Objects.requireNonNull(context, "context cannot be null");
        
        String key = context.createKey();
        
        log.debug("Looking for mock with key: {} (Available keys: regular={}, conditional={})", 
            key, mocks.keySet(), conditionalMocks.keySet());
        
        // First check conditional mocks
        List<ConditionalMock<?>> conditionals = conditionalMocks.get(key);
        if (conditionals != null) {
            for (ConditionalMock<?> conditional : conditionals) {
                if (conditional.matches(context.getInput())) {
                    log.debug("Found matching conditional mock for {}", key);
                    return Optional.of(conditional.getMock());
                }
            }
        }
        
        // Then check regular mocks
        List<MockDefinition<?>> regularMocks = mocks.get(key);
        if (regularMocks != null && !regularMocks.isEmpty()) {
            // Return the most recent mock (last added)
            MockDefinition<?> mock = regularMocks.get(regularMocks.size() - 1);
            log.debug("Found regular mock for {}", key);
            return Optional.of(mock);
        }
        
        log.debug("No mock found for {}", key);
        return Optional.empty();
    }
    
    /**
     * Removes all mocks for a specific workflow step.
     * 
     * @param workflowId the workflow ID
     * @param stepId the step ID
     */
    public void remove(String workflowId, String stepId) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");
        
        String key = createKey(workflowId, stepId);
        mocks.remove(key);
        conditionalMocks.remove(key);
        
        log.debug("Removed all mocks for {}", key);
    }
    
    /**
     * Clears all registered mocks.
     */
    public void clear() {
        log.debug("Clearing all mocks");
        mocks.clear();
        conditionalMocks.clear();
    }
    
    /**
     * Gets the number of registered mocks.
     * 
     * @return total mock count
     */
    public int size() {
        int regularCount = mocks.values().stream()
            .mapToInt(List::size)
            .sum();
        int conditionalCount = conditionalMocks.values().stream()
            .mapToInt(List::size)
            .sum();
        return regularCount + conditionalCount;
    }
    
    /**
     * Creates a unique key for workflow and step.
     * 
     * @param workflowId the workflow ID
     * @param stepId the step ID
     * @return unique key
     */
    private String createKey(String workflowId, String stepId) {
        return workflowId + "." + stepId;
    }
    
    /**
     * Container for conditional mocks.
     */
    private static class ConditionalMock<I> {
        private final Predicate<I> condition;
        private final MockDefinition<?> mock;
        
        ConditionalMock(Predicate<I> condition, MockDefinition<?> mock) {
            this.condition = condition;
            this.mock = mock;
        }
        
        @SuppressWarnings("unchecked")
        boolean matches(Object input) {
            try {
                return condition.test((I) input);
            } catch (ClassCastException e) {
                return false;
            }
        }
        
        MockDefinition<?> getMock() {
            return mock;
        }
    }
}