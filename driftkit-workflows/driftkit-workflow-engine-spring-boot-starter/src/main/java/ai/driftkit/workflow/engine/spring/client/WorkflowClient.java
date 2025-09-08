package ai.driftkit.workflow.engine.spring.client;

import ai.driftkit.workflow.engine.spring.dto.WorkflowDtos.*;
import ai.driftkit.workflow.engine.domain.WorkflowDetails;
import ai.driftkit.workflow.engine.domain.WorkflowMetadata;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import ai.driftkit.workflow.engine.async.ProgressTracker.Progress;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Feign client for WorkflowController.
 * Provides remote access to workflow execution and management endpoints.
 */
@FeignClient(name = "workflow-service", path = "/api/workflows", configuration = WorkflowFeignConfiguration.class)
public interface WorkflowClient {
    
    /**
     * Execute a workflow with the given input.
     */
    @PostMapping("/{workflowId}/execute")
    ResponseEntity<WorkflowResponse> execute(
            @PathVariable("workflowId") String workflowId,
            @RequestBody WorkflowExecutionRequest request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId
    );
    
    /**
     * Get workflow status.
     */
    @GetMapping("/{workflowId}/runs/{runId}/status")
    ResponseEntity<WorkflowStatusResponse> getStatus(
            @PathVariable("workflowId") String workflowId,
            @PathVariable("runId") String runId
    );
    
    /**
     * Resume a suspended workflow.
     */
    @PostMapping("/{workflowId}/runs/{runId}/resume")
    ResponseEntity<WorkflowResponse> resume(
            @PathVariable("workflowId") String workflowId,
            @PathVariable("runId") String runId,
            @RequestBody Map<String, Object> input
    );
    
    /**
     * Cancel a workflow execution.
     */
    @DeleteMapping("/{workflowId}/runs/{runId}")
    ResponseEntity<Void> cancel(
            @PathVariable("workflowId") String workflowId,
            @PathVariable("runId") String runId
    );
    
    /**
     * Get async task status.
     */
    @GetMapping("/tasks/{taskId}/status")
    ResponseEntity<Progress> getTaskStatus(@PathVariable("taskId") String taskId);
    
    /**
     * Wait for async task result.
     */
    @GetMapping("/tasks/{taskId}/result")
    ResponseEntity<WorkflowResponse> getTaskResult(
            @PathVariable("taskId") String taskId,
            @RequestParam(value = "timeout", defaultValue = "30000") long timeout
    );
    
    /**
     * Get the input schema for a workflow.
     */
    @GetMapping("/{workflowId}/schema/input")
    ResponseEntity<AIFunctionSchema> getInputSchema(@PathVariable("workflowId") String workflowId);
    
    /**
     * Get the output schema for a workflow.
     */
    @GetMapping("/{workflowId}/schema/output")
    ResponseEntity<AIFunctionSchema> getOutputSchema(@PathVariable("workflowId") String workflowId);
    
    /**
     * List all registered workflows.
     */
    @GetMapping
    ResponseEntity<List<WorkflowMetadata>> listWorkflows();
    
    /**
     * Get detailed information about a workflow.
     */
    @GetMapping("/{workflowId}")
    ResponseEntity<WorkflowDetails> getWorkflowDetails(@PathVariable("workflowId") String workflowId);
    
    /**
     * Get workflow instances.
     */
    @GetMapping("/{workflowId}/instances")
    ResponseEntity<List<WorkflowInstance>> getWorkflowInstances(
            @PathVariable("workflowId") String workflowId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    );
    
    /**
     * Get a specific workflow instance.
     */
    @GetMapping("/{workflowId}/instances/{instanceId}")
    ResponseEntity<WorkflowInstance> getWorkflowInstance(
            @PathVariable("workflowId") String workflowId,
            @PathVariable("instanceId") String instanceId
    );
    
    /**
     * Retry a failed workflow instance.
     */
    @PostMapping("/{workflowId}/instances/{instanceId}/retry")
    ResponseEntity<WorkflowResponse> retryInstance(
            @PathVariable("workflowId") String workflowId,
            @PathVariable("instanceId") String instanceId,
            @RequestParam(value = "fromStep", required = false) String fromStep
    );
    
    /**
     * Get workflow execution history.
     */
    @GetMapping("/{workflowId}/history")
    ResponseEntity<List<WorkflowHistoryEntry>> getHistory(
            @PathVariable("workflowId") String workflowId,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    );
}