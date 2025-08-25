package ai.driftkit.workflow.test.core;

import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.persistence.WorkflowStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Utility for waiting on workflow conditions without Thread.sleep() hacks.
 * Uses Awaitility for proper polling and timeout handling.
 */
@Slf4j
@RequiredArgsConstructor
public class WorkflowAwaiter {
    
    private final WorkflowStateRepository repository;
    
    /**
     * Default poll interval for checking conditions.
     */
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(50);
    
    /**
     * Waits for a workflow to reach a specific status.
     * 
     * @param runId the workflow run ID
     * @param expectedStatus the expected status
     * @param timeout maximum time to wait
     * @return the workflow instance when status is reached
     * @throws TimeoutException if status is not reached within timeout
     */
    public WorkflowInstance awaitStatus(String runId, WorkflowInstance.WorkflowStatus expectedStatus, 
                                       Duration timeout) throws TimeoutException {
        Objects.requireNonNull(runId, "runId cannot be null");
        Objects.requireNonNull(expectedStatus, "expectedStatus cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");
        
        log.debug("Waiting for workflow {} to reach status {}", runId, expectedStatus);
        
        try {
            return Awaitility.await("workflow status: " + expectedStatus)
                .atMost(timeout)
                .pollInterval(DEFAULT_POLL_INTERVAL)
                .until(
                    () -> repository.load(runId).orElse(null),
                    instance -> instance != null && instance.getStatus() == expectedStatus
                );
        } catch (ConditionTimeoutException e) {
            throw new TimeoutException(
                "Workflow " + runId + " did not reach status " + expectedStatus + " within " + timeout
            );
        }
    }
    
    /**
     * Waits for a workflow to complete (reach any terminal status).
     * 
     * @param runId the workflow run ID
     * @param timeout maximum time to wait
     * @return the workflow instance when completed
     * @throws TimeoutException if workflow doesn't complete within timeout
     */
    public WorkflowInstance awaitCompletion(String runId, Duration timeout) throws TimeoutException {
        Objects.requireNonNull(runId, "runId cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");
        
        log.debug("Waiting for workflow {} to complete", runId);
        
        try {
            return Awaitility.await("workflow completion")
                .atMost(timeout)
                .pollInterval(DEFAULT_POLL_INTERVAL)
                .until(
                    () -> repository.load(runId).orElse(null),
                    instance -> instance != null && isTerminal(instance.getStatus())
                );
        } catch (ConditionTimeoutException e) {
            throw new TimeoutException(
                "Workflow " + runId + " did not complete within " + timeout
            );
        }
    }
    
    /**
     * Waits for a workflow to exist in the repository.
     * 
     * @param runId the workflow run ID
     * @param timeout maximum time to wait
     * @return the workflow instance when found
     * @throws TimeoutException if workflow is not found within timeout
     */
    public WorkflowInstance awaitExistence(String runId, Duration timeout) throws TimeoutException {
        Objects.requireNonNull(runId, "runId cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");
        
        log.debug("Waiting for workflow {} to exist", runId);
        
        try {
            return Awaitility.await("workflow existence")
                .atMost(timeout)
                .pollInterval(DEFAULT_POLL_INTERVAL)
                .until(
                    () -> repository.load(runId).orElse(null),
                    Objects::nonNull
                );
        } catch (ConditionTimeoutException e) {
            throw new TimeoutException(
                "Workflow " + runId + " was not found within " + timeout
            );
        }
    }
    
    /**
     * Waits for a specific condition on a workflow.
     * 
     * @param runId the workflow run ID
     * @param condition the condition to wait for
     * @param description description of what we're waiting for
     * @param timeout maximum time to wait
     * @return the workflow instance when condition is met
     * @throws TimeoutException if condition is not met within timeout
     */
    public WorkflowInstance awaitCondition(String runId, 
                                         Predicate<WorkflowInstance> condition,
                                         String description,
                                         Duration timeout) throws TimeoutException {
        Objects.requireNonNull(runId, "runId cannot be null");
        Objects.requireNonNull(condition, "condition cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");
        
        log.debug("Waiting for workflow {} to meet condition: {}", runId, description);
        
        try {
            return Awaitility.await(description)
                .atMost(timeout)
                .pollInterval(DEFAULT_POLL_INTERVAL)
                .until(
                    () -> repository.load(runId).orElse(null),
                    instance -> instance != null && condition.test(instance)
                );
        } catch (ConditionTimeoutException e) {
            throw new TimeoutException(
                "Workflow " + runId + " did not meet condition '" + description + "' within " + timeout
            );
        }
    }
    
    /**
     * Checks if a workflow status is terminal.
     * 
     * @param status the workflow status
     * @return true if the status is terminal
     */
    private boolean isTerminal(WorkflowInstance.WorkflowStatus status) {
        return status == WorkflowInstance.WorkflowStatus.COMPLETED
            || status == WorkflowInstance.WorkflowStatus.FAILED
            || status == WorkflowInstance.WorkflowStatus.CANCELLED;
    }
}