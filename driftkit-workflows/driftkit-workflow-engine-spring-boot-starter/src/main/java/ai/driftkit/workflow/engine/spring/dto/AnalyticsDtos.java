package ai.driftkit.workflow.engine.spring.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO classes for Analytics operations.
 */
public class AnalyticsDtos {
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TaskVariables {
        private String messageId;
        private String contextId; // same as messageId for use in UI
        private String message;
        private String result;
        private String modelId;
        private long createdTime;
        private long responseTime;
        private List<String> promptIds;
        private Map<String, Object> variables;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LatencyPercentiles {
        private Long p25;
        private Long p50;
        private Long p75;
        private Long p90;

        public static LatencyPercentiles fromMap(Map<String, Long> map) {
            if (map == null) {
                return null;
            }
            return LatencyPercentiles.builder()
                    .p25(map.get("p25"))
                    .p50(map.get("p50"))
                    .p75(map.get("p75"))
                    .p90(map.get("p90"))
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TokensByCategory {
        private Map<String, Integer> promptTokens;
        private Map<String, Integer> completionTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DailyMetricsResponse {
        private long totalTasks;
        private int totalPromptTokens;
        private int totalCompletionTokens;
        private LatencyPercentiles latencyPercentiles;
        private Map<String, Long> tasksByModel;
        private Map<String, Long> tasksByPromptMethod;
        private TokensByCategory tokensByPromptMethod;
        private TokensByCategory tokensByPromptMethodModel;
        private Map<String, LatencyPercentiles> latencyByPromptMethod;
        private Map<String, Long> successByPromptMethod;
        private Map<String, Long> errorsByPromptMethod;
        private Map<String, Double> successRateByPromptMethod;
        private long successCount;
        private long errorCount;
        private double successRate;
        private long timestamp;
        private Map<String, LatencyPercentiles> latencyByPromptMethodModel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PromptMetricsResponse {
        private long totalTraces;
        private int totalPromptTokens;
        private int totalCompletionTokens;
        private int totalTokens;
        private LatencyPercentiles latencyPercentiles;
        private Map<String, Long> tracesByModel;
        private TokensByCategory tokensByModel;
        private Map<String, LatencyPercentiles> latencyByModel;
        private long successCount;
        private long errorCount;
        private double successRate;
        private Map<String, Long> dailyCounts;
    }
}