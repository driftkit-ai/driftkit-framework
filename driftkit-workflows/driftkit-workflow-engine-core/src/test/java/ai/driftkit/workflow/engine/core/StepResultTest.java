package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.persistence.inmemory.*;
import ai.driftkit.workflow.engine.async.InMemoryProgressTracker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class StepResultTest {
    
    private static WorkflowEngine engine;
    
    @BeforeAll
    static void setupEngine() {
        // Initialize WorkflowEngine to set up SchemaProvider
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .stateRepository(new InMemoryWorkflowStateRepository())
            .progressTracker(new InMemoryProgressTracker())
            .chatSessionRepository(new InMemoryChatSessionRepository())
            .chatHistoryRepository(new InMemoryChatHistoryRepository())
            .asyncStepStateRepository(new InMemoryAsyncStepStateRepository())
            .suspensionDataRepository(new InMemorySuspensionDataRepository())
            .build();
        
        engine = new WorkflowEngine(config);
    }
    
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
            assertEquals(value, cont.data());
        }
        
        @Test
        @DisplayName("Should allow null values in Continue")
        void testContinueWithNull() {
            StepResult<String> result = StepResult.continueWith(null);
            
            assertInstanceOf(StepResult.Continue.class, result);
            StepResult.Continue<String> cont = (StepResult.Continue<String>) result;
            assertNull(cont.data());
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
            assertEquals(value, finish.result());
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
        
        // Test class for suspend operations
        static class UserInput {
            private String name;
            private int age;
            
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public int getAge() { return age; }
            public void setAge(int age) { this.age = age; }
        }
        
        @Test
        @DisplayName("Should create Suspend with prompt and expected input class")
        void testSuspend() {
            String prompt = "Please provide your information";
            StepResult<String> result = StepResult.suspend(prompt, UserInput.class);
            
            assertInstanceOf(StepResult.Suspend.class, result);
            StepResult.Suspend<String> suspend = (StepResult.Suspend<String>) result;
            assertEquals(prompt, suspend.promptToUser());
            assertEquals(UserInput.class, suspend.nextInputClass());
            assertNotNull(suspend.nextInputSchema());
            assertNotNull(suspend.metadata());
            assertTrue(suspend.metadata().isEmpty());
        }
        
        @Test
        @DisplayName("Should create Suspend with metadata")
        void testSuspendWithMetadata() {
            String prompt = "Please approve the order";
            Map<String, Object> metadata = Map.of("orderId", "12345", "amount", 100.0);
            
            StepResult<String> result = StepResult.suspend(prompt, UserInput.class, metadata);
            
            assertInstanceOf(StepResult.Suspend.class, result);
            StepResult.Suspend<String> suspend = (StepResult.Suspend<String>) result;
            assertEquals(prompt, suspend.promptToUser());
            assertEquals(UserInput.class, suspend.nextInputClass());
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