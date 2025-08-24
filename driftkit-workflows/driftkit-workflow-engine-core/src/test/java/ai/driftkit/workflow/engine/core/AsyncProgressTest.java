package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.annotations.AsyncStep;
import ai.driftkit.workflow.engine.annotations.InitialStep;
import ai.driftkit.workflow.engine.annotations.Workflow;
import ai.driftkit.workflow.engine.async.TaskProgressReporter;
import ai.driftkit.workflow.engine.domain.WorkflowEvent;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for async workflow execution with progress reporting.
 */
@Slf4j
public class AsyncProgressTest {

    private WorkflowEngine engine;
    private TestAsyncWorkflow workflow;

    @BeforeEach
    void setUp() {
        engine = new WorkflowEngine();
        workflow = new TestAsyncWorkflow();
        engine.register(workflow);
    }

    @Test
    void testAsyncWorkflowWithProgressUpdates() throws Exception {
        // Start workflow
        WorkflowEngine.WorkflowExecution<String> execution = engine.execute("test-async-workflow", "test-input");
        String instanceId = execution.getRunId();

        // Wait a bit for async to start
        Thread.sleep(100);

        // Check workflow is suspended
        WorkflowInstance instance = engine.getWorkflowInstance(instanceId).orElseThrow();
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());

        // Check initial event
        WorkflowEvent currentResult = engine.getCurrentResult(instanceId).orElseThrow();
        assertNotNull(currentResult);
        log.debug("Initial event: {}% - async={}", currentResult.getPercentComplete(), currentResult.isAsync());

        // Reload instance to get latest state
        instance = engine.getWorkflowInstance(instanceId).orElseThrow();

        // Check async state is created
        log.debug("Current step: {}", instance.getCurrentStepId());
        log.debug("Status: {}", instance.getStatus());

        // Wait for async handler to start and update progress
        Thread.sleep(1000);

        // Check progress has been updated
        currentResult = engine.getCurrentResult(instanceId).orElseThrow();
        log.debug("Progress after 1000ms: {}% - {}", currentResult.getPercentComplete(), currentResult.getProperties());
        assertTrue(currentResult.getPercentComplete() > 0, "Expected progress > 0, but was " + currentResult.getPercentComplete());

        // Wait for completion
        String result = execution.get(5, TimeUnit.SECONDS);
        assertEquals("Processed: test-input", result);

        // Verify final state
        instance = engine.getWorkflowInstance(instanceId).orElseThrow();
        assertEquals(WorkflowInstance.WorkflowStatus.COMPLETED, instance.getStatus());

        // Verify progress was tracked
        assertTrue(workflow.progressUpdates.get() > 3, "Expected multiple progress updates");
    }

    //@Test
    void testAsyncCancellation() throws Exception {
        // Start workflow
        WorkflowEngine.WorkflowExecution<String> execution = engine.execute("test-async-workflow", "cancel-test");
        String instanceId = execution.getRunId();

        // Wait for async to start and check state
        Thread.sleep(500);

        // Reload instance to check async state
        WorkflowInstance instance = engine.getWorkflowInstance(instanceId).orElseThrow();
        log.debug("Instance status: {}", instance.getStatus());
        log.debug("Current step: {}", instance.getCurrentStepId());
        log.debug("Status: {}", instance.getStatus());

        // Cancel the operation
        boolean cancelled = engine.cancelAsyncOperation(instanceId);
        log.debug("Cancellation result: {}", cancelled);
        assertTrue(cancelled, "Expected cancellation to succeed");

        // Wait for workflow to fail
        Thread.sleep(500);

        // Check workflow failed
        instance = engine.getWorkflowInstance(instanceId).orElseThrow();
        assertEquals(WorkflowInstance.WorkflowStatus.FAILED, instance.getStatus());

        // Verify cancellation was detected
        assertTrue(workflow.wasCancelled.await(1, TimeUnit.SECONDS));
    }

    @Workflow(id = "test-async-workflow", version = "1.0")
    @Slf4j
    public static class TestAsyncWorkflow {
        final AtomicInteger progressUpdates = new AtomicInteger(0);
        final CountDownLatch wasCancelled = new CountDownLatch(1);

        @InitialStep(description = "Start async processing")
        public StepResult<String> startAsync(String input) {
            // Create a simple message object as immediate data
            Map<String, String> immediateData = Map.of(
                "message", "Processing started",
                "taskId", "test-async"
            );

            return new StepResult.Async<String>(
                    "test-async",
                    5000L, // 5 seconds
                    Map.of("input", input),
                    immediateData
            );
        }

        @AsyncStep("test-async")
        public StepResult<String> executeAsync(Map<String, Object> args,
                                               WorkflowContext context,
                                               TaskProgressReporter progress) {
            log.debug("ASYNC HANDLER CALLED with args: {}", args);
            String input = (String) args.get("input");

            try {
                // Simulate work with progress updates
                for (int i = 0; i <= 100; i += 20) {
                    if (progress.isCancelled()) {
                        wasCancelled.countDown();
                        return new StepResult.Fail<>("Operation cancelled");
                    }

                    log.debug("Updating progress to {}%", i);
                    progress.updateProgress(i, "Processing... " + i + "%");
                    progressUpdates.incrementAndGet();
                    Thread.sleep(100);
                }

                return new StepResult.Finish<>("Processed: " + input);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new StepResult.Fail<>(e);
            }
        }
    }
}