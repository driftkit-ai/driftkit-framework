# Test Framework Interceptor Issue

## Problem

The test framework cannot properly intercept and mock step executions because:

1. `WorkflowEngine` does not expose a public API to add `ExecutionInterceptor`
2. The `workflowExecutor` field in `WorkflowEngine` is private
3. There's no configuration option in `WorkflowEngineConfig` to add interceptors

## Current State

```java
// In WorkflowEngine.java
private final WorkflowExecutor workflowExecutor;

// In WorkflowExecutor.java
public void addInterceptor(ExecutionInterceptor interceptor) {
    synchronized (interceptors) {
        interceptors.add(interceptor);
    }
}
```

The `WorkflowExecutor` has the capability to add interceptors, but it's not accessible from outside `WorkflowEngine`.

## Impact

Without the ability to add interceptors:
- Cannot mock step executions
- Cannot track execution history
- Cannot inject failures for testing retry logic
- Cannot implement conditional mocking

## Proposed Solutions

### Option 1: Add Public Method to WorkflowEngine (Recommended)

```java
public class WorkflowEngine {
    // Add this method
    public void addInterceptor(ExecutionInterceptor interceptor) {
        workflowExecutor.addInterceptor(interceptor);
    }
    
    public void removeInterceptor(ExecutionInterceptor interceptor) {
        workflowExecutor.removeInterceptor(interceptor);
    }
}
```

### Option 2: Add Interceptors to WorkflowEngineConfig

```java
@Builder
public class WorkflowEngineConfig {
    // Add this field
    @Builder.Default
    private List<ExecutionInterceptor> interceptors = new ArrayList<>();
}
```

Then in `WorkflowEngine` constructor:
```java
// Add configured interceptors
config.getInterceptors().forEach(workflowExecutor::addInterceptor);
```

### Option 3: Create TestWorkflowEngine

Create a test-specific subclass that exposes the necessary methods:

```java
public class TestWorkflowEngine extends WorkflowEngine {
    public void addInterceptor(ExecutionInterceptor interceptor) {
        // Use reflection to access private workflowExecutor
        // Not ideal but works for testing
    }
}
```

## Temporary Workaround

Until the WorkflowEngine is updated, the test framework cannot function properly. Tests will fail with:
- Empty execution history
- Mocks not being called
- Original step implementations being executed instead of mocks

## Required Changes

To make the test framework work, one of the following changes must be made to `driftkit-workflow-engine-core`:

1. Add `addInterceptor()` and `removeInterceptor()` methods to `WorkflowEngine`
2. Add interceptor support to `WorkflowEngineConfig`
3. Make `workflowExecutor` protected instead of private

Without these changes, the test framework cannot intercept and control workflow execution for testing purposes.