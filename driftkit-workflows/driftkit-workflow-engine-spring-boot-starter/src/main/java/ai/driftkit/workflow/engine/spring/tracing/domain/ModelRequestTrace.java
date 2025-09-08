package ai.driftkit.workflow.engine.spring.tracing.domain;

import ai.driftkit.common.domain.ModelTrace;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/**
 * Domain entity for storing model request traces.
 * Provides comprehensive tracking of all LLM requests across workflows and agents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "model_request_traces")
public class ModelRequestTrace {

    @Id
    private String id;
    
    // Context information
    private String contextId;  // Can be workflow ID, agent ID, or message ID
    private ContextType contextType;
    private RequestType requestType;
    
    // Timing
    private long timestamp;
    
    // Request details
    private String promptTemplate;
    private String promptId;
    private Map<String, String> variables;
    
    // Model information
    private String modelId;
    
    // Response details
    private String responseId;
    private String response;
    private String errorMessage;
    
    // Trace information from model
    private ModelTrace trace;
    
    // Workflow context
    private WorkflowInfo workflowInfo;
    
    // Additional metadata
    private String purpose;
    private String chatId;
    private String userId;
    
    /**
     * Request type enumeration
     */
    public enum RequestType {
        TEXT_TO_TEXT,
        TEXT_TO_IMAGE,
        IMAGE_TO_TEXT,
        MULTIMODAL
    }

    /**
     * Context type enumeration
     */
    public enum ContextType {
        WORKFLOW,       // Request from workflow
        AGENT,          // Request from LLMAgent
        MESSAGE_TASK,   // Request from message task
        IMAGE_TASK,     // Request from image task
        DIRECT,         // Direct API request
        CUSTOM          // Custom context
    }

    /**
     * Workflow information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowInfo {
        private String workflowId;
        private String workflowType;
        private String workflowStep;
        private String workflowVersion;
    }
}