# DriftKit Workflow Test Framework

A comprehensive testing framework for DriftKit workflows that provides powerful mocking capabilities, execution tracking, and assertion utilities.

## Table of Contents

- [Features](#features)
- [Quick Start](#quick-start)
- [Core Concepts](#core-concepts)
- [API Reference](#api-reference)
- [Examples](#examples)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)
- [Additional Resources](#additional-resources)

## Features

✅ **Powerful Mocking System**
- Mock any workflow step with custom behavior
- Conditional mocking based on input data
- Retry-aware mocking with failure simulation
- Async step mocking support

✅ **Execution Tracking**
- Track all workflow and step executions
- Verify execution paths and counts
- Access execution history and context

✅ **Fluent Assertions**
- AssertJ-style workflow assertions
- Step-specific verification
- Execution order validation

✅ **Framework Integration**
- Mockito integration for external dependencies
- Spring Boot test support
- JUnit 5 compatible

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>ai.driftkit</groupId>
    <artifactId>driftkit-workflow-test-framework</artifactId>
    <version>0.8.7</version>
    <scope>test</scope>
</dependency>
```

### 2. Create Your First Test

```java
public class MyWorkflowTest extends WorkflowTestBase {
    
    @Test
    void testSimpleWorkflow() throws Exception {
        // Define workflow
        WorkflowBuilder<String, String> builder = WorkflowBuilder
            .define("greeting-workflow", String.class, String.class)
            .then("greet", (name, ctx) -> StepResult.finish("Hello, " + name));
        
        engine.register(builder);
        
        // Execute and assert
        String result = executeWorkflow("greeting-workflow", "World");
        assertEquals("Hello, World", result);
    }
}
```

### 3. Mock Workflow Steps

```java
@Test
void testWithMocking() throws Exception {
    // Setup mock
    orchestrator.mock()
        .workflow("my-workflow")
        .step("external-service")
        .always()
        .thenReturn(String.class, input -> 
            StepResult.continueWith("Mocked response for: " + input));
    
    // Execute workflow
    String result = executeWorkflow("my-workflow", "test input");
    
    // Verify mock was called
    assertions.assertStep("my-workflow", "external-service")
        .wasExecuted()
        .withInput("test input");
}
```

## Architecture Overview

### Class and Object Relationships

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              DriftKit Workflow Test Framework                         │
└─────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────┐         ┌─────────────────────┐         ┌─────────────────────┐
│  WorkflowTestBase   │────────▶│WorkflowTestOrchest. │────────▶│  WorkflowEngine     │
│                     │         │                     │         │  (from workflow     │
│ - engine            │         │ - engine            │         │   framework)        │
│ - orchestrator      │         │ - interceptor       │         │                     │
│ - assertions        │         │ - mockRegistry      │         │ - register()        │
│                     │         │                     │         │ - execute()         │
│ + executeWorkflow() │         │ + mock()            │         │ - addInterceptor()  │
│ + executeAsync()    │         │ + getEngine()       │         │                     │
└─────────────────────┘         │ + getInterceptor()  │         └─────────────────────┘
           │                    └─────────────────────┘                    │
           │                               │                               │
           │                               ▼                               ▼
           │                    ┌─────────────────────┐         ┌─────────────────────┐
           │                    │WorkflowTestIntercep.│◀────────│ WorkflowInterceptor │
           │                    │                     │         │  (interface from    │
           │                    │ - mockRegistry      │         │   workflow)         │
           │                    │ - executionTracker  │         │                     │
           │                    │                     │         │ + beforeStep()      │
           │                    │ + beforeStep()      │         │ + afterStep()       │
           │                    │ + afterStep()       │         │                     │
           │                    └─────────────────────┘         └─────────────────────┘
           │                               │
           │                               ▼
           │                    ┌─────────────────────┐
           │                    │   MockRegistry      │
           │                    │                     │
           │                    │ - mocks: Map        │
           │                    │                     │
           │                    │ + register()        │
           │                    │ + findMock()        │
           │                    │ + clear()           │
           │                    └─────────────────────┘
           │                               │
           │                               ▼
           │                    ┌─────────────────────┐         ┌─────────────────────┐
           │                    │    MockBuilder      │────────▶│  WorkflowMock       │
           │                    │                     │ creates │                     │
           │                    │ - workflowId       │         │ - condition         │
           │                    │ - stepId           │         │ - behavior          │
           │                    │                     │         │ - executionLimit    │
           │                    │ + always()          │         │                     │
           │                    │ + when()            │         │ + matches()         │
           │                    │ + times()           │         │ + execute()         │
           │                    │ + thenReturn()      │         │ + canExecute()      │
           │                    │ + thenFail()        │         │                     │
           │                    │ + thenSucceed()     │         └─────────────────────┘
           │                    └─────────────────────┘
           │
           ▼
┌─────────────────────┐         ┌─────────────────────┐         ┌─────────────────────┐
│WorkflowStepAssert.  │────────▶│  ExecutionTracker   │◀────────│ StepExecution       │
│                     │   uses  │                     │ tracks  │                     │
│ - tracker           │         │ - executions: Map   │         │ - stepId            │
│ - workflowId        │         │                     │         │ - input             │
│ - stepId            │         │ + recordExecution() │         │ - output            │
│                     │         │ + getExecutions()   │         │ - timestamp         │
│ + wasExecuted()     │         │ + getExecutionCount()│         │ - error             │
│ + wasNotExecuted()  │         │ + clear()           │         │                     │
│ + withInput()       │         │                     │         └─────────────────────┘
│ + producedOutput()  │         └─────────────────────┘
└─────────────────────┘
           │
           ▼
┌─────────────────────┐         ┌─────────────────────────────────────────────────────┐
│EnhancedWorkflowAss. │────────▶│              Workflow Framework Objects              │
│                     │   uses  ├─────────────────────┬─────────────────────┬─────────┤
│ + assertThat()      │         │  WorkflowBuilder    │  WorkflowExecution  │StepResult│
│ + isCompleted()     │         │                     │                     │         │
│ + isSuspended()     │         │ + define()          │ + getStatus()       │+ finish()│
│ + isFailed()        │         │ + then()            │ + awaitResult()     │+ fail() │
│ + hasResult()       │         │ + branch()          │ + resume()          │+ suspend│
│ + completedWithin() │         │ + thenWithRetry()   │                     │         │
└─────────────────────┘         └─────────────────────┴─────────────────────┴─────────┘

                                        Key Relationships:
                                        ─────▶ Uses/Depends on
                                        ◀────▶ Implements interface
                                        ──────  Creates/Manages
```

### Component Interactions

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                           Test Execution Flow                                         │
└──────────────────────────────────────────────────────────────────────────────────────┘

    Your Test Class              Test Framework                    Workflow Framework
         │                            │                                    │
         │  extends                   │                                    │
         ├───────────────────────────▶│ WorkflowTestBase                  │
         │                            │                                    │
         │  executeWorkflow()         │                                    │
         ├───────────────────────────▶│                                   │
         │                            │                                    │
         │                            │  engine.execute()                 │
         │                            ├───────────────────────────────────▶│
         │                            │                                    │
         │                            │                                    │ WorkflowEngine
         │                            │◀───beforeStep()────────────────────│ (intercepts)
         │                            │                                    │
         │                            │  MockRegistry.findMock()          │
         │                            ├─────────────▶│                     │
         │                            │              │                     │
         │                            │◀─────────────┤ (mock found)       │
         │                            │              │                     │
         │                            │  mock.execute()                    │
         │                            ├─────────────▶│                     │
         │                            │              │                     │
         │                            │  ExecutionTracker.record()         │
         │                            ├─────────────▶│                     │
         │                            │              │                     │
         │                            │  return StepResult                 │
         │                            ├───────────────────────────────────▶│
         │                            │                                    │
         │                            │◀───afterStep()─────────────────────│
         │                            │                                    │
         │◀───────────────────────────┤ WorkflowExecution                 │
         │                            │                                    │
         │  assertions.assertStep()   │                                    │
         ├───────────────────────────▶│                                   │
         │                            │  ExecutionTracker.getExecutions() │
         │                            ├─────────────▶│                     │
         │                            │              │                     │
         │◀───────────────────────────┤ assertions pass/fail              │
         │                            │                                    │
```

### Key Design Principles

1. **Separation of Concerns**
   - Test framework components are cleanly separated from workflow framework
   - Interceptor pattern allows non-invasive testing
   - Mock registry is independent of workflow execution

2. **Extensibility**
   - Custom assertions can be added by extending base assertion classes
   - Mock behaviors can be composed using builder pattern
   - New interceptors can be added without modifying core framework

3. **Type Safety**
   - Generic types preserve type information throughout execution
   - Mock builders enforce type consistency between input and output
   - Assertions provide compile-time type checking

4. **Testability**
   - All components are designed to be easily testable
   - Clear interfaces between components
   - Minimal coupling between test and production code

## Core Concepts

### Test Base Class

All workflow tests should extend `WorkflowTestBase`:

```java
public class MyTest extends WorkflowTestBase {
    @Test
    void testMyWorkflow() throws Exception {
        // Your test logic here
    }
}
```

### Mock Builder API

The mock builder provides a fluent API for creating mocks:

```java
// Always mock
orchestrator.mock()
    .workflow("workflow-id")
    .step("step-id")
    .always()
    .thenReturn(InputType.class, input -> StepResult.continueWith(output));

// Conditional mock
orchestrator.mock()
    .workflow("workflow-id")
    .step("step-id")
    .when(Order.class, order -> order.getAmount() > 1000)
    .thenReturn(Order.class, order -> StepResult.continueWith(processHighValue(order)));

// Failure simulation
orchestrator.mock()
    .workflow("workflow-id")
    .step("step-id")
    .times(2).thenFail(new ServiceException("Service unavailable"))
    .afterwards().thenSucceed("Success after retry");
```

### Assertion API

The framework provides comprehensive assertion capabilities:

```java
// Basic assertions
assertions.assertStep("workflow-id", "step-id")
    .wasExecuted()
    .wasExecutedTimes(3)
    .wasNotExecuted();

// Execution order
assertions.assertExecutionOrder()
    .step("step1")
    .step("step2")
    .step("step3");

// Advanced workflow assertions
EnhancedWorkflowAssertions.assertThat(execution, tracker)
    .isCompleted()
    .hasResult(expectedResult)
    .completedWithin(Duration.ofSeconds(5));
```

## API Reference

### WorkflowTestBase

Base class for all workflow tests. Provides utilities for executing and testing workflows.

**Example Usage:**

```java
// Execute a workflow synchronously
public class SimpleWorkflowTest extends WorkflowTestBase {
    @Test
    void testSync() throws Exception {
        String result = executeWorkflow("my-workflow", "input data");
        assertEquals("expected output", result);
    }
    
    // Execute asynchronously
    @Test
    void testAsync() throws Exception {
        CompletableFuture<String> future = executeWorkflowAsync("my-workflow", "input");
        String result = future.get(5, TimeUnit.SECONDS);
        assertNotNull(result);
    }
    
    // Test workflow suspension
    @Test
    void testSuspension() throws Exception {
        WorkflowExecution<?> execution = executeAndExpectSuspend(
            "approval-workflow", 
            new ApprovalRequest("REQ-123"),
            Duration.ofSeconds(2)
        );
        assertions.assertThat(execution).isSuspended();
    }
}
```

### WorkflowTestOrchestrator

Central orchestration point for test configuration and mocking.

**Example Usage:**

```java
// Access the orchestrator from your test
@Test
void testWithOrchestrator() {
    // Start creating a mock
    orchestrator.mock()
        .workflow("payment-workflow")
        .step("charge-card")
        .always()
        .thenSucceed(new PaymentResult(true, "TXN-123"));
    
    // Access the workflow engine directly
    WorkflowEngine engine = orchestrator.getEngine();
    engine.register(myWorkflowBuilder);
    
    // Access the test interceptor for advanced scenarios
    WorkflowTestInterceptor interceptor = orchestrator.getInterceptor();
    ExecutionTracker tracker = interceptor.getExecutionTracker();
}
```

### MockBuilder

Fluent API for creating sophisticated mocks with various behaviors.

**Example Usage:**

```java
// Always execute mock
orchestrator.mock()
    .workflow("order-workflow")
    .step("validate")
    .always()
    .thenReturn(Order.class, order -> {
        if (order.isValid()) {
            return StepResult.continueWith(order);
        }
        return StepResult.fail("Invalid order");
    });

// Conditional mock based on input
orchestrator.mock()
    .workflow("pricing-workflow")
    .step("calculate-discount")
    .when(Customer.class, customer -> customer.isPremium())
    .thenSucceed(new Discount(0.2)); // 20% for premium

// Limited execution mock (useful for retry testing)
orchestrator.mock()
    .workflow("resilient-workflow")
    .step("external-api")
    .times(2).thenFail(new ServiceException("API down"))
    .afterwards().thenSucceed("API recovered");

// Chain multiple behaviors
orchestrator.mock()
    .workflow("complex-workflow")
    .step("process")
    .times(1).thenReturn(String.class, s -> StepResult.continueWith(s + "-first"))
    .times(1).thenReturn(String.class, s -> StepResult.continueWith(s + "-second"))
    .afterwards().thenFail(new RuntimeException("No more attempts"));
```

### StepAssertions

Comprehensive assertions for verifying workflow execution behavior.

**Example Usage:**

```java
// Basic execution verification
assertions.assertStep("order-workflow", "validate")
    .wasExecuted();

// Verify non-execution
assertions.assertStep("order-workflow", "rollback")
    .wasNotExecuted();

// Verify exact execution count
assertions.assertStep("payment-workflow", "charge-card")
    .wasExecutedTimes(3); // Retried 3 times

// Verify input and output
assertions.assertStep("calculation-workflow", "calculate")
    .wasExecuted()
    .withInput(new CalculationRequest(100, 0.15))
    .producedOutput(new CalculationResult(115));

// Verify execution order
assertions.assertExecutionOrder()
    .step("validate")
    .step("process") 
    .step("notify");

// Chain multiple assertions
assertions.assertStep("workflow-id", "step-id")
    .wasExecuted()
    .withInput(inputData)
    .producedOutput(expectedOutput)
    .wasExecutedTimes(1);
```

### EnhancedWorkflowAssertions

Advanced assertions for workflow state and behavior using AssertJ-style API.

**Example Usage:**

```java
// Basic workflow assertions
WorkflowExecution<?> execution = engine.execute("my-workflow", input);

EnhancedWorkflowAssertions.assertThat(execution, tracker)
    .isCompleted()
    .hasResult(expectedResult)
    .completedWithin(Duration.ofSeconds(5))
    .hasNoErrors();

// Suspended workflow assertions
EnhancedWorkflowAssertions.assertThat(suspendedExecution, tracker)
    .isSuspended()
    .hasSuspensionData(expectedPrompt)
    .isWaitingForInput(UserApproval.class);

// Failed workflow assertions
EnhancedWorkflowAssertions.assertThat(failedExecution, tracker)
    .isFailed()
    .hasError()
    .hasErrorMessage("Service unavailable")
    .hasErrorType(ServiceException.class);

// Complex assertions
EnhancedWorkflowAssertions.assertThat(execution, tracker)
    .hasExecutedSteps("validate", "process", "notify")
    .hasExecutedStepsInOrder("validate", "process", "notify")
    .hasExecutedStepsCount(3)
    .completedWithin(Duration.ofMillis(500));
```

## Examples

### Testing Retry Behavior

```java
@Test
void testRetryMechanism() throws Exception {
    // Create workflow with retry
    WorkflowBuilder<String, String> builder = WorkflowBuilder
        .define("retry-workflow", String.class, String.class)
        .thenWithRetry("flaky-service", 
            (input, ctx) -> callFlakyService(input),
            RetryPolicyBuilder.retry()
                .withMaxAttempts(3)
                .withDelay(100)
                .withRetryOnFailResult(true)
                .build()
        );
    
    engine.register(builder);
    
    // Mock to fail twice then succeed
    orchestrator.mock()
        .workflow("retry-workflow")
        .step("flaky-service")
        .times(2).thenFail(new RuntimeException("Temporary failure"))
        .afterwards().thenSucceed("Success!");
    
    // Execute and verify
    String result = executeWorkflow("retry-workflow", "input");
    assertEquals("Success!", result);
}
```

### Testing Conditional Workflows

```java
@Test
void testConditionalBranching() throws Exception {
    // Mock different responses based on input
    orchestrator.mock()
        .workflow("order-workflow")
        .step("process-payment")
        .when(Order.class, order -> order.isVip())
        .thenReturn(Order.class, order -> 
            StepResult.continueWith(new Payment(order, 0.9))); // 10% discount
    
    // Test VIP order
    Order vipOrder = new Order("VIP-001", 1000, true);
    OrderResult vipResult = executeWorkflow("order-workflow", vipOrder);
    assertEquals(900, vipResult.getFinalAmount()); // Discount applied
    
    // Test regular order
    Order regularOrder = new Order("REG-001", 1000, false);
    OrderResult regularResult = executeWorkflow("order-workflow", regularOrder);
    assertEquals(1000, regularResult.getFinalAmount()); // No discount
}
```

### Testing Async Workflows

```java
@Test
void testAsyncWorkflow() throws Exception {
    // Mock async step
    orchestrator.mockAsync()
        .workflow("async-workflow")
        .step("long-operation")
        .completeAfter(Duration.ofMillis(500))
        .withResult("Async result");
    
    // Execute asynchronously
    CompletableFuture<String> future = executeWorkflowAsync("async-workflow", "input");
    
    // Verify not completed immediately
    assertFalse(future.isDone());
    
    // Wait and verify result
    String result = future.get(1, TimeUnit.SECONDS);
    assertEquals("Async result", result);
}
```

### Testing External Service Integration

```java
public class PaymentWorkflowTest extends WorkflowTestBase {
    
    @Mock
    private PaymentService paymentService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Register workflow with mocked service
        WorkflowBuilder<PaymentRequest, PaymentResult> builder = WorkflowBuilder
            .define("payment-workflow", PaymentRequest.class, PaymentResult.class)
            .then("charge", (req, ctx) -> {
                PaymentResponse response = paymentService.charge(req);
                return StepResult.finish(new PaymentResult(response));
            });
        
        engine.register(builder);
    }
    
    @Test
    void testPaymentProcessing() throws Exception {
        // Setup Mockito mock
        when(paymentService.charge(any()))
            .thenReturn(new PaymentResponse("TXN-123", true));
        
        // Execute workflow
        PaymentResult result = executeWorkflow("payment-workflow", 
            new PaymentRequest("CARD-123", 100.00));
        
        // Verify
        assertTrue(result.isSuccessful());
        assertEquals("TXN-123", result.getTransactionId());
        verify(paymentService).charge(any());
    }
}
```

### Testing Workflow Suspend and Resume

```java
@Test
void testWorkflowSuspendResume() throws Exception {
    // Create workflow that suspends for approval
    WorkflowBuilder<OrderRequest, OrderResult> builder = WorkflowBuilder
        .define("approval-workflow", OrderRequest.class, OrderResult.class)
        .then("validate", (order, ctx) -> StepResult.continueWith(order))
        .then("request-approval", (order, ctx) -> {
            if (order.amount > 10000) {
                return StepResult.suspend(
                    new ApprovalPrompt("High value order requires approval"),
                    ApprovalResponse.class
                );
            }
            return StepResult.continueWith(new ApprovalResponse(true, "AUTO"));
        })
        .then("complete", (approval, ctx) -> {
            OrderRequest order = (OrderRequest) ctx.getTriggerData();
            return StepResult.finish(new OrderResult(order.id, approval.approved));
        });
    
    engine.register(builder);
    
    // Test high-value order suspension
    OrderRequest highValueOrder = new OrderRequest("ORD-001", 15000);
    WorkflowExecution<?> execution = executeAndExpectSuspend(
        "approval-workflow", 
        highValueOrder,
        Duration.ofSeconds(2)
    );
    
    // Verify workflow is suspended
    EnhancedWorkflowAssertions.assertThat(execution, tracker)
        .isSuspended()
        .hasSuspensionData(new ApprovalPrompt("High value order requires approval"));
    
    // Resume workflow with approval
    execution.resume(new ApprovalResponse(true, "MANAGER-123"));
    OrderResult result = (OrderResult) execution.awaitResult(Duration.ofSeconds(5));
    
    // Verify final result
    assertTrue(result.approved);
    assertEquals("ORD-001", result.orderId);
}
```

### Testing Complex Branching Logic

```java
@Test
void testComplexBranchingWorkflow() throws Exception {
    // Mock different paths based on customer type
    orchestrator.mock()
        .workflow("customer-workflow")
        .step("check-credit")
        .when(Customer.class, c -> c.type == CustomerType.PREMIUM)
        .thenSucceed(new CreditResult(50000, true));
    
    orchestrator.mock()
        .workflow("customer-workflow")
        .step("check-credit")
        .when(Customer.class, c -> c.type == CustomerType.REGULAR)
        .thenSucceed(new CreditResult(10000, true));
    
    orchestrator.mock()
        .workflow("customer-workflow")
        .step("check-credit")
        .when(Customer.class, c -> c.type == CustomerType.NEW)
        .thenSucceed(new CreditResult(0, false));
    
    // Test each customer type
    Customer premium = new Customer("C1", CustomerType.PREMIUM);
    ProcessResult premiumResult = executeWorkflow("customer-workflow", premium);
    assertEquals(50000, premiumResult.creditLimit);
    assertions.assertStep("customer-workflow", "premium-benefits").wasExecuted();
    assertions.assertStep("customer-workflow", "standard-processing").wasNotExecuted();
    
    Customer regular = new Customer("C2", CustomerType.REGULAR); 
    ProcessResult regularResult = executeWorkflow("customer-workflow", regular);
    assertEquals(10000, regularResult.creditLimit);
    assertions.assertStep("customer-workflow", "standard-processing").wasExecuted();
    assertions.assertStep("customer-workflow", "premium-benefits").wasNotExecuted();
    
    Customer newCustomer = new Customer("C3", CustomerType.NEW);
    ProcessResult newResult = executeWorkflow("customer-workflow", newCustomer);
    assertEquals(0, newResult.creditLimit);
    assertions.assertStep("customer-workflow", "onboarding").wasExecuted();
}
```

## Best Practices

### Test Organization and Structure

### 1. Test Organization

```java
public class OrderWorkflowTest extends WorkflowTestBase {
    
    private OrderWorkflow orderWorkflow;
    
    @BeforeEach
    void setUp() {
        // Initialize workflow once
        orderWorkflow = new OrderWorkflow();
        engine.register(orderWorkflow);
    }
    
    @Test
    void testHappyPath() { }
    
    @Test
    void testErrorHandling() { }
    
    @Test
    void testRetryScenarios() { }
}
```

### 2. Mock Isolation

```java
@Test
void testWithIsolatedMocks() throws Exception {
    // Each test should set up its own mocks
    orchestrator.mock()
        .workflow("my-workflow")
        .step("external-api")
        .always()
        .thenReturn(Data.class, data -> processData(data));
    
    // Test logic here
    
    // Mocks are automatically cleared after each test
}
```

### 3. Assertion Best Practices

```java
@Test
void testComplexWorkflow() throws Exception {
    // Execute workflow
    OrderResult result = executeWorkflow("order-workflow", order);
    
    // Use descriptive assertions
    assertThat(result)
        .as("Order should be processed successfully")
        .isNotNull()
        .extracting(OrderResult::getStatus)
        .isEqualTo("COMPLETED");
    
    // Verify execution path
    assertions.assertExecutionOrder()
        .as("Should follow happy path")
        .step("validate")
        .step("process-payment")
        .step("send-confirmation");
}
```

### 4. Use Descriptive Test Names

```java
@Test
void shouldRetryPaymentThreeTimesBeforeFailing() { }

@Test
void shouldRoutePremiumOrdersThroughExpressProcessing() { }

@Test
void shouldSuspendWorkflowWhenApprovalRequired() { }
```

### 5. Retry Testing

When testing retries, use the mock builder's retry-specific features:

```java
@Test
void testRetryBehavior() throws Exception {
    // Simulate failures followed by success
    orchestrator.mock()
        .workflow("payment-workflow")
        .step("charge-api")
        .times(2).thenFail(new ServiceException("Temporary outage"))
        .afterwards().thenSucceed(new ChargeResult("TXN-123", true));
    
    // Configure retry policy
    var retryPolicy = RetryPolicyBuilder.retry()
        .withMaxAttempts(3)
        .withDelay(100)
        .withBackoffMultiplier(2.0)
        .build();
    
    // Execute workflow with retry-enabled step
    ChargeResult result = executeWorkflow("payment-workflow", 
        new ChargeRequest("CARD-123", 99.99));
    
    // Verify successful completion after retries
    assertTrue(result.success());
    assertEquals("TXN-123", result.transactionId());
}

@Test
void testRetryExhaustion() throws Exception {
    // Always fail to test retry exhaustion
    orchestrator.mock()
        .workflow("unreliable-workflow")
        .step("always-fails")
        .always()
        .thenFail(new PermanentException("Service decommissioned"));
    
    // Expect workflow to fail after max attempts
    assertThrows(WorkflowException.class, () -> {
        executeWorkflow("unreliable-workflow", "input");
    });
}
```

## Troubleshooting

### Common Issues and Solutions

**Mock Not Being Called**

```java
// Problem: Mock not triggering
// Solution: Check exact IDs and branch prefixes
@Test
void debugMockIssues() {
    // Enable debug logging first
    Logger logger = LoggerFactory.getLogger("ai.driftkit.workflow.test");
    ((ch.qos.logback.classic.Logger) logger).setLevel(Level.DEBUG);
    
    // Use tracker to inspect actual step IDs
    executeWorkflow("my-workflow", input);
    
    // Print all executed steps to see exact IDs
    tracker.getAllExecutions().forEach((key, executions) -> {
        System.out.println("Step executed: " + key);
    });
    
    // Common issue: branch prefixes
    // Instead of: orchestrator.mock().step("process")
    // Use: orchestrator.mock().step("true_1_process")
}
```

**Retry Test Issues**

```java
// Problem: Retries not working with StepResult.fail()
// Solution: Enable retryOnFailResult
@Test 
void fixRetryIssues() {
    var retryPolicy = RetryPolicyBuilder.retry()
        .withMaxAttempts(3)
        .withRetryOnFailResult(true)  // Critical for StepResult.fail()
        .build();
    
    // Mock configuration for retries
    orchestrator.mock()
        .workflow("retry-workflow")
        .step("flaky-step")
        .times(2).thenReturn(Input.class, i -> StepResult.fail("Temporary issue"))
        .afterwards().thenReturn(Input.class, i -> StepResult.continueWith("Success"));
}
```

**Async Test Timing**

```java
// Problem: Async tests timing out
// Solution: Adjust timeouts and verify mock delays
@Test
void handleAsyncTiming() {
    // Configure async mock with realistic delay
    orchestrator.mockAsync()
        .workflow("async-workflow") 
        .step("slow-operation")
        .completeAfter(Duration.ofMillis(200))  // Not too long
        .withResult("Done");
    
    // Use appropriate timeout
    CompletableFuture<String> future = executeWorkflowAsync("async-workflow", input);
    String result = future.get(1, TimeUnit.SECONDS);  // Generous timeout
}
```

### Debug Utilities

```java
// Utility method to debug workflow execution
private void debugWorkflow(String workflowId) {
    System.out.println("=== Workflow Debug Info ===");
    
    // List all registered workflows
    engine.getRegisteredWorkflows().forEach(id -> 
        System.out.println("Registered workflow: " + id)
    );
    
    // List all mocked steps
    orchestrator.getInterceptor().getMockRegistry().getAllMocks()
        .forEach((key, mock) -> 
            System.out.println("Mocked step: " + key)
        );
    
    // Show execution history
    tracker.getAllExecutions().forEach((step, execs) -> {
        System.out.println("Step " + step + " executed " + execs.size() + " times");
        execs.forEach(exec -> {
            System.out.println("  Input: " + exec.getInput());
            System.out.println("  Output: " + exec.getOutput());
        });
    });
}
```

## Advanced Testing Patterns

### Mock Design Patterns

**1. Use Builders for Complex Mocks**

```java
@Test
void shouldHandleComplexPaymentScenarios() {
    // Create a mock builder helper
    MockBuilder paymentMock = orchestrator.mock()
        .workflow("payment-workflow")
        .step("charge-card");
    
    // Configure different behaviors for different inputs
    paymentMock.when(PaymentRequest.class, req -> req.amount > 10000)
        .thenReturn(PaymentRequest.class, req -> 
            StepResult.suspend(new FraudReviewPrompt(req), FraudDecision.class));
    
    paymentMock.when(PaymentRequest.class, req -> req.cardType == CardType.CORPORATE)
        .thenReturn(PaymentRequest.class, req -> 
            StepResult.continueWith(new PaymentResult(req.amount * 0.98))); // 2% discount
    
    paymentMock.when(PaymentRequest.class, req -> req.amount < 0)
        .thenFail(new ValidationException("Invalid amount"));
}
```

**2. Create Reusable Mock Factories**

```java
public class MockFactory {
    
    public static void setupFlakeyServiceMock(WorkflowTestOrchestrator orchestrator, 
                                              String workflowId, 
                                              String stepId,
                                              int failureCount) {
        orchestrator.mock()
            .workflow(workflowId)
            .step(stepId)
            .times(failureCount).thenFail(new ServiceUnavailableException("Service down"))
            .afterwards().thenSucceed("Service recovered");
    }
}
```

### Assertion Strategies

**1. Layer Your Assertions**

```java
@Test
void shouldProcessOrderCompleteFlow() throws Exception {
    // Given
    Order order = new Order("ORD-123", 150.00, CustomerType.PREMIUM);
    
    // When
    OrderResult result = executeWorkflow("order-workflow", order);
    
    // Then - Layer 1: Basic result assertions
    assertNotNull(result);
    assertTrue(result.isSuccessful());
    assertEquals("ORD-123", result.orderId);
    
    // Layer 2: Execution flow assertions
    assertions.assertExecutionOrder()
        .step("validate-order")
        .step("check-inventory")
        .step("calculate-pricing")
        .step("process-payment")
        .step("ship-order")
        .step("send-notification");
    
    // Layer 3: Step-specific assertions
    assertions.assertStep("order-workflow", "calculate-pricing")
        .producedOutput(new PricingResult(150.00, 0.10, 135.00)); // 10% discount
    
    // Layer 4: Performance assertions
    EnhancedWorkflowAssertions.assertThat(execution, tracker)
        .completedWithin(Duration.ofSeconds(2));
}
```

**2. Create Custom Assertions**

```java
public class OrderAssertions {
    
    public static void assertOrderProcessedCorrectly(
            WorkflowStepAssertions assertions, 
            Order order, 
            OrderResult result) {
        
        // Verify order basics
        assertEquals(order.orderId, result.orderId);
        
        // Verify workflow execution based on order type
        if (order.amount > 1000) {
            assertions.assertStep("order-workflow", "fraud-check").wasExecuted();
        } else {
            assertions.assertStep("order-workflow", "fraud-check").wasNotExecuted();
        }
        
        // Verify customer-specific routing
        if (order.customerType == CustomerType.PREMIUM) {
            assertions.assertStep("order-workflow", "premium-benefits").wasExecuted();
        }
    }
}
```

### Performance Testing

```java
@Test
void shouldCompleteWithinSLA() throws Exception {
    // Arrange
    int concurrentRequests = 10;
    Duration maxExecutionTime = Duration.ofSeconds(5);
    
    // Act
    List<CompletableFuture<OrderResult>> futures = IntStream.range(0, concurrentRequests)
        .mapToObj(i -> executeWorkflowAsync("order-workflow", 
            new Order("ORD-" + i, 100.00 * i)))
        .collect(Collectors.toList());
    
    // Assert
    long startTime = System.currentTimeMillis();
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .get(maxExecutionTime.toMillis(), TimeUnit.MILLISECONDS);
    long executionTime = System.currentTimeMillis() - startTime;
    
    assertTrue(executionTime < maxExecutionTime.toMillis(),
        "Workflow execution took " + executionTime + "ms, exceeding SLA of " + maxExecutionTime);
    
    // Verify all completed successfully
    futures.forEach(future -> {
        assertTrue(future.isDone());
        assertDoesNotThrow(() -> future.get());
    });
}
```

### Error Recovery Testing

```java
@Test
void shouldRecoverFromTransientErrors() throws Exception {
    // Setup recovery scenario
    AtomicInteger attemptCount = new AtomicInteger(0);
    
    orchestrator.mock()
        .workflow("resilient-workflow")
        .step("unstable-service")
        .always()
        .thenReturn(Request.class, req -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt <= 2) {
                // Fail first two attempts
                throw new TransientException("Service temporarily unavailable");
            }
            return StepResult.continueWith("Service call succeeded on attempt " + attempt);
        });
    
    // Execute with retry policy
    String result = executeWorkflow("resilient-workflow", new Request());
    
    // Verify recovery
    assertEquals(3, attemptCount.get());
    assertThat(result).contains("attempt 3");
}
```

## Common Pitfalls to Avoid

### 1. Avoid Over-Mocking

**❌ BAD: Mocking everything**
```java
@Test
void testOverMocked() {
    // Mocking internal logic - makes test brittle
    orchestrator.mock().workflow("wf").step("internal-calc").always().thenSucceed(42);
    orchestrator.mock().workflow("wf").step("format").always().thenSucceed("42");
    orchestrator.mock().workflow("wf").step("validate").always().thenSucceed(true);
    
    // Test doesn't actually test the workflow logic!
}
```

**✅ GOOD: Mock only external dependencies**
```java
@Test
void testProperMocking() {
    // Only mock external service calls
    orchestrator.mock()
        .workflow("calculation-workflow")
        .step("fetch-exchange-rate")
        .always()
        .thenSucceed(1.2); // Mock external API
    
    // Let internal calculation logic run normally
    CalculationResult result = executeWorkflow("calculation-workflow", 
        new CalculationInput(100, "USD", "EUR"));
    
    // Verify actual calculation logic
    assertEquals(120.0, result.convertedAmount, 0.01);
}
```

### 2. Handle Timing Issues Properly

**❌ BAD: Using fixed sleeps**
```java
@Test
void testBadTiming() throws Exception {
    CompletableFuture<Result> future = executeWorkflowAsync("async-workflow", input);
    Thread.sleep(1000); // Brittle! May fail on slow systems
    assertTrue(future.isDone());
}
```

**✅ GOOD: Use proper waiting mechanisms**
```java
@Test
void testProperTiming() throws Exception {
    CompletableFuture<Result> future = executeWorkflowAsync("async-workflow", input);
    
    // Wait with timeout
    Result result = future.get(5, TimeUnit.SECONDS);
    assertNotNull(result);
    
    // Or use assertions that wait
    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
            assertTrue(future.isDone());
            assertEquals(expectedResult, future.get());
        });
}
```

### 3. Clean Up Resources

```java
public class ResourceIntensiveTest extends WorkflowTestBase {
    
    private ExecutorService executorService;
    
    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(10);
    }
    
    @AfterEach
    void tearDown() {
        // Always clean up resources
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}
```

## Additional Resources

### Advanced Examples

For more complex testing scenarios, see [ADVANCED_EXAMPLES.md](ADVANCED_EXAMPLES.md) which covers:

- **Stateful Workflow Testing** - Managing complex state across workflow steps
- **Multi-Stage Pipeline Testing** - Testing ETL and data processing workflows  
- **Event-Driven Workflow Testing** - Workflows that emit and react to events
- **Saga Pattern Testing** - Distributed transactions with compensation
- **Dynamic Workflow Testing** - Workflows that generate steps at runtime
- **Integration Testing** - Testing with real external systems using TestContainers

### API Documentation

For detailed API documentation and method signatures, refer to the Javadocs or explore the source code:

- [WorkflowTestBase](src/main/java/ai/driftkit/workflow/test/core/WorkflowTestBase.java) - Base test class
- [MockBuilder](src/main/java/ai/driftkit/workflow/test/mocks/MockBuilder.java) - Mock configuration API
- [WorkflowStepAssertions](src/main/java/ai/driftkit/workflow/test/assertions/WorkflowStepAssertions.java) - Assertion utilities
- [EnhancedWorkflowAssertions](src/main/java/ai/driftkit/workflow/test/assertions/EnhancedWorkflowAssertions.java) - Advanced assertions

### Getting Help

- **Issues**: Report bugs or request features on our [GitHub Issues](https://github.com/driftkit/workflow-test-framework/issues)
- **Discussions**: Join our community discussions for questions and best practices
- **Documentation**: Full documentation available at [docs.driftkit.ai](https://docs.driftkit.ai)

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.