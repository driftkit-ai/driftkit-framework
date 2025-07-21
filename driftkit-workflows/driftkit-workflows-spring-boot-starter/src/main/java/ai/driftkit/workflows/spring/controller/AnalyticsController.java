package ai.driftkit.workflows.spring.controller;

import ai.driftkit.common.domain.RestResponse;
import ai.driftkit.workflows.spring.domain.ModelRequestTrace;
import ai.driftkit.workflows.spring.service.AnalyticsService;
import ai.driftkit.workflows.spring.service.AnalyticsService.DailyMetricsResponse;
import ai.driftkit.workflows.spring.service.AnalyticsService.PromptMetricsResponse;
import ai.driftkit.workflows.spring.service.AnalyticsService.TaskVariables;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Controller
@RequestMapping(path = "/data/v1.0/analytics")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;
    
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