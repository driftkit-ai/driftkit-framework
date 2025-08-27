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
     * @param inputType the expected input type for type safety
     * @param condition the condition to evaluate
     * @param mock the mock to use when condition is true
     * @param <I> input type
     */
    public <I> void registerConditional(String workflowId, String stepId, 
                                       Class<I> inputType, Predicate<I> condition, MockDefinition<?> mock) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");
        Objects.requireNonNull(inputType, "inputType cannot be null");
        Objects.requireNonNull(condition, "condition cannot be null");
        Objects.requireNonNull(mock, "mock cannot be null");
        
        String key = createKey(workflowId, stepId);
        log.debug("Registering conditional mock for {}", key);
        
        ConditionalMock<I> conditionalMock = new ConditionalMock<>(inputType, condition, mock);
        
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
     * Supports both exact matches and partial matches for steps with branch prefixes.
     * 
     * @param context the step context
     * @return the mock definition or empty if not found
     */
    public Optional<MockDefinition<?>> findMock(StepContext context) {
        Objects.requireNonNull(context, "context cannot be null");
        
        String key = context.createKey();
        String workflowId = context.getWorkflowId();
        String stepId = context.getStepId();
        
        log.debug("Looking for mock with key: {} (Available keys: regular={}, conditional={})", 
            key, mocks.keySet(), conditionalMocks.keySet());
        
        // Try exact match first
        Optional<MockDefinition<?>> exactMatch = findExactMatch(key, context.getInput());
        if (exactMatch.isPresent()) {
            return exactMatch;
        }
        
        // If no exact match, try partial matching for branch-prefixed steps
        return findPartialMatch(workflowId, stepId, context.getInput());
    }
    
    /**
     * Find exact match for the given key.
     */
    private Optional<MockDefinition<?>> findExactMatch(String key, Object input) {
        // First try conditional mocks
        List<ConditionalMock<?>> conditionals = conditionalMocks.get(key);
        if (conditionals != null) {
            for (ConditionalMock<?> conditional : conditionals) {
                if (conditional.matches(input)) {
                    log.debug("Found matching conditional mock for {}", key);
                    return Optional.of(conditional.getMock());
                }
            }
        }
        
        // Then try regular mocks
        List<MockDefinition<?>> regularMocks = mocks.get(key);
        if (regularMocks != null && !regularMocks.isEmpty()) {
            // Return the most recent mock (last added)
            MockDefinition<?> mock = regularMocks.get(regularMocks.size() - 1);
            log.debug("Found regular mock for {}", key);
            return Optional.of(mock);
        }
        
        return Optional.empty();
    }
    
    /**
     * Find partial match for branch-prefixed steps.
     */
    private Optional<MockDefinition<?>> findPartialMatch(String workflowId, String stepId, Object input) {
        String workflowPrefix = workflowId + ".";
        
        // Check conditional mocks with partial matching
        Optional<MockDefinition<?>> conditionalMatch = findPartialConditionalMatch(workflowPrefix, stepId, input);
        if (conditionalMatch.isPresent()) {
            return conditionalMatch;
        }
        
        // Check regular mocks with partial matching
        return findPartialRegularMatch(workflowPrefix, stepId);
    }
    
    /**
     * Find partial match in conditional mocks.
     */
    private Optional<MockDefinition<?>> findPartialConditionalMatch(String workflowPrefix, String stepId, Object input) {
        for (Map.Entry<String, List<ConditionalMock<?>>> entry : conditionalMocks.entrySet()) {
            String entryKey = entry.getKey();
            if (!entryKey.startsWith(workflowPrefix)) {
                continue;
            }
            
            String registeredStepId = entryKey.substring(workflowPrefix.length());
            if (!stepId.contains(registeredStepId)) {
                continue;
            }
            
            log.debug("Trying partial match for conditional mocks: {} contains {}", stepId, registeredStepId);
            for (ConditionalMock<?> conditional : entry.getValue()) {
                if (conditional.matches(input)) {
                    log.debug("Found matching conditional mock via partial match");
                    return Optional.of(conditional.getMock());
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Find partial match in regular mocks.
     */
    private Optional<MockDefinition<?>> findPartialRegularMatch(String workflowPrefix, String stepId) {
        for (Map.Entry<String, List<MockDefinition<?>>> entry : mocks.entrySet()) {
            String entryKey = entry.getKey();
            if (!entryKey.startsWith(workflowPrefix)) {
                continue;
            }
            
            String registeredStepId = entryKey.substring(workflowPrefix.length());
            if (!stepId.contains(registeredStepId)) {
                continue;
            }
            
            log.debug("Found partial match: {} contains {}", stepId, registeredStepId);
            List<MockDefinition<?>> mockList = entry.getValue();
            if (mockList != null && !mockList.isEmpty()) {
                MockDefinition<?> mock = mockList.get(mockList.size() - 1);
                log.debug("Found regular mock via partial match");
                return Optional.of(mock);
            }
        }
        
        log.debug("No mock found for workflow {} step {}", workflowPrefix, stepId);
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
        return new StringBuilder(workflowId.length() + stepId.length() + 1)
            .append(workflowId)
            .append('.')
            .append(stepId)
            .toString();
    }
    
    /**
     * Container for conditional mocks with type safety.
     */
    private static class ConditionalMock<I> {
        private final Class<I> inputType;
        private final Predicate<I> condition;
        private final MockDefinition<?> mock;
        
        ConditionalMock(Class<I> inputType, Predicate<I> condition, MockDefinition<?> mock) {
            this.inputType = inputType;
            this.condition = condition;
            this.mock = mock;
        }
        
        /**
         * Safely checks if the input matches the condition.
         * 
         * @param input the input object to test
         * @return true if input matches condition, false otherwise
         */
        boolean matches(Object input) {
            if (!inputType.isInstance(input)) {
                log.debug("Conditional mock type mismatch: expected {}, got {}", 
                    inputType.getSimpleName(), 
                    input != null ? input.getClass().getSimpleName() : "null");
                return false;
            }
            
            try {
                I typedInput = inputType.cast(input);
                boolean result = condition.test(typedInput);
                log.debug("Conditional mock evaluated: input={}, result={}", input, result);
                return result;
            } catch (Exception e) {
                log.warn("Conditional mock predicate threw exception", e);
                return false;
            }
        }
        
        MockDefinition<?> getMock() {
            return mock;
        }
    }
}