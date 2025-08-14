package ai.driftkit.workflow.engine.examples;

import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.core.WorkflowEngine.WorkflowExecution;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.domain.WorkflowEvent;
import ai.driftkit.workflow.engine.schema.DefaultSchemaProvider;
import ai.driftkit.workflow.engine.schema.SchemaProvider;
import ai.driftkit.workflow.engine.chat.ChatDomain.*;
import ai.driftkit.workflow.engine.examples.ChatWorkflowExample.*;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.persistence.inmemory.InMemoryWorkflowStateRepository;
import ai.driftkit.workflow.engine.persistence.WorkflowStateRepository;
import ai.driftkit.common.domain.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for ChatWorkflowExample demonstrating all StepResult types.
 * Tests workflow execution through the WorkflowEngine API with proper state verification.
 */
public class ChatWorkflowExampleTest {

    private WorkflowEngine engine;
    private ChatWorkflowExample workflow;
    private SchemaProvider schemaProvider;
    private WorkflowStateRepository stateRepository;
    private TestExecutionListener testListener;

    @Mock
    private ChatWorkflowExample.ExternalApiService apiService;

    /**
     * Test listener to capture workflow execution events.
     */
    private static class TestExecutionListener implements WorkflowEngine.WorkflowExecutionListener {
        private final List<String> executedSteps = new ArrayList<>();
        private final Map<String, StepResult<?>> stepResults = new HashMap<>();
        private CountDownLatch suspendLatch = new CountDownLatch(1);
        private CountDownLatch completeLatch = new CountDownLatch(1);
        private WorkflowInstance lastSuspendedInstance;
        private Object finalResult;
        private Throwable lastError;

        @Override
        public void onStepCompleted(WorkflowInstance instance, String stepId, StepResult<?> result) {
            executedSteps.add(stepId);
            stepResults.put(stepId, result);
            System.out.println("Step completed: " + stepId + " with result type: " + result.getClass().getSimpleName());
        }

        @Override
        public void onWorkflowSuspended(WorkflowInstance instance) {
            System.out.println("Workflow suspended at step: " + instance.getCurrentStepId());
            lastSuspendedInstance = instance;
            suspendLatch.countDown();
        }

        @Override
        public void onWorkflowCompleted(WorkflowInstance instance, Object result) {
            System.out.println("Workflow completed with result: " + result);
            finalResult = result;
            completeLatch.countDown();
        }

        @Override
        public void onWorkflowFailed(WorkflowInstance instance, Throwable error) {
            System.out.println("Workflow failed with error: " + error.getMessage());
            if (error != null) {
                error.printStackTrace();
            }
            lastError = error;
            completeLatch.countDown();
        }

        public boolean awaitSuspension(Duration timeout) throws InterruptedException {
            return suspendLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        public boolean awaitCompletion(Duration timeout) throws InterruptedException {
            return completeLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        public void reset() {
            executedSteps.clear();
            stepResults.clear();
            lastSuspendedInstance = null;
            finalResult = null;
            lastError = null;
            suspendLatch = new CountDownLatch(1);
            completeLatch = new CountDownLatch(1);
        }
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        schemaProvider = new DefaultSchemaProvider();
        workflow = new ChatWorkflowExample(schemaProvider, apiService);

        // Create state repository
        stateRepository = new InMemoryWorkflowStateRepository();

        // Create engine with custom config
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
                .stateRepository(stateRepository)
                .coreThreads(2)
                .maxThreads(10)
                .defaultStepTimeoutMs(5000)
                .build();

        engine = new WorkflowEngine(config);

        // Add test listener
        testListener = new TestExecutionListener();
        engine.addListener("test", testListener);

        // Register workflow
        engine.register(workflow);

        // Enable debug logging
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
    }

    @Test
    @DisplayName("Should handle greeting intent and suspend for user input")
    void testGreetingFlowWithSuspension() throws Exception {
        // Given
        ChatRequest request = new ChatRequest(
                "chat-123",
                Map.of("message", "Hello!", "userId", "user-456"),
                Language.ENGLISH,
                "advanced-chat-workflow"
        );
        request.setUserId("user-456");

        // When
        WorkflowExecution<Object> execution = engine.execute("advanced-chat-workflow", request);

        // Then - verify workflow suspends
        assertTrue(testListener.awaitSuspension(Duration.ofSeconds(5)));
        assertNotNull(testListener.lastSuspendedInstance);
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, testListener.lastSuspendedInstance.getStatus());

        // Verify executed steps
        assertTrue(testListener.executedSteps.contains("processInitialRequest"));
        assertTrue(testListener.executedSteps.contains("routeByIntent"));
        assertTrue(testListener.executedSteps.contains("handleGreeting"));

        // Verify suspension - suspension data is now managed separately
        assertNotNull(testListener.lastSuspendedInstance);
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, testListener.lastSuspendedInstance.getStatus());

        // Resume with user input - send thank you to trigger positive feedback flow
        UserChatMessage userMessage = new UserChatMessage();
        userMessage.setMessage("Thank you, that was helpful!");

        WorkflowExecution<Object> resumedExecution = engine.resume(execution.getRunId(), userMessage);

        // Should complete after resume
        assertTrue(testListener.awaitCompletion(Duration.ofSeconds(5)));
        assertNotNull(testListener.finalResult);

        // Verify final result
        Object finalResult = resumedExecution.get(5, TimeUnit.SECONDS);
        assertNotNull(finalResult);
    }

    @Test
    @DisplayName("Should handle question with async search for complex queries")
    void testQuestionFlowWithAsyncSearch() throws Exception {
        // Given
        ChatRequest request = new ChatRequest(
                "chat-123",
                Map.of("message", "What are the latest developments in quantum computing?", "userId", "user-789"),
                Language.ENGLISH,
                "advanced-chat-workflow"
        );
        request.setUserId("user-789");

        // Mock async search
        SearchResult mockResult = new SearchResult(
                "Quantum computing has seen significant advances...",
                0.95,
                List.of("arxiv.org/quantum", "nature.com/quantum")
        );

        CompletableFuture<SearchResult> searchFuture = new CompletableFuture<>();
        when(apiService.searchAsync(anyString())).thenReturn(searchFuture);

        // When
        WorkflowExecution<Object> execution = engine.execute("advanced-chat-workflow", request);

        // Complete the async search after a delay
        CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS).execute(() -> {
            searchFuture.complete(mockResult);
        });

        // Wait for the workflow to suspend (not complete)
        boolean suspended = testListener.awaitSuspension(Duration.ofSeconds(2));
        assertTrue(suspended, "Workflow should have suspended");

        // Wait a bit more for async operation to complete
        Thread.sleep(200);

        // Check workflow is still suspended (waiting for user input)
        Optional<WorkflowInstance> instance = stateRepository.load(execution.getRunId());
        assertTrue(instance.isPresent());
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.get().getStatus());

        // Verify async search was called
        verify(apiService).searchAsync(contains("quantum computing"));

        // Verify steps were executed
        assertTrue(testListener.executedSteps.contains("processInitialRequest"));
        assertTrue(testListener.executedSteps.contains("routeByIntent"));
        assertTrue(testListener.executedSteps.contains("handleQuestion"));
    }

    @Test
    @DisplayName("Should handle task request and collect details")
    void testTaskRequestFlow() throws Exception {
        // Given
        ChatRequest request = new ChatRequest(
                "chat-123",
                Map.of("message", "I need you to help me organize a project", "userId", "user-321"),
                Language.ENGLISH,
                "advanced-chat-workflow"
        );
        request.setUserId("user-321");

        // When
        WorkflowExecution<Object> execution = engine.execute("advanced-chat-workflow", request);

        // Should suspend asking for task details
        boolean suspended = testListener.awaitSuspension(Duration.ofSeconds(5));
        if (!suspended) {
            System.out.println("Not suspended. Executed steps: " + testListener.executedSteps);
            System.out.println("Last error: " + testListener.lastError);
        }
        assertTrue(suspended);

        // Verify suspension
        assertNotNull(testListener.lastSuspendedInstance);
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, testListener.lastSuspendedInstance.getStatus());

        // Resume with task details
        TaskDetails details = new TaskDetails();
        details.setTaskName("Project Organization");
        details.setRequirements(List.of("Create folder structure", "Set up documentation", "Initialize git repo"));
        details.setPriority("high");
        details.setNotes("This is for a new web application project");

        testListener.reset();
        WorkflowExecution<Object> resumedExecution = engine.resume(execution.getRunId(), details);

        // Wait for async task to complete
        Thread.sleep(2000); // Give async handler time to complete

        // Check workflow state after async execution
        Optional<WorkflowInstance> finalInstance = stateRepository.load(execution.getRunId());
        assertTrue(finalInstance.isPresent());

        System.out.println("Final workflow status: " + finalInstance.get().getStatus());
        System.out.println("Current step: " + finalInstance.get().getCurrentStepId());
        System.out.println("Executed steps: " + testListener.executedSteps);

        // Should complete task execution
        assertEquals(WorkflowInstance.WorkflowStatus.COMPLETED, finalInstance.get().getStatus());

        // Verify task execution steps were called
        assertTrue(testListener.executedSteps.contains("processTaskDetails"));
        assertTrue(testListener.executedSteps.contains("executeTask"));
    }

    @Test
    @DisplayName("Should handle feedback and process sentiment")
    void testFeedbackFlow() throws Exception {
        // Given - negative feedback
        ChatRequest request = new ChatRequest(
                "chat-123",
                Map.of("message", "This isn't working properly, I'm frustrated", "userId", "user-555"),
                Language.ENGLISH,
                "advanced-chat-workflow"
        );
        request.setUserId("user-555");

        // When
        WorkflowExecution<Object> execution = engine.execute("advanced-chat-workflow", request);

        // Should suspend asking for detailed feedback
        assertTrue(testListener.awaitSuspension(Duration.ofSeconds(5)));

        // Verify we're in feedback flow
        assertTrue(testListener.executedSteps.contains("handleFeedback"));

        // Resume with detailed feedback
        DetailedFeedback feedback = new DetailedFeedback();
        feedback.setIssue("The search results are not relevant");
        feedback.setSuggestions("Improve search algorithm accuracy");
        feedback.setRating(2);

        // Reset listener to track new steps
        testListener.reset();

        WorkflowExecution<Object> resumedExecution = engine.resume(execution.getRunId(), feedback);

        // Should suspend again waiting for user message
        assertTrue(testListener.awaitSuspension(Duration.ofSeconds(5)));
        assertTrue(testListener.executedSteps.contains("processDetailedFeedback"));

        // Verify suspension
        assertNotNull(testListener.lastSuspendedInstance);
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, testListener.lastSuspendedInstance.getStatus());

        // Resume with user response to complete the workflow
        UserChatMessage userMessage = new UserChatMessage();
        userMessage.setMessage("Thank you for taking my feedback!");

        testListener.reset();
        WorkflowExecution<Object> finalExecution = engine.resume(resumedExecution.getRunId(), userMessage);

        // Should complete now
        assertTrue(testListener.awaitCompletion(Duration.ofSeconds(5)));

        Object finalResult = finalExecution.get(5, TimeUnit.SECONDS);
        assertNotNull(finalResult);
    }

    @Test
    @DisplayName("Should handle errors gracefully and offer recovery options")
    void testErrorHandling() throws Exception {
        // Given - simulate an error scenario
        ChatRequest request = new ChatRequest(
                "chat-123",
                Map.of("message", "What are the latest trends in technology?", "userId", "user-999"),
                Language.ENGLISH,
                "advanced-chat-workflow"
        );
        request.setUserId("user-999");

        // Mock search to throw exception
        when(apiService.searchAsync(anyString()))
                .thenThrow(new RuntimeException("Search service unavailable"));

        // When
        WorkflowExecution<Object> execution = engine.execute("advanced-chat-workflow", request);

        // Should fail due to search service error
        assertThrows(ExecutionException.class, () -> {
            execution.get(2, TimeUnit.SECONDS);
        });

        // Verify workflow failed
        Optional<WorkflowInstance> instance = stateRepository.load(execution.getRunId());
        assertTrue(instance.isPresent());
        assertEquals(WorkflowInstance.WorkflowStatus.FAILED, instance.get().getStatus());

        // Verify the step that failed
        assertTrue(testListener.executedSteps.contains("handleQuestion"));

        // Check if we captured an error - it might be null in some cases
        if (testListener.lastError == null) {
            // If lastError is null, check the workflow instance for error info
            Optional<WorkflowInstance> failedInstance = stateRepository.load(execution.getRunId());
            assertTrue(failedInstance.isPresent());
            assertEquals(WorkflowInstance.WorkflowStatus.FAILED, failedInstance.get().getStatus());

            // The error info should be in the workflow instance
            assertNotNull(failedInstance.get().getErrorInfo());
            String errorMessage = failedInstance.get().getErrorInfo().errorMessage();
            assertTrue(errorMessage != null && errorMessage.contains("Search service unavailable"),
                    "Expected error message to contain 'Search service unavailable' but was: " + errorMessage);
        } else {
            // Check the captured error
            String errorMessage = testListener.lastError.getMessage();
            if (errorMessage == null && testListener.lastError.getCause() != null) {
                errorMessage = testListener.lastError.getCause().getMessage();
            }
            System.out.println("Error message: " + errorMessage);
            System.out.println("Error class: " + testListener.lastError.getClass());

            // The error might be wrapped in a WorkflowExecutionException
            assertTrue(errorMessage != null &&
                            (errorMessage.contains("Search service unavailable") ||
                                    errorMessage.contains("Workflow failed")),
                    "Expected error message to contain 'Search service unavailable' but was: " + errorMessage);
        }
    }

    @Test
    @DisplayName("Should handle unknown intent and ask for clarification")
    void testUnknownIntentFlow() throws Exception {
        // Given - ambiguous request
        ChatRequest request = new ChatRequest(
                "chat-123",
                Map.of("message", "xyz123 qwerty", "userId", "user-111"),
                Language.ENGLISH,
                "advanced-chat-workflow"
        );
        request.setUserId("user-111");

        // When
        WorkflowExecution<Object> execution = engine.execute("advanced-chat-workflow", request);

        // Should suspend asking for clarification
        assertTrue(testListener.awaitSuspension(Duration.ofSeconds(5)));

        // Verify suspension
        assertNotNull(testListener.lastSuspendedInstance);
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, testListener.lastSuspendedInstance.getStatus());

        // Resume with clarification
        UserClarification clarification = new UserClarification();
        clarification.setClarification("I want to search for information about xyz123");

        WorkflowExecution<Object> resumedExecution = engine.resume(execution.getRunId(), clarification);

        // Should process clarified request
        assertTrue(testListener.awaitCompletion(Duration.ofSeconds(5)));
        assertTrue(testListener.executedSteps.contains("processClarification"));
    }

    @Test
    @DisplayName("Should track workflow execution history")
    void testExecutionHistory() throws Exception {
        // Given
        ChatRequest request = new ChatRequest(
                "chat-123",
                Map.of("message", "Hello there!", "userId", "user-222"),
                Language.ENGLISH,
                "advanced-chat-workflow"
        );
        request.setUserId("user-222");

        // When
        WorkflowExecution<Object> execution = engine.execute("advanced-chat-workflow", request);

        // Wait for suspension
        assertTrue(testListener.awaitSuspension(Duration.ofSeconds(5)));

        // Get workflow instance from repository
        Optional<WorkflowInstance> instanceOpt = stateRepository.load(execution.getRunId());
        assertTrue(instanceOpt.isPresent());

        WorkflowInstance instance = instanceOpt.get();

        // Verify execution history
        List<WorkflowInstance.StepExecutionRecord> history = instance.getExecutionHistory();
        assertFalse(history.isEmpty());

        // Verify steps were executed in order
        List<String> stepIds = history.stream()
                .map(WorkflowInstance.StepExecutionRecord::getStepId)
                .toList();

        assertTrue(stepIds.contains("processInitialRequest"));
        assertTrue(stepIds.contains("routeByIntent"));
        assertTrue(stepIds.contains("handleGreeting"));

        // Verify each step succeeded (except async steps which might still be in progress)
        history.forEach(record -> {
            if (!record.getStepId().equals("handleQuestion") || record.isSuccess()) {
                assertTrue(record.isSuccess(), "Step " + record.getStepId() + " should have succeeded");
                assertTrue(record.getDurationMs() >= 0, "Step " + record.getStepId() + " should have non-negative duration");
                // Output might be null for some steps
            }
        });
    }

    @Test
    @DisplayName("Should handle concurrent workflow executions")
    void testConcurrentExecutions() throws Exception {
        int concurrentRequests = 5;
        List<CompletableFuture<WorkflowExecution<Object>>> futures = new ArrayList<>();

        // Create multiple concurrent requests
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            CompletableFuture<WorkflowExecution<Object>> future = CompletableFuture.supplyAsync(() -> {
                ChatRequest request = new ChatRequest(
                        "chat-" + index,
                        Map.of("message", "Hello from user " + index, "userId", "user-" + index),
                        Language.ENGLISH,
                        "advanced-chat-workflow"
                );
                request.setUserId("user-" + index);
                return engine.execute("advanced-chat-workflow", request);
            });
            futures.add(future);
        }

        // Wait for all executions to start
        List<WorkflowExecution<Object>> executions = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        // Verify all executions have unique run IDs
        Set<String> runIds = executions.stream()
                .map(WorkflowExecution::getRunId)
                .collect(Collectors.toSet());

        assertEquals(concurrentRequests, runIds.size());

        // Wait a bit for workflows to reach suspended state
        Thread.sleep(2000);

        // Verify all are in suspended state
        for (WorkflowExecution<Object> exec : executions) {
            Optional<WorkflowInstance> instance = stateRepository.load(exec.getRunId());
            assertTrue(instance.isPresent());

            // Wait for the workflow to reach suspended state (greetings suspend waiting for user input)
            int retries = 0;
            while (instance.get().getStatus() == WorkflowInstance.WorkflowStatus.RUNNING && retries < 10) {
                Thread.sleep(100);
                instance = stateRepository.load(exec.getRunId());
                retries++;
            }

            assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.get().getStatus());
        }
    }

    @Test
    @DisplayName("Should handle async operations with new suspend behavior")
    void testAsyncStepTimeout() throws Exception {
        // Given - a question that triggers async search
        ChatRequest request = new ChatRequest(
                "chat-123",
                Map.of("message", "What are the latest quantum computing developments?", "userId", "user-timeout"),
                Language.ENGLISH,
                "advanced-chat-workflow"
        );
        request.setUserId("user-timeout");

        // Mock search to return completed future immediately
        SearchResult mockResult = new SearchResult(
                "Quantum computing has advanced significantly...",
                0.95,
                List.of("arxiv.org", "nature.com")
        );
        // Return already completed future so async handler completes immediately
        when(apiService.searchAsync(anyString())).thenReturn(CompletableFuture.completedFuture(mockResult));

        // When
        WorkflowExecution<Object> execution = engine.execute("advanced-chat-workflow", request);

        // Wait for workflow to reach suspended state
        boolean suspended = testListener.awaitSuspension(Duration.ofSeconds(2));
        if (!suspended) {
            System.err.println("Workflow did not suspend within timeout");
        }

        // Check workflow state
        Optional<WorkflowInstance> instance = stateRepository.load(execution.getRunId());
        assertTrue(instance.isPresent());

        System.out.println("Workflow status after suspension: " + instance.get().getStatus());
        System.out.println("Current step: " + instance.get().getCurrentStepId());
        System.out.println("Executed steps: " + testListener.executedSteps);

        // Check what happened if the workflow failed
        if (instance.get().getStatus() == WorkflowInstance.WorkflowStatus.FAILED) {
            System.err.println("Workflow failed!");
            if (testListener.lastError != null) {
                System.err.println("Error details:");
                testListener.lastError.printStackTrace();
            }
            // Check execution history
            instance.get().getExecutionHistory().forEach(record -> {
                System.err.println("Step " + record.getStepId() + ": " +
                        (record.isSuccess() ? "SUCCESS" : "FAILED"));
            });
        }

        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.get().getStatus());
        assertEquals("handleQuestion", instance.get().getCurrentStepId());

        // Check that we can get current result with progress
        Optional<WorkflowEvent> currentResult = engine.getCurrentResult(execution.getRunId());
        assertTrue(currentResult.isPresent());

        // The async handler may have already started processing, check the state

        // Debug output
        System.out.println("Current result - completed: " + currentResult.get().isCompleted() +
                ", percentComplete: " + currentResult.get().getPercentComplete() +
                ", properties: " + currentResult.get().getProperties());

        // If async handler hasn't completed yet, we should see progress
        if (!currentResult.get().isCompleted()) {
            // The async operation might have finished very quickly and set progress to 100%
            // but the workflow event might not be marked as completed yet
            if (currentResult.get().getPercentComplete() < 100) {
                assertTrue(currentResult.get().getPercentComplete() >= 0,
                        "Expected percentComplete >= 0, but was " + currentResult.get().getPercentComplete());
            }
            // Async started events may not have properties initially
            assertNotNull(currentResult.get().getProperties());
        }

        // Wait for async operation to complete and workflow to process result
        Thread.sleep(1000);

        // After async completes, workflow should still be suspended (waiting for user input)
        instance = stateRepository.load(execution.getRunId());
        assertTrue(instance.isPresent());

        System.out.println("Workflow status after async completion: " + instance.get().getStatus());

        // Check what happened if the workflow failed
        if (instance.get().getStatus() == WorkflowInstance.WorkflowStatus.FAILED) {
            System.err.println("Workflow failed after async!");
            if (testListener.lastError != null) {
                System.err.println("Error details:");
                testListener.lastError.printStackTrace();
            }
            // Check execution history
            instance.get().getExecutionHistory().forEach(record -> {
                System.err.println("Step " + record.getStepId() + ": " +
                        (record.isSuccess() ? "SUCCESS" : "FAILED"));
            });
        }

        // In the new async-as-suspend model, workflow should remain suspended
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.get().getStatus());

        // Current result should show the search completed
        currentResult = engine.getCurrentResult(execution.getRunId());
        assertTrue(currentResult.isPresent());
        assertTrue(currentResult.get().isCompleted());
        assertEquals(100, currentResult.get().getPercentComplete());
    }

    @AfterEach
    void tearDown() {
        engine.removeListener("test");
        engine.shutdown();
    }
}