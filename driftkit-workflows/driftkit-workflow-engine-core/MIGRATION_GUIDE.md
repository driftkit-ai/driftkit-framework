# Workflow Engine Core Migration Guide

This guide helps you migrate from the old workflow engine architecture to the new simplified architecture introduced in version 0.6.0.

## Overview of Changes

### 1. State Management Simplification

#### Old Architecture
- `WorkflowInstance` contained embedded state: `suspensionData` and `asyncStepStates`
- Circular dependencies between `WorkflowContext` and `WorkflowInstance`
- Complex state reconstruction logic

#### New Architecture
- `WorkflowInstance` contains only minimal state (status, currentStepId, timestamps)
- State data moved to dedicated repositories:
  - `SuspensionDataRepository` - manages suspension state
  - `AsyncStepStateRepository` - manages async operation state
- No circular dependencies

### 2. Timestamp Changes

All timestamp fields changed from `Instant` to `long` (milliseconds since epoch):
- `WorkflowInstance`: `createdAt`, `updatedAt`, `completedAt`
- `AsyncStepState`: `startTime`, `completionTime`
- WebSocket DTOs: all timestamp fields

## Migration Steps

### Step 1: Update Dependencies

Ensure you're using the latest version of workflow-engine-core:
```xml
<dependency>
    <groupId>ai.driftkit</groupId>
    <artifactId>driftkit-workflow-engine-core</artifactId>
    <version>0.8.0</version>
</dependency>
```

### Step 2: Update WorkflowInstance Usage

#### Old Code
```java
// Accessing suspension data
WorkflowInstance instance = engine.getWorkflowInstance(instanceId);
SuspensionData data = instance.getSuspensionData();
Class<?> nextInputClass = data.nextInputClass();

// Checking async states
Map<String, AsyncStepState> asyncStates = instance.getAsyncStepStates();
```

#### New Code
```java
// Accessing suspension data through repository
@Autowired
private SuspensionDataRepository suspensionDataRepository;

WorkflowInstance instance = engine.getWorkflowInstance(instanceId);
Optional<SuspensionData> data = suspensionDataRepository.findByInstanceId(instanceId);
Class<?> nextInputClass = data.map(SuspensionData::nextInputClass).orElse(null);

// Accessing async states through repository
@Autowired
private AsyncStepStateRepository asyncStepStateRepository;

Optional<AsyncStepState> asyncState = asyncStepStateRepository.findByMessageId(messageId);
```

### Step 3: Update Spring Configuration

If using Spring Boot starter, repositories are automatically configured. For manual configuration:

```java
@Configuration
public class WorkflowConfig {
    
    @Bean
    public SuspensionDataRepository suspensionDataRepository() {
        return new InMemorySuspensionDataRepository();
    }
    
    @Bean
    public AsyncStepStateRepository asyncStepStateRepository() {
        return new InMemoryAsyncStepStateRepository();
    }
    
    @Bean
    public WorkflowEngine workflowEngine(/* other deps */) {
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            // ... other config ...
            .suspensionDataRepository(suspensionDataRepository())
            .asyncStepStateRepository(asyncStepStateRepository())
            .build();
        return new WorkflowEngine(config);
    }
}
```

### Step 4: Update Schema Annotations

The schema annotation has been consolidated. If you were using `AIFunctionSchema.SchemaName`:

#### Old Code
```java
import ai.driftkit.workflow.engine.schema.AIFunctionSchema.SchemaName;

@SchemaName("userInput")
public class UserInput {
    // fields
}
```

#### New Code
```java
import ai.driftkit.workflow.engine.schema.SchemaName;

@SchemaName("userInput")
public class UserInput {
    // fields
}
```

### Step 5: Update Timestamp Handling

#### Old Code
```java
Instant createdAt = instance.getCreatedAt();
long millis = createdAt.toEpochMilli();
```

#### New Code
```java
long createdAt = instance.getCreatedAt(); // Already in milliseconds
```

### Step 6: Update Tests

Tests that directly accessed `suspensionData` or `asyncStepStates` need to be updated:

#### Old Test Code
```java
WorkflowInstance instance = engine.getWorkflowInstance(runId);
assertNotNull(instance.getSuspensionData());
assertEquals(UserInput.class, instance.getSuspensionData().nextInputClass());
```

#### New Test Code
```java
@Autowired
private SuspensionDataRepository suspensionDataRepository;

WorkflowInstance instance = engine.getWorkflowInstance(runId);
Optional<SuspensionData> suspensionData = suspensionDataRepository.findByInstanceId(instance.getInstanceId());
assertTrue(suspensionData.isPresent());
assertEquals(UserInput.class, suspensionData.get().nextInputClass());
```

## API Compatibility

### Breaking Changes
1. `WorkflowInstance.getSuspensionData()` - removed
2. `WorkflowInstance.getAsyncStepStates()` - removed
3. `WorkflowInstance.suspend(SuspensionData)` - changed to `suspend()` (no parameters)
4. All timestamp getters return `long` instead of `Instant`

### New APIs
1. `SuspensionDataRepository` - for accessing suspension data
2. `AsyncStepStateRepository` - for accessing async state
3. `SchemaProvider.getSchemaClass(String schemaName)` - for schema lookup by name

## Benefits of Migration

1. **Simplified State Management**: No more embedded state in WorkflowInstance
2. **Better Performance**: Reduced instance saves, smaller serialization footprint
3. **Improved Testability**: State can be mocked/stubbed independently
4. **Schema Caching**: Faster schema resolution with built-in registry
5. **No Circular Dependencies**: Cleaner architecture, easier to understand

## Troubleshooting

### Issue: Schema not found during resume
**Cause**: Schema not registered in SchemaRegistry
**Solution**: Ensure schema classes are annotated with `@SchemaName` and schema is generated during workflow execution

### Issue: Async states not found
**Cause**: Looking for async states in WorkflowInstance
**Solution**: Use `AsyncStepStateRepository.findByMessageId()` or `findByTaskId()`

### Issue: Timestamp conversion errors
**Cause**: Expecting `Instant` objects
**Solution**: Update code to work with `long` timestamps (milliseconds since epoch)

## Support

For questions or issues during migration:
1. Check the test examples in the codebase
2. Review the refactoring plan document: `WORKFLOW_ENGINE_REFACTORING_PLAN.md`
3. Create an issue in the project repository