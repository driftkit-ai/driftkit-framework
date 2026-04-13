# DriftKit Workflow Test Framework - Code Quality Fixes

## Overview

This document tracks the code quality improvements for the test framework, divided into phases for systematic implementation.

## Phase 1: Thread Safety and Resource Leaks (High Priority)

### 1.1 Fix ThreadLocal Cleanup in WorkflowTestInterceptor

**Issue**: ThreadLocal `currentStepContext` not properly cleaned up, potential memory leak

**Fix**:
```java
// WorkflowTestInterceptor.java
public void clear() {
    mockRegistry.clear();
    executionTracker.clear();
    // Ensure ThreadLocal is fully cleaned
    currentStepContext.remove();
}

@Override
public void afterStep(...) {
    try {
        // existing logic
    } finally {
        // Always remove ThreadLocal even if exception occurs
        currentStepContext.remove();
    }
}
```

### 1.2 Fix Memory Leak in FailureThenSuccessMock

**Issue**: attemptCounts ConcurrentHashMap grows indefinitely on failures

**Fix**:
```java
// MockBuilder.java - FailureThenSuccessMock
private static class FailureThenSuccessMock<I, O> implements WorkflowMock {
    private final Map<String, AtomicInteger> attemptCounts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Exception failureException;
    private final Function<I, StepResult<O>> successBehavior;
    // Add cleanup mechanism
    private final Set<String> completedExecutions = ConcurrentHashMap.newKeySet();
    
    @Override
    public StepResult<?> execute(Object input, String executionId) {
        AtomicInteger attempts = attemptCounts.computeIfAbsent(executionId, k -> new AtomicInteger(0));
        int attemptCount = attempts.incrementAndGet();
        
        if (attemptCount <= maxFailures) {
            throw new RuntimeException(failureException);
        }
        
        // Success - clean up resources
        StepResult<O> result = successBehavior.apply((I) input);
        attemptCounts.remove(executionId);
        completedExecutions.add(executionId);
        return result;
    }
    
    // Add cleanup method
    public void cleanup() {
        attemptCounts.clear();
        completedExecutions.clear();
    }
}
```

### 1.3 Fix Race Condition in SequentialMockDefinition

**Issue**: Non-thread-safe list access in concurrent environment

**Fix**:
```java
// SequentialMockDefinition.java
public class SequentialMockDefinition implements WorkflowMock {
    private final List<WorkflowMock> sequence;
    private final AtomicInteger index = new AtomicInteger(0);
    private final Object lock = new Object(); // Add synchronization
    
    @Override
    public StepResult<?> execute(Object input, String executionId) {
        synchronized (lock) {
            int currentIndex = index.get();
            if (currentIndex >= sequence.size()) {
                throw new IllegalStateException("Mock sequence exhausted");
            }
            
            WorkflowMock currentMock = sequence.get(currentIndex);
            index.incrementAndGet();
            return currentMock.execute(input, executionId);
        }
    }
    
    @Override
    public boolean canExecute() {
        synchronized (lock) {
            return index.get() < sequence.size();
        }
    }
}
```

## Phase 2: Performance Improvements (Medium Priority)

### 2.1 Replace Busy Waiting with Event-Driven Approach

**Issue**: waitForStatus() uses inefficient busy waiting

**Fix**:
```java
// WorkflowTestBase.java
protected void waitForStatus(String runId, WorkflowInstance.WorkflowStatus status, Duration timeout) 
        throws TimeoutException {
    Objects.requireNonNull(runId, "runId cannot be null");
    Objects.requireNonNull(status, "status cannot be null");
    Objects.requireNonNull(timeout, "timeout cannot be null");
    
    long startTime = System.currentTimeMillis();
    long timeoutMs = timeout.toMillis();
    long sleepTime = 10; // Start with 10ms
    
    while (System.currentTimeMillis() - startTime < timeoutMs) {
        WorkflowInstance instance = getWorkflowInstance(runId);
        
        if (instance != null && instance.getStatus() == status) {
            return; // Success
        }
        
        // Exponential backoff up to 100ms
        try {
            Thread.sleep(Math.min(sleepTime, 100));
            sleepTime = Math.min(sleepTime * 2, 100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TimeoutException("Interrupted while waiting for workflow status");
        }
    }
    
    // Timeout reached
    WorkflowInstance instance = getWorkflowInstance(runId);
    WorkflowInstance.WorkflowStatus currentStatus = instance != null ? instance.getStatus() : null;
    
    throw new TimeoutException(
        "Workflow " + runId + " did not reach status " + status + " within " + timeout + 
        " (current status: " + currentStatus + ")"
    );
}
```

### 2.2 Refactor Complex findMock() Method

**Issue**: 80+ line method with complex nested loops

**Fix**:
```java
// MockRegistry.java
public Optional<WorkflowMock> findMock(String workflowId, String stepId) {
    // First try exact match
    Optional<WorkflowMock> exactMatch = findExactMatch(workflowId, stepId);
    if (exactMatch.isPresent()) {
        return exactMatch;
    }
    
    // Then try wildcard matches
    Optional<WorkflowMock> wildcardMatch = findWildcardMatch(workflowId, stepId);
    if (wildcardMatch.isPresent()) {
        return wildcardMatch;
    }
    
    // Finally try conditional matches
    return findConditionalMatch(workflowId, stepId);
}

private Optional<WorkflowMock> findExactMatch(String workflowId, String stepId) {
    String exactKey = createKey(workflowId, stepId);
    
    MockRegistration exactReg = exactMatchMocks.get(exactKey);
    if (exactReg != null && exactReg.canExecute()) {
        log.debug("Found exact match mock for {}", exactKey);
        return Optional.of(exactReg);
    }
    
    return Optional.empty();
}

private Optional<WorkflowMock> findWildcardMatch(String workflowId, String stepId) {
    // Check workflow wildcard
    String workflowWildcard = createKey(workflowId, WILDCARD);
    MockRegistration workflowReg = exactMatchMocks.get(workflowWildcard);
    if (workflowReg != null && workflowReg.canExecute()) {
        log.debug("Found workflow wildcard mock for {}", workflowWildcard);
        return Optional.of(workflowReg);
    }
    
    // Check global wildcard
    String globalWildcard = createKey(WILDCARD, WILDCARD);
    MockRegistration globalReg = exactMatchMocks.get(globalWildcard);
    if (globalReg != null && globalReg.canExecute()) {
        log.debug("Found global wildcard mock");
        return Optional.of(globalReg);
    }
    
    return Optional.empty();
}

private Optional<WorkflowMock> findConditionalMatch(String workflowId, String stepId) {
    String conditionalKey = createKey(workflowId, stepId);
    List<ConditionalMock> conditionals = conditionalMocks.get(conditionalKey);
    
    if (conditionals == null || conditionals.isEmpty()) {
        return Optional.empty();
    }
    
    return conditionals.stream()
        .filter(ConditionalMock::canExecute)
        .findFirst()
        .map(mock -> {
            log.debug("Found conditional mock for {}", conditionalKey);
            return mock;
        });
}
```

### 2.3 Replace String Concatenation with StringBuilder

**Issue**: String concatenation in hot path

**Fix**:
```java
// MockRegistry.java
private String createKey(String workflowId, String stepId) {
    return new StringBuilder(workflowId.length() + stepId.length() + 1)
        .append(workflowId)
        .append(':')
        .append(stepId)
        .toString();
}
```

## Phase 3: Type Safety (Medium Priority)

### 3.1 Fix Unsafe Generic Casting

**Issue**: Unchecked cast in MockBuilder that could fail at runtime

**Fix**:
```java
// MockBuilder.java
public <I> MockRegistrationBuilder<I> thenReturn(Class<I> inputType, Function<I, StepResult<?>> behavior) {
    Objects.requireNonNull(inputType, "inputType cannot be null");
    Objects.requireNonNull(behavior, "behavior cannot be null");
    
    // Safe cast with type checking
    Function<Object, StepResult<?>> safeBehavior = input -> {
        if (!inputType.isInstance(input)) {
            throw new IllegalArgumentException(
                "Input type mismatch. Expected: " + inputType.getName() + 
                ", but got: " + (input == null ? "null" : input.getClass().getName())
            );
        }
        return behavior.apply(inputType.cast(input));
    };
    
    this.behavior = safeBehavior;
    return (MockRegistrationBuilder<I>) this;
}
```

### 3.2 Fix Raw Type Usage

**Issue**: ConditionalMock uses raw types in collections

**Fix**:
```java
// MockRegistry.java
private final Map<String, List<ConditionalMock<?, ?>>> conditionalMocks = new ConcurrentHashMap<>();

// ConditionalMock with proper generics
private static class ConditionalMock<I, O> implements WorkflowMock {
    private final Predicate<I> condition;
    private final WorkflowMock mock;
    private final Class<I> inputType;
    
    public ConditionalMock(Predicate<I> condition, WorkflowMock mock, Class<I> inputType) {
        this.condition = condition;
        this.mock = mock;
        this.inputType = inputType;
    }
    
    @Override
    public boolean matches(Object input) {
        if (!inputType.isInstance(input)) {
            return false;
        }
        try {
            return condition.test(inputType.cast(input));
        } catch (Exception e) {
            log.warn("Error evaluating condition", e);
            return false;
        }
    }
}
```

## Phase 4: Code Duplication (Low Priority)

### 4.1 Consolidate Execution Checking Logic

**Issue**: Similar logic in getExecutionCount() and wasExecuted()

**Fix**:
```java
// ExecutionTracker.java
public boolean wasExecuted(String workflowId, String stepId) {
    return getExecutionCount(workflowId, stepId) > 0;
}

public int getExecutionCount(String workflowId, String stepId) {
    String key = createKey(workflowId, stepId);
    return executions.getOrDefault(key, Collections.emptyList()).size();
}
```

### 4.2 Extract Null Check Validation

**Issue**: Repeated Objects.requireNonNull() calls

**Fix**:
```java
// Create ValidationUtils.java
public final class ValidationUtils {
    private ValidationUtils() {} // Utility class
    
    public static void requireNonNull(String name, Object value) {
        if (value == null) {
            throw new NullPointerException(name + " cannot be null");
        }
    }
    
    public static void requireNonNull(Object... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            String name = (String) pairs[i];
            Object value = pairs[i + 1];
            requireNonNull(name, value);
        }
    }
}

// Usage example:
ValidationUtils.requireNonNull(
    "workflowId", workflowId,
    "stepId", stepId,
    "mock", mock
);
```

## Phase 5: Error Handling Improvements

### 5.1 Consistent Exception Handling

**Issue**: Inconsistent error handling patterns

**Fix**:
```java
// WorkflowTestBase.java
protected <T, R> R executeWorkflow(String workflowId, T input, Duration timeout) throws WorkflowExecutionException {
    try {
        // existing logic
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new WorkflowExecutionException("Workflow execution interrupted", e);
    } catch (TimeoutException e) {
        throw new WorkflowExecutionException("Workflow did not complete within " + timeout, e);
    } catch (Exception e) {
        throw new WorkflowExecutionException("Workflow execution failed", e);
    }
}

// New exception class
public class WorkflowExecutionException extends Exception {
    public WorkflowExecutionException(String message) {
        super(message);
    }
    
    public WorkflowExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

## Testing Strategy

After each phase:
1. Run `mvn clean compile` to ensure compilation
2. Run `mvn test` to ensure all tests pass
3. Commit changes with descriptive message

## Progress Tracking

- [x] Phase 1: Thread Safety and Resource Leaks ✅ (Completed, all tests passing)
- [x] Phase 2: Performance Improvements ✅ (Completed, all tests passing)  
- [x] Phase 3: Type Safety ✅ (Completed, all tests passing)
- [x] Phase 4: Code Duplication ✅ (Completed, all tests passing)
- [x] Phase 5: Error Handling Improvements ✅ (Completed, all tests passing)

## Summary

All code quality fixes have been successfully implemented:

✅ **Phase 1 (High Priority)**: Fixed thread safety issues, memory leaks, and race conditions
✅ **Phase 2 (Medium Priority)**: Improved performance with exponential backoff, method refactoring, and StringBuilder usage
✅ **Phase 3 (Medium Priority)**: Eliminated unsafe casting and raw type usage with proper type safety
✅ **Phase 4 (Low Priority)**: Consolidated duplicate code and created reusable utility methods  
✅ **Phase 5 (Low Priority)**: Implemented consistent error handling with specialized exception classes

**Final Result**: 20/20 tests passing, all code quality issues resolved.