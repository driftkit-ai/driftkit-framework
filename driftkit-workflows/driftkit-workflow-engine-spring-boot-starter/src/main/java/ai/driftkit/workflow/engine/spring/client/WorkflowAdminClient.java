package ai.driftkit.workflow.engine.spring.client;

import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Feign client for WorkflowAdminController.
 * Provides remote access to workflow administration endpoints.
 */
@FeignClient(name = "workflow-admin-service", path = "/api/v1/workflow-admin", configuration = WorkflowFeignConfiguration.class)
public interface WorkflowAdminClient {
    
    /**
     * Get all workflow instances with optional filtering.
     */
    @GetMapping("/instances")
    ResponseEntity<List<WorkflowInstance>> getAllInstances(
            @RequestParam(value = "workflowId", required = false) String workflowId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    );
    
    /**
     * Get a specific workflow instance.
     */
    @GetMapping("/instances/{instanceId}")
    ResponseEntity<WorkflowInstance> getInstance(@PathVariable("instanceId") String instanceId);
    
    /**
     * Delete a workflow instance.
     */
    @DeleteMapping("/instances/{instanceId}")
    ResponseEntity<Void> deleteInstance(@PathVariable("instanceId") String instanceId);
    
    /**
     * Clean up old workflow instances.
     */
    @PostMapping("/cleanup")
    ResponseEntity<Map<String, Object>> cleanup(
            @RequestParam(value = "daysToKeep", defaultValue = "30") int daysToKeep,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun
    );
    
    /**
     * Get workflow statistics.
     */
    @GetMapping("/stats")
    ResponseEntity<Map<String, Object>> getStatistics(
            @RequestParam(value = "workflowId", required = false) String workflowId
    );
}