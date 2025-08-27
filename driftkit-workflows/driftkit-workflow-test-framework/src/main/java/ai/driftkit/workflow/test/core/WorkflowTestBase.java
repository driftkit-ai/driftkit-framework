package ai.driftkit.workflow.test.core;

import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.persistence.WorkflowStateRepository;
import ai.driftkit.workflow.test.assertions.AssertionEngine;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Base class for all workflow tests providing common functionality.
 * This class is agnostic to workflow definition style (annotations vs fluent API).
 */
@Slf4j
public abstract class WorkflowTestBase {
    
    @Getter
    protected WorkflowEngine engine;
    
    @Getter
    protected WorkflowTestContext testContext;
    
    @Getter
    protected WorkflowTestInterceptor testInterceptor;
    
    
    @Getter
    protected WorkflowTestOrchestrator orchestrator;
    
    @Getter
    protected AssertionEngine assertions;
    
    /**
     * Default timeout for workflow execution.
     */
    protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    @BeforeEach
    void setupBase() {
        log.debug("Setting up workflow test base");
        
        // Create test context
        this.testContext = new WorkflowTestContext();
        
        // Create test interceptor and share MockRegistry and ExecutionTracker
        this.testInterceptor = new WorkflowTestInterceptor(
            testContext.getMockRegistry(), 
            testContext.getExecutionTracker()
        );
        
        // Create engine with interceptor
        this.engine = createAndConfigureEngine();
        
        // Create orchestrator
        this.orchestrator = new WorkflowTestOrchestrator(
            testContext.getMockRegistry(),
            testContext.getExecutionTracker(),
            testInterceptor,
            engine
        );
        
        // Create assertion engine
        this.assertions = new AssertionEngine(testContext.getExecutionTracker());

        log.debug("Workflow test base setup complete");
    }
    
    @AfterEach
    void tearDownBase() {
        log.debug("Tearing down workflow test base");
        
        // Clear test context
        if (testContext != null) {
            testContext.clear();
        }
        
        // Clear interceptor state
        if (testInterceptor != null) {
            testInterceptor.clear();
        }
        
        // Shutdown engine
        if (engine != null) {
            engine.shutdown();
        }
        
        log.debug("Workflow test base teardown complete");
    }
    
    /**
     * Creates and configures the workflow engine.
     * Subclasses can override this to provide custom engine configuration.
     * Default implementation returns a new WorkflowEngine instance.
     * 
     * @return configured workflow engine
     */
    protected WorkflowEngine createEngine() {
        return new WorkflowEngine();
    }

    /**
     * Creates and configures the workflow engine with test interceptor.
     */
    private WorkflowEngine createAndConfigureEngine() {
        WorkflowEngine engine = createEngine();
        engine.addInterceptor(testInterceptor);
        return engine;
    }
    
    // Common test utilities
    
    /**
     * Executes a workflow synchronously and waits for completion.
     * 
     * @param workflowId the workflow to execute
     * @param input the input data
     * @param <T> input type
     * @param <R> result type
     * @return the workflow result
     * @throws WorkflowExecutionException if workflow execution fails
     */
    protected <T, R> R executeWorkflow(String workflowId, T input) throws WorkflowExecutionException {
        return executeWorkflow(workflowId, input, DEFAULT_TIMEOUT);
    }
    
    /**
     * Executes a workflow synchronously and waits for completion.
     * 
     * @param workflowId the workflow to execute
     * @param input the input data
     * @param timeout maximum time to wait
     * @param <T> input type
     * @param <R> result type
     * @return the workflow result
     * @throws WorkflowExecutionException if workflow execution fails
     */
    @SuppressWarnings("unchecked")
    protected <T, R> R executeWorkflow(String workflowId, T input, Duration timeout) throws WorkflowExecutionException {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");
        
        var execution = engine.execute(workflowId, input);
        
        try {
            return (R) execution.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkflowExecutionException("Workflow execution interrupted", e);
        } catch (TimeoutException e) {
            throw new WorkflowExecutionException("Workflow " + workflowId + " did not complete within " + timeout, e);
        } catch (Exception e) {
            throw new WorkflowExecutionException("Workflow execution failed", e);
        }
    }
    
    /**
     * Executes a workflow asynchronously.
     * 
     * @param workflowId the workflow to execute
     * @param input the input data
     * @param <T> input type
     * @param <R> result type
     * @return workflow execution handle
     */
    protected <T, R> WorkflowEngine.WorkflowExecution<R> executeWorkflowAsync(String workflowId, T input) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(input, "input cannot be null");
        
        return engine.execute(workflowId, input);
    }
    
    /**
     * Executes a workflow that is expected to suspend.
     * Waits for the workflow to reach SUSPENDED status.
     * 
     * @param workflowId the workflow to execute
     * @param input the input data
     * @param <T> input type
     * @return the suspended workflow execution handle
     * @throws WorkflowExecutionException if workflow doesn't suspend within timeout
     */
    protected <T> WorkflowEngine.WorkflowExecution<?> executeAndExpectSuspend(String workflowId, T input) throws WorkflowExecutionException {
        return executeAndExpectSuspend(workflowId, input, DEFAULT_TIMEOUT);
    }
    
    /**
     * Executes a workflow that is expected to suspend.
     * Waits for the workflow to reach SUSPENDED status.
     * 
     * @param workflowId the workflow to execute
     * @param input the input data
     * @param timeout maximum time to wait for suspend
     * @param <T> input type
     * @return the suspended workflow execution handle
     * @throws WorkflowExecutionException if workflow doesn't suspend within timeout
     */
    protected <T> WorkflowEngine.WorkflowExecution<?> executeAndExpectSuspend(String workflowId, T input, Duration timeout) throws WorkflowExecutionException {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");
        
        var execution = engine.execute(workflowId, input);
        String runId = execution.getRunId();
        
        try {
            // Wait for workflow to reach SUSPENDED status
            waitForStatus(runId, WorkflowInstance.WorkflowStatus.SUSPENDED, timeout);
            return execution;
            
        } catch (WorkflowExecutionException e) {
            // Check if workflow completed instead of suspending
            if (execution.isDone()) {
                try {
                    Object result = execution.get(1, TimeUnit.MILLISECONDS);
                    throw new WorkflowExecutionException("Expected workflow to suspend, but it completed with result: " + result, e);
                } catch (Exception ex) {
                    throw new WorkflowExecutionException("Expected workflow to suspend, but it failed: " + ex.getMessage(), ex);
                }
            }
            
            // Check current status for better error message
            WorkflowInstance instance = getWorkflowInstance(runId);
            if (instance != null) {
                throw new WorkflowExecutionException("Expected workflow to suspend within " + timeout + 
                    ", but status is: " + instance.getStatus(), e);
            } else {
                throw new WorkflowExecutionException("Expected workflow to suspend within " + timeout + 
                    ", but workflow instance not found", e);
            }
        }
    }
    
    /**
     * Resumes a suspended workflow.
     * 
     * @param runId the workflow run ID
     * @param event the event to resume with
     * @param <E> event type
     * @param <R> result type
     * @return the workflow result
     * @throws WorkflowExecutionException if workflow execution fails
     */
    protected <E, R> R resumeWorkflow(String runId, E event) throws WorkflowExecutionException {
        return resumeWorkflow(runId, event, DEFAULT_TIMEOUT);
    }
    
    /**
     * Resumes a suspended workflow.
     * 
     * @param runId the workflow run ID
     * @param event the event to resume with
     * @param timeout maximum time to wait
     * @param <E> event type
     * @param <R> result type
     * @return the workflow result
     * @throws WorkflowExecutionException if workflow execution fails
     */
    @SuppressWarnings("unchecked")
    protected <E, R> R resumeWorkflow(String runId, E event, Duration timeout) throws WorkflowExecutionException {
        Objects.requireNonNull(runId, "runId cannot be null");
        Objects.requireNonNull(event, "event cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");
        
        var execution = engine.resume(runId, event);
        
        try {
            return (R) execution.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkflowExecutionException("Workflow resumption interrupted", e);
        } catch (TimeoutException e) {
            throw new WorkflowExecutionException("Workflow " + runId + " did not complete within " + timeout, e);
        } catch (Exception e) {
            throw new WorkflowExecutionException("Workflow resumption failed", e);
        }
    }
    
    /**
     * Gets workflow instance by run ID.
     * 
     * @param runId the workflow run ID
     * @return the workflow instance or null if not found
     */
    protected WorkflowInstance getWorkflowInstance(String runId) {
        Objects.requireNonNull(runId, "runId cannot be null");
        
        return engine.getWorkflowInstance(runId).orElse(null);
    }
    
    /**
     * Waits for a workflow to reach a specific status.
     * 
     * @param runId the workflow run ID
     * @param status the expected status
     * @param timeout maximum time to wait
     * @throws WorkflowExecutionException if status is not reached within timeout
     */
    protected void waitForStatus(String runId, WorkflowInstance.WorkflowStatus status, Duration timeout) 
            throws WorkflowExecutionException {
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
                throw new WorkflowExecutionException("Interrupted while waiting for workflow status", e);
            }
        }
        
        // Timeout reached
        WorkflowInstance instance = getWorkflowInstance(runId);
        WorkflowInstance.WorkflowStatus currentStatus = instance != null ? instance.getStatus() : null;
        
        throw new WorkflowExecutionException(
            "Workflow " + runId + " did not reach status " + status + " within " + timeout + 
            " (current status: " + currentStatus + ")"
        );
    }
    
    /**
     * Waits for a workflow to reach a specific status with default timeout.
     * 
     * @param runId the workflow run ID
     * @param status the expected status
     * @throws WorkflowExecutionException if status is not reached within default timeout
     */
    protected void waitForStatus(String runId, WorkflowInstance.WorkflowStatus status) throws WorkflowExecutionException {
        waitForStatus(runId, status, DEFAULT_TIMEOUT);
    }
}