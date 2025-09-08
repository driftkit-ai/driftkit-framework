package ai.driftkit.workflow.engine.spring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO classes for Workflow Admin operations.
 */
public class WorkflowAdminDtos {
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowInfo {
        private String id;
        private String name;
        private String description;
        private String version;
        private boolean enabled;
        private Map<String, Object> metadata;
        private List<String> tags;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CleanupResult {
        private int totalInstances;
        private int deletedInstances;
        private List<String> deletedInstanceIds;
        private boolean dryRun;
        private String message;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowStatistics {
        private String workflowId;
        private long totalExecutions;
        private long successfulExecutions;
        private long failedExecutions;
        private long runningExecutions;
        private long suspendedExecutions;
        private double averageExecutionTimeMs;
        private Map<String, Long> executionsByStatus;
        private Map<String, Object> additionalMetrics;
    }
}