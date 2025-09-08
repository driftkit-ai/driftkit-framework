package ai.driftkit.workflow.engine.spring.dto;

import ai.driftkit.common.domain.chat.ChatResponse;
import ai.driftkit.workflow.engine.chat.ChatMessageTask;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO classes for Assistant operations.
 */
public class AssistantDtos {
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatInfo {
        private String chatId;
        private Long lastMessageTime;
        private String lastMessage;
        private Map<String, Object> metadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchemaResponse {
        private List<AIFunctionSchema> schemas;
        private Map<String, String> messageIds;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FirstStepSchemaResponse {
        private String workflowId;
        private String stepId;
        private List<AIFunctionSchema> schemas;
        private Map<String, Object> metadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatResponseWithTasks {
        private ChatResponse originalResponse;
        private List<ChatMessageTask> request;
        private List<ChatMessageTask> response;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionInfo {
        private String sessionId;
        private String userId;
        private String workflowId;
        private Map<String, Object> context;
        private long createdAt;
        private long lastActivityAt;
        private boolean active;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionRequest {
        private String workflowId;
        private Map<String, Object> initialContext;
        private Map<String, Object> metadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionContext {
        private Map<String, Object> context;
        private Map<String, Object> metadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowInfo {
        private String id;
        private String name;
        private String description;
        private List<String> capabilities;
        private Map<String, Object> metadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssistantCapabilities {
        private List<String> supportedWorkflows;
        private List<String> supportedModels;
        private boolean supportsStreaming;
        private boolean supportsAsync;
        private Map<String, Object> features;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamEvent {
        private String type; // "message", "delta", "error", "complete"
        private String content;
        private Map<String, Object> metadata;
        private long timestamp;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String id;
        private String role; // "user", "assistant", "system"
        private String content;
        private long timestamp;
        private Map<String, Object> metadata;
    }
}