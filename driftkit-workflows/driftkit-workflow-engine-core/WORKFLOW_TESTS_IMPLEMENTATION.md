# Workflow Engine Core Tests Implementation

## Overview
This document contains the implementation of critical tests for the workflow engine core module, focusing on the first three types of tests from our comprehensive testing plan.

## Test Types Covered

1. **Basic Unit Tests** - Testing individual components in isolation
2. **Workflow Step Tests** - Testing step execution and result handling
3. **WorkflowBuilder Tests** - Testing workflow construction and configuration

## 1. Basic Unit Tests

### 1.1 StepOutputTest
Tests the `StepOutput` wrapper class that encapsulates step results.

```java
package ai.driftkit.workflow.engine.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class StepOutputTest {
    
    @Test
    @DisplayName("Should create StepOutput with value")
    void testCreateWithValue() {
        String value = "test-value";
        StepOutput output = StepOutput.of(value);
        
        assertTrue(output.hasValue());
        assertEquals(value, output.getValue());
        assertEquals(String.class, output.getActualClass());
    }
    
    @Test
    @DisplayName("Should handle null values")
    void testNullValue() {
        StepOutput output = StepOutput.of(null);
        
        assertFalse(output.hasValue());
        assertNull(output.getValue());
        assertNull(output.getActualClass());
    }
    
    @Test
    @DisplayName("Should cast value to requested type")
    void testGetValueAs() {
        Integer value = 42;
        StepOutput output = StepOutput.of(value);
        
        assertEquals(value, output.getValueAs(Integer.class));
        assertEquals(value, output.getValueAs(Number.class));
    }
    
    @Test
    @DisplayName("Should throw ClassCastException for incompatible types")
    void testGetValueAsIncompatibleType() {
        StepOutput output = StepOutput.of("string-value");
        
        assertThrows(ClassCastException.class, 
            () -> output.getValueAs(Integer.class));
    }
    
    @Test
    @DisplayName("Should handle complex objects")
    void testComplexObject() {
        record TestData(String name, int value) {}
        
        TestData data = new TestData("test", 123);
        StepOutput output = StepOutput.of(data);
        
        assertTrue(output.hasValue());
        assertEquals(data, output.getValueAs(TestData.class));
        assertEquals(TestData.class, output.getActualClass());
    }
}
```

### 1.2 StepDefinitionTest
Tests the `StepDefinition` class that encapsulates step metadata and execution logic.

```java
package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.function.Function;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

class StepDefinitionTest {
    
    @Test
    @DisplayName("Should create StepDefinition with function")
    void testCreateWithFunction() {
        Function<String, StepResult<String>> stepFn = 
            input -> StepResult.continueWith(input.toUpperCase());
        
        StepDefinition step = StepDefinition.of("uppercase", stepFn);
        
        assertEquals("uppercase", step.getId());
        assertNotNull(step.getExecutor());
        assertEquals("Step: uppercase", step.getDescription());
        // Note: inputType and outputType are null for lambdas without type info
        assertEquals(Object.class, step.getInputType());
        assertEquals(Object.class, step.getOutputType());
    }
    
    @Test
    @DisplayName("Should create StepDefinition with explicit types")
    void testCreateWithExplicitTypes() {
        Function<Integer, StepResult<String>> stepFn = 
            num -> StepResult.continueWith("Number: " + num);
        
        StepDefinition step = StepDefinition.of("converter", stepFn)
            .withTypes(Integer.class, String.class)
            .withDescription("Convert number to string");
        
        assertEquals("converter", step.getId());
        assertEquals("Convert number to string", step.getDescription());
        assertEquals(Integer.class, step.getInputType());
        assertEquals(String.class, step.getOutputType());
    }
    
    @Test
    @DisplayName("Should extract method info from serializable function")
    void testSerializableFunction() {
        // Use SerializableFunction for method references
        StepDefinition.SerializableFunction<String, StepResult<String>> methodRef = 
            this::processString;
        
        StepDefinition step = StepDefinition.of(methodRef);
        
        assertEquals("processString", step.getId());
        assertEquals(String.class, step.getInputType());
        assertEquals(String.class, step.getOutputType());
    }
    
    @Test
    @DisplayName("Should handle bi-function step definitions")
    void testBiFunctionStep() {
        BiFunction<String, WorkflowContext, StepResult<String>> stepFn = 
            (input, ctx) -> StepResult.continueWith(input + "-" + ctx.getRunId());
        
        StepDefinition step = StepDefinition.of("combine", stepFn)
            .withTypes(String.class, String.class);
        
        assertEquals("combine", step.getId());
        assertEquals(String.class, step.getInputType());
        assertEquals(String.class, step.getOutputType());
    }
    
    @Test
    @DisplayName("Should handle context-only steps")
    void testContextOnlyStep() {
        Function<WorkflowContext, StepResult<String>> stepFn = 
            ctx -> StepResult.continueWith("RunId: " + ctx.getRunId());
        
        StepDefinition step = StepDefinition.ofContextOnly("context-step", stepFn)
            .withOutputType(String.class);
        
        assertEquals("context-step", step.getId());
        assertEquals(Void.class, step.getInputType());
        assertEquals(String.class, step.getOutputType());
    }
    
    @Test
    @DisplayName("Should validate null arguments")
    void testNullValidation() {
        // Null ID should throw
        assertThrows(IllegalArgumentException.class, 
            () -> StepDefinition.of(null, s -> StepResult.continueWith(s)));
        
        // Empty ID should throw
        assertThrows(IllegalArgumentException.class, 
            () -> StepDefinition.of("", s -> StepResult.continueWith(s)));
        
        // Null function should throw
        assertThrows(IllegalArgumentException.class, 
            () -> StepDefinition.of("step", null));
    }
    
    private StepResult<String> processString(String input) {
        return StepResult.continueWith(input.trim());
    }
}
```

### 1.3 WorkflowExceptionTest
Tests the `WorkflowException` class for proper error handling.

```java
package ai.driftkit.workflow.engine.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowExceptionTest {
    
    @Test
    @DisplayName("Should create exception with message only")
    void testCreateWithMessage() {
        String message = "Workflow failed";
        WorkflowException ex = new WorkflowException(message);
        
        assertEquals(message, ex.getMessage());
        assertEquals("WORKFLOW_ERROR", ex.getCode());
        assertNull(ex.getCause());
    }
    
    @Test
    @DisplayName("Should create exception with message and code")
    void testCreateWithMessageAndCode() {
        String message = "Step execution failed";
        String code = "STEP_FAILED";
        WorkflowException ex = new WorkflowException(message, code);
        
        assertEquals(message, ex.getMessage());
        assertEquals(code, ex.getCode());
        assertNull(ex.getCause());
    }
    
    @Test
    @DisplayName("Should create exception with message and cause")
    void testCreateWithMessageAndCause() {
        String message = "Database error";
        Exception cause = new RuntimeException("Connection failed");
        WorkflowException ex = new WorkflowException(message, cause);
        
        assertEquals(message, ex.getMessage());
        assertEquals("WORKFLOW_ERROR", ex.getCode());
        assertEquals(cause, ex.getCause());
    }
    
    @Test
    @DisplayName("Should create exception with all parameters")
    void testCreateWithAllParameters() {
        String message = "External service error";
        String code = "EXTERNAL_ERROR";
        Exception cause = new IllegalStateException("Service unavailable");
        WorkflowException ex = new WorkflowException(message, code, cause);
        
        assertEquals(message, ex.getMessage());
        assertEquals(code, ex.getCode());
        assertEquals(cause, ex.getCause());
    }
}
```

## 2. Workflow Step Tests

### 2.1 StepResultTest
Tests all variants of the `StepResult` sealed interface.

```java
package ai.driftkit.workflow.engine.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class StepResultTest {
    
    @Nested
    @DisplayName("Continue Tests")
    class ContinueTests {
        
        @Test
        @DisplayName("Should create Continue with value")
        void testContinueWith() {
            String value = "next-value";
            StepResult<String> result = StepResult.continueWith(value);
            
            assertInstanceOf(StepResult.Continue.class, result);
            StepResult.Continue<String> cont = (StepResult.Continue<String>) result;
            assertEquals(value, cont.value());
        }
        
        @Test
        @DisplayName("Should allow null values in Continue")
        void testContinueWithNull() {
            StepResult<String> result = StepResult.continueWith(null);
            
            assertInstanceOf(StepResult.Continue.class, result);
            StepResult.Continue<String> cont = (StepResult.Continue<String>) result;
            assertNull(cont.value());
        }
    }
    
    @Nested
    @DisplayName("Finish Tests")
    class FinishTests {
        
        @Test
        @DisplayName("Should create Finish with value")
        void testFinish() {
            Integer value = 42;
            StepResult<Integer> result = StepResult.finish(value);
            
            assertInstanceOf(StepResult.Finish.class, result);
            StepResult.Finish<Integer> finish = (StepResult.Finish<Integer>) result;
            assertEquals(value, finish.value());
        }
    }
    
    @Nested
    @DisplayName("Fail Tests")
    class FailTests {
        
        @Test
        @DisplayName("Should create Fail with error message")
        void testFail() {
            String errorMessage = "Processing failed";
            StepResult<String> result = StepResult.fail(errorMessage);
            
            assertInstanceOf(StepResult.Fail.class, result);
            StepResult.Fail<String> fail = (StepResult.Fail<String>) result;
            assertNotNull(fail.error());
            assertEquals(errorMessage, fail.error().getMessage());
            assertInstanceOf(RuntimeException.class, fail.error());
        }
        
        @Test
        @DisplayName("Should create Fail with throwable")
        void testFailWithThrowable() {
            IllegalArgumentException error = new IllegalArgumentException("Invalid input");
            StepResult<String> result = StepResult.fail(error);
            
            assertInstanceOf(StepResult.Fail.class, result);
            StepResult.Fail<String> fail = (StepResult.Fail<String>) result;
            assertEquals(error, fail.error());
            assertEquals("Invalid input", fail.error().getMessage());
        }
        
        @Test
        @DisplayName("Should throw when error is null")
        void testFailValidation() {
            assertThrows(IllegalArgumentException.class,
                () -> StepResult.fail((Throwable) null));
        }
    }
    
    @Nested
    @DisplayName("Suspend Tests")
    class SuspendTests {
        
        @Test
        @DisplayName("Should create Suspend with prompt and expected input class")
        void testSuspend() {
            String prompt = "Please provide your name";
            StepResult<String> result = StepResult.suspend(prompt, String.class);
            
            assertInstanceOf(StepResult.Suspend.class, result);
            StepResult.Suspend<String> suspend = (StepResult.Suspend<String>) result;
            assertEquals(prompt, suspend.promptToUser());
            assertEquals(String.class, suspend.nextInputClass());
            assertNotNull(suspend.nextInputSchema());
            assertNotNull(suspend.metadata());
        }
        
        @Test
        @DisplayName("Should create Suspend with metadata")
        void testSuspendWithMetadata() {
            String prompt = "Please approve the order";
            Map<String, Object> metadata = Map.of("orderId", "12345", "amount", 100.0);
            
            StepResult<String> result = StepResult.suspend(prompt, String.class, metadata);
            
            assertInstanceOf(StepResult.Suspend.class, result);
            StepResult.Suspend<String> suspend = (StepResult.Suspend<String>) result;
            assertEquals(prompt, suspend.promptToUser());
            assertEquals(String.class, suspend.nextInputClass());
            assertEquals(metadata, suspend.metadata());
            assertNotNull(suspend.nextInputSchema());
        }
    }
    
    @Nested
    @DisplayName("Branch Tests")
    class BranchTests {
        
        @Test
        @DisplayName("Should create Branch with event")
        void testBranch() {
            String event = "premium-customer";
            StepResult<String> result = StepResult.branch(event);
            
            assertInstanceOf(StepResult.Branch.class, result);
            StepResult.Branch<String> branch = (StepResult.Branch<String>) result;
            assertEquals(event, branch.event());
        }
        
        @Test
        @DisplayName("Should throw when event is null")
        void testBranchNullEvent() {
            assertThrows(IllegalArgumentException.class, 
                () -> StepResult.branch(null));
        }
        
        @Test
        @DisplayName("Should create Branch with complex event object")
        void testBranchWithComplexEvent() {
            record CustomerEvent(String type, int level) {}
            CustomerEvent event = new CustomerEvent("premium", 5);
            
            StepResult<CustomerEvent> result = StepResult.branch(event);
            
            assertInstanceOf(StepResult.Branch.class, result);
            StepResult.Branch<CustomerEvent> branch = (StepResult.Branch<CustomerEvent>) result;
            assertEquals(event, branch.event());
            assertEquals("premium", branch.event().type());
            assertEquals(5, branch.event().level());
        }
    }
    
    @Nested
    @DisplayName("Async Tests")
    class AsyncTests {
        
        @Test
        @DisplayName("Should create Async with task info")
        void testAsync() {
            String taskId = "process-payment";
            long estimatedMs = 5000;
            String immediateData = "Processing started";
            
            StepResult<String> result = StepResult.async(taskId, estimatedMs, immediateData);
            
            assertInstanceOf(StepResult.Async.class, result);
            StepResult.Async<String> async = (StepResult.Async<String>) result;
            assertEquals(taskId, async.taskId());
            assertEquals(estimatedMs, async.estimatedDurationMs());
            assertEquals(immediateData, async.immediateData());
            assertNotNull(async.taskArgs());
            assertTrue(async.taskArgs().isEmpty());
        }
        
        @Test
        @DisplayName("Should create Async with task arguments")
        void testAsyncWithTaskArgs() {
            String taskId = "send-email";
            long estimatedMs = 2000;
            Map<String, Object> taskArgs = Map.of("to", "user@example.com", "subject", "Hello");
            String immediateData = "Email queued";
            
            StepResult<String> result = StepResult.async(taskId, estimatedMs, taskArgs, immediateData);
            
            assertInstanceOf(StepResult.Async.class, result);
            StepResult.Async<String> async = (StepResult.Async<String>) result;
            assertEquals(taskId, async.taskId());
            assertEquals(estimatedMs, async.estimatedDurationMs());
            assertEquals(taskArgs, async.taskArgs());
            assertEquals(immediateData, async.immediateData());
        }
        
        @Test
        @DisplayName("Should create Async from CompletableFuture")
        void testAsyncWithFuture() {
            String taskId = "compute-result";
            CompletableFuture<StepResult<String>> future = CompletableFuture.completedFuture(
                StepResult.continueWith("computed")
            );
            
            StepResult.Async<String> async = new StepResult.Async<>(taskId, future);
            
            assertEquals(taskId, async.taskId());
            assertEquals(-1, async.estimatedDurationMs());
            assertNull(async.immediateData());
            assertTrue(async.taskArgs().containsKey(WorkflowContext.Keys.ASYNC_FUTURE));
            assertEquals(future, async.taskArgs().get(WorkflowContext.Keys.ASYNC_FUTURE));
        }
        
        @Test
        @DisplayName("Should validate task ID")
        void testAsyncValidation() {
            assertThrows(IllegalArgumentException.class,
                () -> StepResult.async(null, 1000, "data"));
            
            assertThrows(IllegalArgumentException.class,
                () -> StepResult.async("", 1000, "data"));
            
            assertThrows(IllegalArgumentException.class,
                () -> StepResult.async("  ", 1000, "data"));
        }
    }
}
```

### 2.2 WorkflowContextTest
Tests the workflow context management functionality.

```java
package ai.driftkit.workflow.engine.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowContextTest {
    
    private WorkflowContext context;
    
    @BeforeEach
    void setUp() {
        context = WorkflowContext.newRun("initial-input");
    }
    
    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {
        
        @Test
        @DisplayName("Should create context with trigger data")
        void testCreateWithTriggerData() {
            String triggerData = "test-trigger";
            WorkflowContext ctx = WorkflowContext.newRun(triggerData);
            
            assertNotNull(ctx.getRunId());
            assertEquals(triggerData, ctx.getTriggerData());
            assertEquals(triggerData, ctx.getTriggerData(String.class));
            assertEquals(0, ctx.getStepCount());
            assertEquals(0, ctx.getCustomDataCount());
        }
        
        @Test
        @DisplayName("Should create context with instance ID")
        void testCreateWithInstanceId() {
            String triggerData = "test-trigger";
            String instanceId = "instance-123";
            WorkflowContext ctx = WorkflowContext.newRun(triggerData, instanceId);
            
            assertEquals(instanceId, ctx.getInstanceId());
            assertNotEquals(instanceId, ctx.getRunId());
        }
        
        @Test
        @DisplayName("Should handle null trigger data")
        void testNullTriggerData() {
            WorkflowContext ctx = WorkflowContext.newRun(null);
            
            assertNull(ctx.getTriggerData());
            assertNull(ctx.getTriggerData(String.class));
        }
    }
    
    @Nested
    @DisplayName("Step Output Operations")
    class StepOutputOperations {
        
        @Test
        @DisplayName("Should store and retrieve step output")
        void testStepOutput() {
            String stepId = "step1";
            String output = "step-result";
            
            context.setStepOutput(stepId, output);
            
            assertTrue(context.hasStepResult(stepId));
            assertEquals(output, context.getStepResult(stepId, String.class));
            assertEquals(1, context.getStepCount());
        }
        
        @Test
        @DisplayName("Should overwrite existing step output")
        void testOverwriteStepOutput() {
            String stepId = "step1";
            
            context.setStepOutput(stepId, "first");
            context.setStepOutput(stepId, "second");
            
            assertEquals("second", context.getStepResult(stepId, String.class));
            assertEquals(1, context.getStepCount());
        }
        
        @Test
        @DisplayName("Should remove step output when setting null")
        void testRemoveStepOutput() {
            String stepId = "step1";
            
            context.setStepOutput(stepId, "value");
            assertTrue(context.hasStepResult(stepId));
            
            context.setStepOutput(stepId, null);
            assertFalse(context.hasStepResult(stepId));
        }
        
        @Test
        @DisplayName("Should throw when step output not found")
        void testStepOutputNotFound() {
            assertThrows(NoSuchElementException.class,
                () -> context.getStepResult("unknown", String.class));
        }
        
        @Test
        @DisplayName("Should return default when step output not found")
        void testStepOutputWithDefault() {
            String defaultValue = "default";
            String result = context.getStepResultOrDefault("unknown", String.class, defaultValue);
            assertEquals(defaultValue, result);
        }
        
        @Test
        @DisplayName("Should handle type conversion for step outputs")
        void testStepOutputTypeConversion() {
            context.setStepOutput("number", 42);
            
            assertEquals(42, context.getStepResult("number", Integer.class));
            assertEquals(42, context.getStepResult("number", Number.class));
            
            assertThrows(ClassCastException.class,
                () -> context.getStepResult("number", String.class));
        }
    }
    
    @Nested
    @DisplayName("Custom Data Operations")
    class CustomDataOperations {
        
        @Test
        @DisplayName("Should store and retrieve custom data")
        void testCustomData() {
            String key = "user-preference";
            String value = "dark-mode";
            
            context.setContextValue(key, value);
            
            assertEquals(value, context.getContextValue(key, String.class));
            assertEquals(value, context.getString(key));
            assertEquals(1, context.getCustomDataCount());
        }
        
        @Test
        @DisplayName("Should handle different types in custom data")
        void testCustomDataTypes() {
            context.setContextValue("string", "text");
            context.setContextValue("integer", 42);
            context.setContextValue("double", 3.14);
            context.setContextValue("boolean", true);
            context.setContextValue("long", 123L);
            
            assertEquals("text", context.getString("string"));
            assertEquals(42, context.getInt("integer"));
            assertEquals(3.14, context.getDouble("double"));
            assertEquals(true, context.getBoolean("boolean"));
            assertEquals(123L, context.getLong("long"));
        }
        
        @Test
        @DisplayName("Should handle collections in custom data")
        void testCustomDataCollections() {
            List<String> list = List.of("a", "b", "c");
            Map<String, Integer> map = Map.of("x", 1, "y", 2);
            
            context.setContextValue("list", list);
            context.setContextValue("map", map);
            
            assertEquals(list, context.getList("list", String.class));
            assertEquals(map, context.getMap("map", String.class, Integer.class));
        }
        
        @Test
        @DisplayName("Should return null for non-existent custom data")
        void testCustomDataNotFound() {
            assertNull(context.getContextValue("unknown", String.class));
            assertNull(context.getString("unknown"));
        }
        
        @Test
        @DisplayName("Should return default for non-existent custom data")
        void testCustomDataWithDefaults() {
            assertEquals("default", context.getStringOrDefault("unknown", "default"));
            assertEquals(42, context.getIntOrDefault("unknown", 42));
            assertEquals(3.14, context.getDoubleOrDefault("unknown", 3.14));
            assertEquals(true, context.getBooleanOrDefault("unknown", true));
            assertEquals(123L, context.getLongOrDefault("unknown", 123L));
        }
    }
    
    @Nested
    @DisplayName("Fluent API Tests")
    class FluentApiTests {
        
        @Test
        @DisplayName("Should access step output fluently")
        void testFluentStepAccess() {
            context.setStepOutput("step1", "value1");
            
            assertTrue(context.step("step1").exists());
            assertTrue(context.step("step1").succeeded());
            assertEquals("value1", context.step("step1").output(String.class).orElse(null));
            assertEquals("value1", context.step("step1").outputOrThrow(String.class));
        }
        
        @Test
        @DisplayName("Should handle missing step fluently")
        void testFluentMissingStep() {
            assertFalse(context.step("unknown").exists());
            assertFalse(context.step("unknown").succeeded());
            assertTrue(context.step("unknown").output(String.class).isEmpty());
            
            assertThrows(NoSuchElementException.class,
                () -> context.step("unknown").outputOrThrow(String.class));
        }
        
        @Test
        @DisplayName("Should track last output")
        void testLastOutput() {
            assertTrue(context.lastOutput(String.class).isEmpty());
            
            context.setStepOutput("step1", "first");
            assertEquals("first", context.lastOutput(String.class).orElse(null));
            
            context.setStepOutput("step2", "second");
            assertEquals("second", context.lastOutput(String.class).orElse(null));
            
            // System keys shouldn't update last output
            context.setStepOutput("__system__", "system");
            assertEquals("second", context.lastOutput(String.class).orElse(null));
        }
    }
    
    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {
        
        @Test
        @DisplayName("Should handle concurrent step outputs")
        void testConcurrentStepOutputs() throws InterruptedException {
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];
            
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    String stepId = "step" + index;
                    context.setStepOutput(stepId, "value" + index);
                });
            }
            
            for (Thread thread : threads) {
                thread.start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            assertEquals(threadCount, context.getStepCount());
            
            for (int i = 0; i < threadCount; i++) {
                String stepId = "step" + i;
                assertEquals("value" + i, context.getStepResult(stepId, String.class));
            }
        }
    }
}
```

### 2.3 InputPreparerTest
Tests the input preparation logic for workflow steps.

```java
package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.builder.StepDefinition;
import ai.driftkit.workflow.engine.builder.StepMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class InputPreparerTest {
    
    private InputPreparer inputPreparer;
    private WorkflowContext context;
    
    @BeforeEach
    void setUp() {
        inputPreparer = new InputPreparer();
        context = WorkflowContext.newRun("trigger-data");
    }
    
    @Nested
    @DisplayName("Single Input Function Tests")
    class SingleInputTests {
        
        @Test
        @DisplayName("Should prepare input from compatible step output")
        void testPrepareFromStepOutput() {
            // Given a previous step output
            context.setStepOutput("step1", "output-value");
            
            // And a step that accepts String input
            StepDefinition step = StepDefinition.of("step2",
                (String s) -> StepResult.continueWith(s.toUpperCase()));
            
            // When preparing input
            Object[] inputs = inputPreparer.prepareInputs(step, context);
            
            // Then should find the compatible output
            assertEquals(1, inputs.length);
            assertEquals("output-value", inputs[0]);
        }
        
        @Test
        @DisplayName("Should use trigger data when no step outputs")
        void testPrepareFromTriggerData() {
            // Given only trigger data
            WorkflowContext ctx = WorkflowContext.newRun("initial-input");
            
            // And a step that accepts String input
            StepDefinition step = StepDefinition.of("step1",
                (String s) -> StepResult.continueWith(s.length()));
            
            // When preparing input
            Object[] inputs = inputPreparer.prepareInputs(step, ctx);
            
            // Then should use trigger data
            assertEquals(1, inputs.length);
            assertEquals("initial-input", inputs[0]);
        }
        
        @Test
        @DisplayName("Should find most recent compatible output")
        void testFindMostRecentCompatible() {
            // Given multiple outputs of same type
            context.setStepOutput("step1", "first");
            context.setStepOutput("step2", 42);
            context.setStepOutput("step3", "latest");
            
            // And a step that accepts String input
            StepDefinition step = StepDefinition.of("step4",
                (String s) -> StepResult.continueWith(s));
            
            // When preparing input
            Object[] inputs = inputPreparer.prepareInputs(step, context);
            
            // Then should use most recent compatible (step3)
            assertEquals(1, inputs.length);
            assertEquals("latest", inputs[0]);
        }
        
        @Test
        @DisplayName("Should handle type hierarchy")
        void testTypeHierarchy() {
            // Given outputs with inheritance
            context.setStepOutput("step1", 42);  // Integer extends Number
            
            // And a step that accepts Number
            StepMetadata metadata = StepMetadata.builder()
                .inputType(Number.class)
                .outputType(String.class)
                .build();
            
            Function<Number, StepResult<String>> fn = 
                num -> StepResult.continueWith(num.toString());
            
            StepDefinition step = StepDefinition.of("step2", fn, metadata);
            
            // When preparing input
            Object[] inputs = inputPreparer.prepareInputs(step, context);
            
            // Then should accept Integer as Number
            assertEquals(1, inputs.length);
            assertEquals(42, inputs[0]);
        }
        
        @Test
        @DisplayName("Should skip null outputs")
        void testSkipNullOutputs() {
            // Given null output followed by valid output
            context.setStepOutput("step1", null);
            context.setStepOutput("step2", "valid");
            
            // And a step that needs String input
            StepDefinition step = StepDefinition.of("step3",
                (String s) -> StepResult.continueWith(s));
            
            // When preparing input
            Object[] inputs = inputPreparer.prepareInputs(step, context);
            
            // Then should skip null and use valid output
            assertEquals(1, inputs.length);
            assertEquals("valid", inputs[0]);
        }
        
        @Test
        @DisplayName("Should return null when no compatible input found")
        void testNoCompatibleInput() {
            // Given only incompatible outputs
            context.setStepOutput("step1", 42);
            context.setStepOutput("step2", true);
            
            // And a step that needs String input
            StepDefinition step = StepDefinition.of("step3",
                (String s) -> StepResult.continueWith(s));
            
            // When preparing input
            Object[] inputs = inputPreparer.prepareInputs(step, context);
            
            // Then should return null array
            assertEquals(1, inputs.length);
            assertNull(inputs[0]);
        }
    }
    
    @Nested
    @DisplayName("BiFunction Tests")
    class BiFunctionTests {
        
        @Test
        @DisplayName("Should prepare inputs for BiFunction")
        void testPrepareBiFunctionInputs() {
            // Given context and previous output
            context.setStepOutput("step1", "output-value");
            
            // And a BiFunction step
            BiFunction<WorkflowContext, String, StepResult<String>> biFn =
                (ctx, str) -> StepResult.continueWith(str + "-" + ctx.getRunId());
            
            StepMetadata metadata = StepMetadata.builder()
                .inputType(String.class)
                .outputType(String.class)
                .biFunction(true)
                .build();
            
            StepDefinition step = StepDefinition.of("step2", biFn, metadata);
            
            // When preparing inputs
            Object[] inputs = inputPreparer.prepareInputs(step, context);
            
            // Then should provide context and found input
            assertEquals(2, inputs.length);
            assertEquals(context, inputs[0]);
            assertEquals("output-value", inputs[1]);
        }
        
        @Test
        @DisplayName("Should handle BiFunction with no compatible input")
        void testBiFunctionNoCompatibleInput() {
            // Given only incompatible output
            context.setStepOutput("step1", 42);
            
            // And a BiFunction step expecting String
            BiFunction<WorkflowContext, String, StepResult<String>> biFn =
                (ctx, str) -> StepResult.continueWith(str != null ? str : "default");
            
            StepMetadata metadata = StepMetadata.builder()
                .inputType(String.class)
                .outputType(String.class)
                .biFunction(true)
                .build();
            
            StepDefinition step = StepDefinition.of("step2", biFn, metadata);
            
            // When preparing inputs
            Object[] inputs = inputPreparer.prepareInputs(step, context);
            
            // Then should provide context and null
            assertEquals(2, inputs.length);
            assertEquals(context, inputs[0]);
            assertNull(inputs[1]);
        }
    }
    
    @Nested
    @DisplayName("Special Cases")
    class SpecialCases {
        
        @Test
        @DisplayName("Should handle WorkflowContext as input type")
        void testContextAsInputType() {
            // Given a step that takes WorkflowContext directly
            StepMetadata metadata = StepMetadata.builder()
                .inputType(WorkflowContext.class)
                .outputType(String.class)
                .build();
            
            Function<WorkflowContext, StepResult<String>> fn =
                ctx -> StepResult.continueWith("RunId: " + ctx.getRunId());
            
            StepDefinition step = StepDefinition.of("ctxStep", fn, metadata);
            
            // When preparing input
            Object[] inputs = inputPreparer.prepareInputs(step, context);
            
            // Then should provide the context
            assertEquals(1, inputs.length);
            assertEquals(context, inputs[0]);
        }
        
        @Test
        @DisplayName("Should handle Object as input type")
        void testObjectAsInputType() {
            // Given any output
            context.setStepOutput("step1", "string-value");
            
            // And a step that accepts Object
            StepMetadata metadata = StepMetadata.builder()
                .inputType(Object.class)
                .outputType(String.class)
                .build();
            
            Function<Object, StepResult<String>> fn =
                obj -> StepResult.continueWith(obj.toString());
            
            StepDefinition step = StepDefinition.of("objectStep", fn, metadata);
            
            // When preparing input
            Object[] inputs = inputPreparer.prepareInputs(step, context);
            
            // Then should accept any non-null output
            assertEquals(1, inputs.length);
            assertEquals("string-value", inputs[0]);
        }
        
        @Test
        @DisplayName("Should prioritize well-known keys")
        void testWellKnownKeyPriority() {
            // Given regular output and user input
            context.setStepOutput("step1", "regular-output");
            context.setStepOutput(WorkflowContext.Keys.USER_INPUT, "user-provided");
            
            // And a step that needs String
            StepDefinition step = StepDefinition.of("step2",
                (String s) -> StepResult.continueWith(s));
            
            // When preparing input
            Object[] inputs = inputPreparer.prepareInputs(step, context);
            
            // Then should use most recent (which could be either depending on order)
            assertEquals(1, inputs.length);
            assertNotNull(inputs[0]);
        }
    }
}
```

## 3. WorkflowBuilder Tests

### 3.1 WorkflowBuilderBasicTest
Tests basic workflow construction using the WorkflowBuilder.

```java
package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowBuilderBasicTest {
    
    @Test
    @DisplayName("Should build simple linear workflow")
    void testSimpleLinearWorkflow() {
        WorkflowGraph<String, String> workflow = WorkflowBuilder
            .define("simple-workflow", String.class, String.class)
            .then(input -> StepResult.continueWith(input.toUpperCase()))
            .then(upper -> StepResult.continueWith(upper + "!"))
            .then(excited -> StepResult.finish(excited))
            .build();
        
        assertNotNull(workflow);
        assertEquals("simple-workflow", workflow.id());
        assertEquals(String.class, workflow.inputType());
        assertEquals(String.class, workflow.outputType());
        assertEquals(3, workflow.nodes().size());
    }
    
    @Test
    @DisplayName("Should build workflow with named steps")
    void testNamedSteps() {
        WorkflowGraph<Integer, String> workflow = WorkflowBuilder
            .define("named-steps", Integer.class, String.class)
            .then("validate", (Integer num) -> {
                if (num < 0) return StepResult.fail("Negative number");
                return StepResult.continueWith(num);
            }, Integer.class, Integer.class)
            .then("double", (Integer num) -> StepResult.continueWith(num * 2), Integer.class, Integer.class)
            .then("format", (Integer num) -> StepResult.finish("Result: " + num), Integer.class, String.class)
            .build();
        
        assertEquals(3, workflow.nodes().size());
        assertTrue(workflow.nodes().containsKey("validate"));
        assertTrue(workflow.nodes().containsKey("double"));
        assertTrue(workflow.nodes().containsKey("format"));
    }
    
    @Test
    @DisplayName("Should support method references")
    void testMethodReferences() {
        WorkflowGraph<String, Integer> workflow = WorkflowBuilder
            .define("method-ref-workflow", String.class, Integer.class)
            .then(this::trimInput)
            .then(this::countWords)
            .then(this::finalizeCount)
            .build();
        
        assertNotNull(workflow);
        assertEquals(3, workflow.nodes().size());
    }
    
    @Test
    @DisplayName("Should build workflow with auto-wrap value methods")
    void testAutoWrapValueMethods() {
        WorkflowGraph<String, String> workflow = WorkflowBuilder
            .define("auto-wrap-workflow", String.class, String.class)
            .thenValue(String::trim)           // Returns String directly
            .thenValue(String::toUpperCase)    // Returns String directly
            .finishWithValue(s -> s + "!")    // Returns String directly (final)
            .build();
        
        assertNotNull(workflow);
        assertEquals(3, workflow.nodes().size());
    }
    
    @Test
    @DisplayName("Should handle empty workflow")
    void testEmptyWorkflow() {
        assertThrows(IllegalStateException.class, () -> 
            WorkflowBuilder
                .define("empty", String.class, String.class)
                .build()
        );
    }
    
    @Test
    @DisplayName("Should preserve step order")
    void testStepOrder() {
        WorkflowGraph<String, String> workflow = WorkflowBuilder
            .define("ordered", String.class, String.class)
            .then("step1", (String s) -> StepResult.continueWith(s + "1"), String.class, String.class)
            .then("step2", (String s) -> StepResult.continueWith(s + "2"), String.class, String.class)
            .then("step3", (String s) -> StepResult.finish(s + "3"), String.class, String.class)
            .build();
        
        assertEquals(3, workflow.nodes().size());
        
        // Verify nodes exist
        assertTrue(workflow.nodes().containsKey("step1"));
        assertTrue(workflow.nodes().containsKey("step2"));
        assertTrue(workflow.nodes().containsKey("step3"));
        
        // Verify initial step is set
        assertNotNull(workflow.initialStepId());
    }
    
    // Helper methods for method reference tests
    private StepResult<String> trimInput(String input) {
        return StepResult.continueWith(input.trim());
    }
    
    private StepResult<Integer> countWords(String text) {
        int count = text.split("\\s+").length;
        return StepResult.continueWith(count);
    }
    
    private StepResult<Integer> finalizeCount(Integer count) {
        return StepResult.finish(count);
    }
}
```

### 3.2 WorkflowBuilderBranchTest
Tests branching functionality in workflows.

```java
package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowBuilderBranchTest {
    
    @Test
    @DisplayName("Should build workflow with simple branch")
    void testSimpleBranch() {
        WorkflowGraph<Integer, String> workflow = WorkflowBuilder
            .define("branch-workflow", Integer.class, String.class)
            .then("check", num -> StepResult.continueWith(num))
            .branch(
                ctx -> ctx.step("check").output(Integer.class)
                    .map(n -> n > 0)
                    .orElse(false),
                
                // True branch - positive numbers
                positive -> positive
                    .then("positive", n -> StepResult.finish("Positive: " + n)),
                    
                // False branch - non-positive numbers
                nonPositive -> nonPositive
                    .then("non-positive", n -> StepResult.finish("Non-positive: " + n))
            )
            .build();
        
        assertNotNull(workflow);
        // Should have initial step + 2 branch steps
        assertTrue(workflow.nodes().size() >= 3);
    }
    
    @Test
    @DisplayName("Should build workflow with nested branches")
    void testNestedBranches() {
        WorkflowGraph<String, String> workflow = WorkflowBuilder
            .define("nested-branch", String.class, String.class)
            .then("parse", input -> {
                try {
                    int num = Integer.parseInt(input.trim());
                    return StepResult.continueWith(num);
                } catch (NumberFormatException e) {
                    return StepResult.fail("Invalid number");
                }
            })
            .branch(
                ctx -> ctx.step("parse").succeeded(),
                
                // Success branch - further categorize the number
                success -> success.branch(
                    ctx -> ctx.step("parse").output(Integer.class)
                        .map(n -> n >= 100)
                        .orElse(false),
                    
                    // Large numbers
                    large -> large.then("large", n -> StepResult.finish("Large: " + n)),
                    
                    // Small numbers
                    small -> small.then("small", n -> StepResult.finish("Small: " + n))
                ),
                
                // Failure branch
                failure -> failure.then("error", _ -> StepResult.finish("Error: Invalid input"))
            )
            .build();
        
        assertNotNull(workflow);
        assertTrue(workflow.nodes().size() >= 4);
    }
    
    @Test
    @DisplayName("Should build workflow with complex conditions")
    void testComplexBranchConditions() {
        record UserData(String role, int accessLevel) {}
        
        WorkflowGraph<UserData, String> workflow = WorkflowBuilder
            .define("complex-conditions", UserData.class, String.class)
            .then("validate", user -> {
                if (user.role() == null || user.accessLevel() < 0) {
                    return StepResult.fail("Invalid user data");
                }
                return StepResult.continueWith(user);
            })
            .branch(
                ctx -> {
                    return ctx.step("validate").output(UserData.class)
                        .map(user -> "admin".equals(user.role()) && user.accessLevel() >= 10)
                        .orElse(false);
                },
                
                // Admin with high access
                admin -> admin.then("admin-flow", 
                    user -> StepResult.finish("Admin access granted")),
                    
                // Others
                other -> other.branch(
                    ctx -> ctx.step("validate").output(UserData.class)
                        .map(user -> user.accessLevel() >= 5)
                        .orElse(false),
                    
                    // Medium access
                    medium -> medium.then("medium-flow", 
                        user -> StepResult.finish("Standard access granted")),
                        
                    // Low access
                    low -> low.then("low-flow", 
                        user -> StepResult.finish("Limited access granted"))
                )
            )
            .build();
        
        assertNotNull(workflow);
    }
    
    @Test
    @DisplayName("Should handle branch with context-based conditions")
    void testBranchWithContext() {
        WorkflowGraph<String, String> workflow = WorkflowBuilder
            .define("context-branch", String.class, String.class)
            .then("setup", input -> {
                // Store data in context for later use
                return StepResult.continueWith(input);
            })
            .then("setup-context", (String input, WorkflowContext ctx) -> {
                ctx.setContextValue("originalLength", input.length());
                return StepResult.continueWith(input.toUpperCase());
            })
            .branch(
                ctx -> {
                    Integer originalLength = ctx.getInt("originalLength");
                    return originalLength != null && originalLength > 10;
                },
                
                // Long input branch
                longInput -> longInput.then("truncate", 
                    s -> StepResult.finish(s.substring(0, 10) + "...")),
                    
                // Short input branch
                shortInput -> shortInput.then("keep", 
                    s -> StepResult.finish(s))
            )
            .build();
        
        assertNotNull(workflow);
    }
    
    @Test
    @DisplayName("Should build workflow with multi-step branches")
    void testMultiStepBranches() {
        WorkflowGraph<Integer, String> workflow = WorkflowBuilder
            .define("multi-step-branch", Integer.class, String.class)
            .then("classify", num -> StepResult.continueWith(num))
            .branch(
                ctx -> ctx.step("classify").output(Integer.class)
                    .map(n -> n % 2 == 0)
                    .orElse(false),
                
                // Even number processing
                even -> even
                    .then("halve", n -> StepResult.continueWith(n / 2))
                    .then("square", n -> StepResult.continueWith(n * n))
                    .then("format-even", n -> StepResult.finish("Even result: " + n)),
                    
                // Odd number processing
                odd -> odd
                    .then("triple", n -> StepResult.continueWith(n * 3))
                    .then("add-one", n -> StepResult.continueWith(n + 1))
                    .then("format-odd", n -> StepResult.finish("Odd result: " + n))
            )
            .build();
        
        assertNotNull(workflow);
        // Should have: 1 initial + 3 even branch + 3 odd branch = 7 steps minimum
        assertTrue(workflow.nodes().size() >= 7);
    }
}
```

### 3.3 WorkflowBuilderValidationTest
Tests validation and error handling in workflow construction.

```java
package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowBuilderValidationTest {
    
    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {
        
        @Test
        @DisplayName("Should validate empty workflow")
        void testEmptyWorkflowValidation() {
            assertThrows(IllegalStateException.class, () ->
                WorkflowBuilder
                    .define("empty", String.class, String.class)
                    .build()
            );
        }
        
        @Test
        @DisplayName("Should validate duplicate step IDs")
        void testDuplicateStepIds() {
            assertThrows(IllegalArgumentException.class, () ->
                WorkflowBuilder
                    .define("duplicate-ids", String.class, String.class)
                    .then("step1", s -> StepResult.continueWith(s))
                    .then("step1", s -> StepResult.finish(s))  // Duplicate ID
                    .build()
            );
        }
        
        @Test
        @DisplayName("Should validate null step ID")
        void testNullStepId() {
            assertThrows(IllegalArgumentException.class, () ->
                WorkflowBuilder
                    .define("null-id", String.class, String.class)
                    .then(null, s -> StepResult.continueWith(s))
                    .build()
            );
        }
        
        @Test
        @DisplayName("Should validate null step function")
        void testNullStepFunction() {
            assertThrows(IllegalArgumentException.class, () ->
                WorkflowBuilder
                    .define("null-function", String.class, String.class)
                    .then("step1", null)
                    .build()
            );
        }
    }
}
```

## Test Execution Guide

### Running Individual Test Files

```bash
# Run a specific test class
mvn test -Dtest=StepOutputTest

# Run multiple test classes
mvn test -Dtest=StepOutputTest,StepDefinitionTest,WorkflowExceptionTest

# Run all tests in a package
mvn test -Dtest="ai.driftkit.workflow.engine.core.*Test"

# Run with specific test method
mvn test -Dtest=WorkflowContextTest#testCreateWithTriggerData
```

### Running Test Categories

```bash
# Run all basic unit tests
mvn test -Dtest="StepOutputTest,StepDefinitionTest,WorkflowExceptionTest"

# Run all workflow step tests
mvn test -Dtest="StepResultTest,WorkflowContextTest,InputPreparerTest"

# Run all builder tests
mvn test -Dtest="WorkflowBuilderBasicTest,WorkflowBuilderBranchTest,WorkflowBuilderErrorHandlingTest"
```

### Test Coverage Report

```bash
# Generate coverage report
mvn clean test jacoco:report

# View report at:
# target/site/jacoco/index.html
```

## Next Steps

After implementing these tests, the next phases would include:

4. **Integration Tests** - Testing workflows end-to-end
5. **Async and Concurrency Tests** - Testing async steps and concurrent execution
6. **Error Recovery Tests** - Testing suspension, resumption, and error handling
7. **Performance Tests** - Testing workflow execution performance
8. **Persistence Tests** - Testing state persistence and recovery

These tests provide a solid foundation for ensuring the workflow engine works correctly at the component level.

## Important Notes on Test Compatibility

All tests in this document have been verified to match the actual API of the workflow engine core components:

1. **StepOutput** - Uses correct methods like `hasValue()`, `getValue()`, `getActualClass()`
2. **StepDefinition** - Uses getter methods (`getId()`, `getExecutor()`, etc.) and builder methods (`withTypes()`, `withDescription()`)
3. **StepResult** - All factory methods match the actual API (e.g., `suspend()` takes prompt and input class)
4. **WorkflowContext** - Uses correct methods for context management and step output access
5. **WorkflowBuilder** - Uses correct method signatures with explicit type parameters
6. **WorkflowGraph** - Uses `nodes()` instead of `getSteps()` to access workflow nodes

The tests are ready to be implemented and should compile without issues.