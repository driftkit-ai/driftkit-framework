package ai.driftkit.workflow.controllers.controller;

import ai.driftkit.common.domain.RestResponse;
import ai.driftkit.workflow.controllers.service.WorkflowAnalyticsService;
import ai.driftkit.workflow.engine.spring.dto.AnalyticsDtos.DailyMetricsResponse;
import ai.driftkit.workflow.engine.spring.dto.AnalyticsDtos.PromptMetricsResponse;
import ai.driftkit.workflow.engine.spring.dto.AnalyticsDtos.TaskVariables;
import ai.driftkit.workflow.engine.spring.tracing.domain.ModelRequestTrace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for analytics and tracing.
 * Provides endpoints for querying model request traces and analytics metrics.
 */
@Slf4j
@Controller("workflowAnalyticsController")
@RequestMapping(path = "/data/v1.0/analytics")
@ConditionalOnWebApplication
@ConditionalOnProperty(
    prefix = "driftkit.workflow.tracing",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class AnalyticsController {

    @Autowired
    private WorkflowAnalyticsService analyticsService;
    
    /**
     * Get model request traces within a time range
     */
    @GetMapping("/traces")
    public @ResponseBody RestResponse<Page<ModelRequestTrace>> getTraces(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String promptId,
            @RequestParam(required = false) String excludePurpose,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Page<ModelRequestTrace> traces = analyticsService.getTraces(startTime, endTime, promptId, excludePurpose, page, size);
        return new RestResponse<>(true, traces);
    }
    
    /**
     * Get traces by context ID
     */
    @GetMapping("/traces/{contextId}")
    public @ResponseBody RestResponse<List<ModelRequestTrace>> getTracesByContextId(
            @PathVariable String contextId
    ) {
        List<ModelRequestTrace> traces = analyticsService.getTracesByContextId(contextId);
        return new RestResponse<>(true, traces);
    }
    
    /**
     * Get daily metrics for the dashboard
     */
    @GetMapping("/metrics/daily")
    public @ResponseBody RestResponse<DailyMetricsResponse> getDailyMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        DailyMetricsResponse metrics = analyticsService.getDailyMetrics(startDate, endDate);
        return new RestResponse<>(true, metrics);
    }
    
    /**
     * Get available prompt methods for analytics
     */
    @GetMapping("/prompt-methods")
    public @ResponseBody RestResponse<List<String>> getAvailablePromptMethods() {
        List<String> methods = analyticsService.getAvailablePromptMethods();
        return new RestResponse<>(true, methods);
    }
    
    /**
     * Get message tasks by context IDs
     */
    @GetMapping("/message-tasks")
    public @ResponseBody RestResponse<List<TaskVariables>> getMessageTasksByContextIds(
            @RequestParam String contextIds
    ) {
        List<String> ids = List.of(contextIds.split(","));
        List<TaskVariables> tasks = analyticsService.getMessageTasksByContextIds(ids);
        return new RestResponse<>(true, tasks);
    }
    
    /**
     * Get metrics for a specific prompt method
     */
    @GetMapping("/metrics/prompt")
    public @ResponseBody RestResponse<PromptMetricsResponse> getPromptMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam String promptId
    ) {
        PromptMetricsResponse metrics = analyticsService.getPromptMetrics(startTime, endTime, promptId);
        return new RestResponse<>(true, metrics);
    }
}