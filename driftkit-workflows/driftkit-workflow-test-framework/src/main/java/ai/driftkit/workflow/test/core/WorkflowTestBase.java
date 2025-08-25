package ai.driftkit.workflow.test.core;

import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.persistence.WorkflowStateRepository;
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
    protected WorkflowStateRepository stateRepository;
    
    /**
     * Default timeout for workflow execution.
     */
    protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    @BeforeEach
    void setupBase() {
        log.debug("Setting up workflow test base");
        
        // Create test context
        this.testContext = new WorkflowTestContext();
        
        // Create test interceptor and share MockRegistry
        this.testInterceptor = new WorkflowTestInterceptor(testContext.getMockRegistry());
        
        // Create engine with interceptor
        this.engine = createAndConfigureEngine();
        
        // Register workflows
        registerWorkflows();
        
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
     * Subclasses must implement this to provide engine configuration specific to their needs.
     * 
     * @return configured workflow engine
     */
    protected abstract WorkflowEngine createEngine();
    
    /**
     * Registers workflows with the engine.
     * Subclasses should override this to register their test workflows.
     */
    protected abstract void registerWorkflows();
    
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
     * @throws TimeoutException if workflow doesn't complete within default timeout
     */
    protected <T, R> R executeWorkflow(String workflowId, T input) throws TimeoutException {
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
     * @throws TimeoutException if workflow doesn't complete within timeout
     */
    @SuppressWarnings("unchecked")
    protected <T, R> R executeWorkflow(String workflowId, T input, Duration timeout) throws TimeoutException {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");
        
        var execution = engine.execute(workflowId, input);
        
        try {
            return (R) execution.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Workflow execution interrupted", e);
        } catch (TimeoutException e) {
            throw new TimeoutException("Workflow " + workflowId + " did not complete within " + timeout);
        } catch (Exception e) {
            throw new RuntimeException("Workflow execution failed", e);
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
     * Resumes a suspended workflow.
     * 
     * @param runId the workflow run ID
     * @param event the event to resume with
     * @param <E> event type
     * @param <R> result type
     * @return the workflow result
     * @throws TimeoutException if workflow doesn't complete within default timeout
     */
    protected <E, R> R resumeWorkflow(String runId, E event) throws TimeoutException {
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
     * @throws TimeoutException if workflow doesn't complete within timeout
     */
    @SuppressWarnings("unchecked")
    protected <E, R> R resumeWorkflow(String runId, E event, Duration timeout) throws TimeoutException {
        Objects.requireNonNull(runId, "runId cannot be null");
        Objects.requireNonNull(event, "event cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");
        
        var execution = engine.resume(runId, event);
        
        try {
            return (R) execution.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Workflow resumption interrupted", e);
        } catch (TimeoutException e) {
            throw new TimeoutException("Workflow " + runId + " did not complete within " + timeout);
        } catch (Exception e) {
            throw new RuntimeException("Workflow resumption failed", e);
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
     * @throws TimeoutException if status is not reached within timeout
     */
    protected void waitForStatus(String runId, WorkflowInstance.WorkflowStatus status, Duration timeout) 
            throws TimeoutException {
        Objects.requireNonNull(runId, "runId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");
        
        WorkflowAwaiter awaiter = new WorkflowAwaiter(stateRepository);
        WorkflowInstance instance = awaiter.awaitStatus(runId, status, timeout);
        
        if (instance == null || instance.getStatus() != status) {
            throw new TimeoutException(
                "Workflow " + runId + " did not reach status " + status + " within " + timeout
            );
        }
    }
    
    /**
     * Waits for a workflow to reach a specific status with default timeout.
     * 
     * @param runId the workflow run ID
     * @param status the expected status
     * @throws TimeoutException if status is not reached within default timeout
     */
    protected void waitForStatus(String runId, WorkflowInstance.WorkflowStatus status) throws TimeoutException {
        waitForStatus(runId, status, DEFAULT_TIMEOUT);
    }
}