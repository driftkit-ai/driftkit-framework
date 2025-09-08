package ai.driftkit.workflow.engine.spring.tracing.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/**
 * Entity for storing async task execution status and results.
 * Provides persistent storage for async LLM operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "async_tasks")
public class AsyncTaskEntity {
    
    @Id
    private String taskId;
    
    @Indexed
    private String chatId;
    
    @Indexed
    private String userId;
    
    @Indexed
    private TaskStatus status;
    
    private TaskType taskType;
    
    // Request information
    private String requestBody; // JSON serialized request
    private String workflowId;
    private String promptId;
    private Map<String, Object> variables;
    
    // Execution information
    private Long createdAt;
    private Long startedAt;
    private Long completedAt;
    private Long executionTimeMs;
    
    // Result information
    private String result; // JSON serialized AgentResponse
    private String errorMessage;
    private String errorStackTrace;
    
    // Model information
    private String modelId;
    private Double temperature;
    
    // Metadata
    private Map<String, String> metadata;
    
    /**
     * Task execution status
     */
    public enum TaskStatus {
        PENDING,    // Task created but not started
        RUNNING,    // Task is being executed
        COMPLETED,  // Task completed successfully
        FAILED,     // Task failed with error
        TIMEOUT,    // Task timed out
        CANCELLED   // Task was cancelled
    }
    
    /**
     * Task type
     */
    public enum TaskType {
        PROMPT_REQUEST,  // Request with prompt ID
        TEXT_REQUEST,    // Direct text request
        WORKFLOW        // Workflow execution
    }
}