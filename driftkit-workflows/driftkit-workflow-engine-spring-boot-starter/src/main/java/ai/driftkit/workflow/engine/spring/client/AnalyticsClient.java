package ai.driftkit.workflow.engine.spring.client;

import ai.driftkit.workflow.engine.spring.dto.AnalyticsDtos.DailyMetricsResponse;
import ai.driftkit.workflow.engine.spring.dto.AnalyticsDtos.PromptMetricsResponse;
import ai.driftkit.workflow.engine.spring.dto.AnalyticsDtos.TaskVariables;
import ai.driftkit.workflow.engine.spring.tracing.domain.ModelRequestTrace;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Feign client for AnalyticsController.
 * Provides remote access to analytics and metrics endpoints.
 */
@FeignClient(name = "analytics-service", path = "/api/v1/analytics", configuration = WorkflowFeignConfiguration.class)
public interface AnalyticsClient {
    
    /**
     * Get model request traces within a time range.
     */
    @GetMapping("/traces")
    Page<ModelRequestTrace> getTraces(
            @RequestParam(value = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(value = "promptId", required = false) String promptId,
            @RequestParam(value = "excludePurpose", required = false) String excludePurpose,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    );
    
    /**
     * Get traces by context ID.
     */
    @GetMapping("/traces/context/{contextId}")
    List<ModelRequestTrace> getTracesByContextId(@PathVariable("contextId") String contextId);
    
    /**
     * Get message tasks by context IDs.
     */
    @PostMapping("/tasks")
    List<TaskVariables> getMessageTasksByContextIds(@RequestBody List<String> contextIds);
    
    /**
     * Get available prompt methods for analytics.
     */
    @GetMapping("/prompts/methods")
    List<String> getAvailablePromptMethods();
    
    /**
     * Get daily metrics.
     */
    @GetMapping("/metrics/daily")
    DailyMetricsResponse getDailyMetrics(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    );
    
    /**
     * Get metrics for a specific prompt method.
     */
    @GetMapping("/metrics/prompt")
    PromptMetricsResponse getPromptMetrics(
            @RequestParam(value = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(value = "promptId", required = false) String promptId
    );
}