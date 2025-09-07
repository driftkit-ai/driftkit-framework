package ai.driftkit.workflow.engine.monitoring;

import ai.driftkit.workflow.engine.core.RetryMetrics;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exports retry metrics in various formats for monitoring systems.
 */
@Slf4j
public class RetryMetricsExporter {
    
    private final RetryMetrics metrics;
    
    public RetryMetricsExporter(RetryMetrics metrics) {
        this.metrics = metrics;
    }
    
    /**
     * Exports metrics as key-value pairs for logging or metrics systems.
     */
    public MetricsSnapshot exportSnapshot() {
        RetryMetrics.GlobalMetrics global = metrics.getGlobalMetrics();
        
        return MetricsSnapshot.builder()
            .timestamp(Instant.now())
            .totalRetryAttempts(global.getTotalAttempts())
            .totalRetrySuccesses(global.getTotalSuccesses())
            .totalRetryFailures(global.getTotalFailures())
            .totalRetryExhausted(global.getTotalExhausted())
            .totalRetryAborted(0L) // TODO: Add aborted tracking to GlobalMetrics
            .globalSuccessRate(global.getSuccessRate())
            .stepMetrics(exportStepMetrics())
            .build();
    }
    
    /**
     * Exports metrics in Prometheus format.
     */
    public String exportPrometheus() {
        MetricsSnapshot snapshot = exportSnapshot();
        StringBuilder sb = new StringBuilder();
        
        // Global metrics
        sb.append("# HELP retry_attempts_total Total number of retry attempts\n");
        sb.append("# TYPE retry_attempts_total counter\n");
        sb.append("retry_attempts_total ").append(snapshot.getTotalRetryAttempts()).append("\n\n");
        
        sb.append("# HELP retry_successes_total Total number of successful retries\n");
        sb.append("# TYPE retry_successes_total counter\n");
        sb.append("retry_successes_total ").append(snapshot.getTotalRetrySuccesses()).append("\n\n");
        
        sb.append("# HELP retry_failures_total Total number of failed retries\n");
        sb.append("# TYPE retry_failures_total counter\n");
        sb.append("retry_failures_total ").append(snapshot.getTotalRetryFailures()).append("\n\n");
        
        sb.append("# HELP retry_exhausted_total Total number of exhausted retries\n");
        sb.append("# TYPE retry_exhausted_total counter\n");
        sb.append("retry_exhausted_total ").append(snapshot.getTotalRetryExhausted()).append("\n\n");
        
        sb.append("# HELP retry_success_rate Global retry success rate\n");
        sb.append("# TYPE retry_success_rate gauge\n");
        sb.append("retry_success_rate ").append(snapshot.getGlobalSuccessRate()).append("\n\n");
        
        // Per-step metrics
        sb.append("# HELP retry_step_attempts Retry attempts per step\n");
        sb.append("# TYPE retry_step_attempts counter\n");
        for (Map.Entry<String, StepMetricsSnapshot> entry : snapshot.getStepMetrics().entrySet()) {
            String stepId = entry.getKey();
            StepMetricsSnapshot step = entry.getValue();
            sb.append("retry_step_attempts{step=\"").append(stepId).append("\"} ")
              .append(step.getTotalAttempts()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Exports metrics as JSON-friendly map.
     */
    public Map<String, Object> exportJson() {
        MetricsSnapshot snapshot = exportSnapshot();
        
        return Map.of(
            "timestamp", snapshot.getTimestamp().toString(),
            "global", Map.of(
                "totalAttempts", snapshot.getTotalRetryAttempts(),
                "totalSuccesses", snapshot.getTotalRetrySuccesses(),
                "totalFailures", snapshot.getTotalRetryFailures(),
                "totalExhausted", snapshot.getTotalRetryExhausted(),
                "totalAborted", snapshot.getTotalRetryAborted(),
                "successRate", snapshot.getGlobalSuccessRate()
            ),
            "steps", snapshot.getStepMetrics().entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> Map.of(
                        "totalAttempts", e.getValue().getTotalAttempts(),
                        "successCount", e.getValue().getSuccessCount(),
                        "failureCount", e.getValue().getFailureCount(),
                        "exhaustedCount", e.getValue().getExhaustedCount(),
                        "successRate", e.getValue().getSuccessRate(),
                        "avgDuration", e.getValue().getAverageDuration()
                    )
                ))
        );
    }
    
    /**
     * Logs current metrics at INFO level.
     */
    public void logMetrics() {
        MetricsSnapshot snapshot = exportSnapshot();
        
        log.info("Retry Metrics Summary: attempts={}, successes={}, failures={}, exhausted={}, successRate={}%",
            snapshot.getTotalRetryAttempts(),
            snapshot.getTotalRetrySuccesses(),
            snapshot.getTotalRetryFailures(),
            snapshot.getTotalRetryExhausted(),
            String.format("%.2f", snapshot.getGlobalSuccessRate()));
        
        // Log top failing steps
        List<Map.Entry<String, StepMetricsSnapshot>> topFailures = snapshot.getStepMetrics().entrySet().stream()
            .filter(e -> e.getValue().getFailureCount() > 0)
            .sorted((a, b) -> Long.compare(b.getValue().getFailureCount(), a.getValue().getFailureCount()))
            .limit(5)
            .collect(Collectors.toList());
        
        if (!topFailures.isEmpty()) {
            log.info("Top failing steps:");
            for (Map.Entry<String, StepMetricsSnapshot> entry : topFailures) {
                log.info("  - {}: {} failures, {}% success rate", 
                    entry.getKey(), 
                    entry.getValue().getFailureCount(),
                    String.format("%.2f", entry.getValue().getSuccessRate()));
            }
        }
    }
    
    private Map<String, StepMetricsSnapshot> exportStepMetrics() {
        return metrics.getAllStepMetrics().entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> {
                    RetryMetrics.StepMetrics step = e.getValue();
                    return StepMetricsSnapshot.builder()
                        .totalAttempts(step.getTotalAttempts())
                        .successCount(step.getSuccessCount().get())
                        .failureCount(step.getFailureCount().get())
                        .exhaustedCount(step.getExhaustedCount().get())
                        .abortedCount(step.getAbortedCount())
                        .successRate(step.getSuccessRate())
                        .averageDuration(step.getAverageDuration())
                        .build();
                }
            ));
    }
    
    @Getter
    @Builder
    public static class MetricsSnapshot {
        private final Instant timestamp;
        private final long totalRetryAttempts;
        private final long totalRetrySuccesses;
        private final long totalRetryFailures;
        private final long totalRetryExhausted;
        private final long totalRetryAborted;
        private final double globalSuccessRate;
        private final Map<String, StepMetricsSnapshot> stepMetrics;
    }
    
    @Getter
    @Builder
    public static class StepMetricsSnapshot {
        private final long totalAttempts;
        private final long successCount;
        private final long failureCount;
        private final long exhaustedCount;
        private final long abortedCount;
        private final double successRate;
        private final double averageDuration;
    }
}