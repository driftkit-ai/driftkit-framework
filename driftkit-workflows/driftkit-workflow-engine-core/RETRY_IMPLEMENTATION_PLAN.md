# Retry Logic Implementation Plan for DriftKit Workflow Engine Core

## Overview

This document analyzes the retry capabilities missing in the new workflow engine compared to the old framework and provides a phased implementation plan.

## Analysis of Old Framework Retry Features

### 1. RetryPolicy Annotation
```java
public @interface RetryPolicy {
    int delay() default 5;
    int maximumAttempts() default 10;
}
```

### 2. Invocation Limit Control
- `invocationLimit`: Maximum times a step can be invoked
- `OnInvocationsLimit`: Enum controlling behavior when limit reached (ERROR, STOP, CONTINUE)

### 3. Step-Level Configuration
```java
@Step(
    retryPolicy = @RetryPolicy(delay = 10, maximumAttempts = 5),
    invocationLimit = 3,
    onInvocationsLimit = OnInvocationsLimit.ERROR
)
```

### 4. Implementation Details
- Retry logic in `ExecutableWorkflowGraph.invokeWithRetry()`
- Exponential backoff support via delay parameter
- Exception handling with cause extraction
- Logging of retry attempts

## Missing Features in New Framework

1. **No Retry Annotations**: The new `@Step` annotation lacks retry configuration
2. **No Retry Execution Logic**: WorkflowEngine doesn't implement retry mechanism
3. **No Invocation Tracking**: No tracking of step execution counts
4. **No Delay/Backoff Strategy**: No configurable delay between retries
5. **No Circuit Breaker Pattern**: No protection against repeated failures
6. **No Retry Context**: No way to access retry attempt number in step logic

## Implementation Plan

### Phase 1: Core Retry Infrastructure (Foundation) ✅ COMPLETED

**Goal**: Establish retry annotation and basic domain model

**Tasks Completed**:
1. ✅ Created `RetryPolicy` annotation with comprehensive configuration options
2. ✅ Created `OnInvocationsLimit` enum with ERROR, STOP, CONTINUE options
3. ✅ Updated `@Step` annotation to include retry configuration
4. ✅ Created `RetryContext` class using Lombok for runtime retry information
5. ✅ Created `RetryStrategy` interface and `DefaultRetryStrategy` implementation

**Files created/modified**:
- `/annotations/RetryPolicy.java` (created)
- `/annotations/OnInvocationsLimit.java` (created)
- `/annotations/Step.java` (modified - added retryPolicy, invocationLimit, onInvocationsLimit)
- `/domain/RetryContext.java` (created with Lombok)
- `/core/RetryStrategy.java` (created)
- `/core/DefaultRetryStrategy.java` (created)

**Tests Created**:
- `RetryPolicyTest.java` - Tests annotation defaults and custom values
- `RetryContextTest.java` - Tests context builder and state calculations
- `DefaultRetryStrategyTest.java` - Tests retry logic and delay calculations

**Test Results**: All 20 tests passing ✅

### Phase 2: Step Metadata Enhancement ✅ COMPLETED

**Goal**: Extend StepDefinition to support retry configuration

**Tasks Completed**:
1. ✅ Added retry fields to `StepDefinition` (retryPolicy, invocationLimit, onInvocationsLimit)
2. ✅ Updated `StepDefinition` with fluent methods for retry configuration
3. ✅ Modified `WorkflowAnalyzer` to extract retry metadata from annotations
4. ✅ Integrated retry tracking into `WorkflowContext` instead of separate tracker

**Files created/modified**:
- `/builder/StepDefinition.java` (modified - added retry fields and fluent methods)
- `/builder/RetryPolicyBuilder.java` (created - programmatic RetryPolicy creation)
- `/analyzer/StepInfo.java` (modified - added retry fields)
- `/core/WorkflowAnalyzer.java` (modified - extracts retry from @Step)
- `/core/WorkflowContext.java` (modified - integrated step execution tracking and retry context)
- ~~`/core/StepExecutionTracker.java`~~ (not needed - integrated into WorkflowContext)

**Key Design Decision**: 
- Integrated retry tracking directly into WorkflowContext for automatic cleanup
- Avoids separate tracker that would need manual lifecycle management

**Tests Created**:
- `StepDefinitionRetryTest.java` - Tests StepDefinition retry configuration
- `RetryPolicyBuilderTest.java` - Tests programmatic RetryPolicy creation
- `WorkflowContextRetryTest.java` - Tests execution tracking and retry context

**Test Results**: All 18 tests passing ✅

### Phase 3: Retry Execution Logic ✅ COMPLETED

**Goal**: Implement retry mechanism in WorkflowEngine

**Tasks Completed**:
1. ✅ Created `RetryExecutor` class with retry logic
2. ✅ Integrated exponential backoff with jitter in `DefaultRetryStrategy`
3. ✅ Modified `WorkflowExecutor` to use `RetryExecutor`
4. ✅ Integrated retry tracking with WorkflowContext
5. ✅ Handled different StepResult types during retry
6. ✅ Fixed integration tests annotation conflicts

**Key Design Decisions**:
- Modified `StepNode` record to include retry configuration directly
- All steps have retry configuration (can be null for no retry)
- Unified approach without separate retry/non-retry branches
- Invocation limit checking integrated into retry logic
- `@InitialStep` and `@Step` annotations cannot be combined

**Files created/modified**:
- `/core/RetryExecutor.java` (created - handles retry execution)
- `/core/WorkflowExecutor.java` (modified - delegates to RetryExecutor)
- `/graph/StepNode.java` (modified - added retry fields to record)
- Various test files updated for new StepNode constructor

**Tests Created**:
- `RetryExecutorTest.java` - Comprehensive tests for retry logic (10 tests)
- `RetryIntegrationTest.java` - Integration tests for retry functionality (3 tests)
  - Tests annotation-based retry configuration
  - Tests fluent API retry configuration
  - Tests invocation limit enforcement

**Test Results**: All retry tests passing ✅ (48 core tests + 3 integration tests)

**Additional Fixes Completed**:
- Fixed async tests by ensuring Branch results are properly stored in WorkflowContext
- Fixed fluent API branch routing to correctly handle internal routing markers
- Created InternalRoutingMarker interface to properly identify routing objects
- All 180 tests now passing ✅

### Phase 4: Advanced Retry Features

**Goal**: Implement sophisticated retry patterns

**Tasks**:
1. Implement circuit breaker pattern
2. Add jitter to exponential backoff
3. Create retry listeners/hooks
4. Add retry metrics collection
5. Implement conditional retry (retry only on specific exceptions)

**Files to create**:
- `/core/CircuitBreaker.java`
- `/core/RetryListener.java`
- `/core/RetryMetrics.java`
- `/core/ConditionalRetryStrategy.java`

**Tests**: Test circuit breaker, metrics, and conditional retry

### Phase 5: Persistence and Recovery

**Goal**: Make retry state persistent across restarts

**Tasks**:
1. Extend `WorkflowState` to include retry information
2. Update repositories to persist retry state
3. Implement recovery logic for interrupted retries
4. Add retry history tracking

**Files to modify**:
- `/domain/WorkflowState.java`
- `/persistence/WorkflowStateRepository.java`
- `/core/WorkflowEngine.java`

**Tests**: Test persistence and recovery scenarios

### Phase 6: Configuration and Monitoring

**Goal**: Add runtime configuration and monitoring

**Tasks**:
1. Create retry configuration properties
2. Add JMX/metrics endpoints for retry monitoring
3. Implement retry policy override mechanism
4. Add retry dashboard data collection
5. Create retry troubleshooting utilities

**Files to create**:
- `/config/RetryConfiguration.java`
- `/monitoring/RetryMonitor.java`
- `/util/RetryDiagnostics.java`

**Tests**: Configuration and monitoring tests

## Implementation Guidelines

### 1. Backward Compatibility
- Ensure existing workflows continue to function without retry
- Default retry behavior should be "no retry"

### 2. Performance Considerations
- Retry state should not significantly impact performance
- Use efficient data structures for tracking invocations
- Consider memory implications of retry history

### 3. Error Handling
- Clear error messages for retry configuration issues
- Proper logging at each retry attempt
- Distinguish between retryable and non-retryable errors

### 4. Testing Strategy
- Unit tests for each component
- Integration tests for end-to-end retry scenarios
- Performance tests for high-retry scenarios
- Chaos testing for failure conditions

## Example Usage (Target API)

```java
@Workflow("payment-processor")
public class PaymentWorkflow extends AnnotatedWorkflow {
    
    @Step(
        retryPolicy = @RetryPolicy(
            maxAttempts = 3,
            delay = 1000,
            backoffMultiplier = 2.0,
            maxDelay = 10000
        ),
        invocationLimit = 5,
        onInvocationsLimit = OnInvocationsLimit.ERROR
    )
    public StepResult<PaymentResult> processPayment(PaymentRequest request, WorkflowContext context) {
        // Access retry context if needed
        RetryContext retryContext = context.getRetryContext();
        if (retryContext.getAttemptNumber() > 1) {
            log.info("Retry attempt {} for payment {}", 
                retryContext.getAttemptNumber(), request.getId());
        }
        
        try {
            return StepResult.continueWith(paymentService.process(request));
        } catch (TransientException e) {
            // This will trigger retry
            throw e;
        } catch (PermanentException e) {
            // This will not retry
            return StepResult.fail(e);
        }
    }
    
    @Step(
        retryPolicy = @RetryPolicy(
            retryOn = {NetworkException.class, TimeoutException.class},
            abortOn = {ValidationException.class}
        )
    )
    public StepResult<Void> sendNotification(PaymentResult result) {
        // Conditional retry example
        notificationService.send(result);
        return StepResult.finish(null);
    }
}
```

## Success Criteria

1. All retry features from old framework are available
2. New advanced features (circuit breaker, conditional retry) work correctly
3. Performance impact is minimal (<5% overhead)
4. All tests pass after each phase
5. Documentation is complete and examples provided
6. Monitoring and debugging tools are functional

## Risk Mitigation

1. **Complexity**: Keep retry logic modular and testable
2. **Performance**: Profile and optimize critical paths
3. **Debugging**: Comprehensive logging and metrics
4. **Migration**: Provide migration guide from old framework
5. **Thread Safety**: Ensure retry state is thread-safe

## Timeline Estimation

- Phase 1: 2 days
- Phase 2: 2 days
- Phase 3: 3 days
- Phase 4: 3 days
- Phase 5: 2 days
- Phase 6: 2 days

Total: ~14 days of development

## Next Steps

1. Review and approve this plan
2. Create feature branch for retry implementation
3. Begin Phase 1 implementation
4. Run full test suite after each phase