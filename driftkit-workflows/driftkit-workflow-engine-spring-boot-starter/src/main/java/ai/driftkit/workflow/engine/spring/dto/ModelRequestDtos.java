package ai.driftkit.workflow.engine.spring.dto;

import ai.driftkit.common.domain.client.ResponseFormat;
import ai.driftkit.workflow.engine.spring.tracing.domain.AsyncTaskEntity.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO classes for Model Request operations.
 */
public class ModelRequestDtos {
    
    /**
     * Text request for synchronous model operations.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextRequest {
        private String text;
        private String systemMessage;
        private String chatId;
        private String modelId;
        private Double temperature;
        private Map<String, Object> variables;
        private String workflow;
        private ResponseFormat responseFormat;
        private List<String> images;
    }
    
    /**
     * Async task response.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AsyncTaskResponse {
        private String taskId;
        private TaskStatus status;
    }
    
    /**
     * Task rating request.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskRating {
        private Integer grade; // 1-5 rating scale
        private String comment;
    }
}