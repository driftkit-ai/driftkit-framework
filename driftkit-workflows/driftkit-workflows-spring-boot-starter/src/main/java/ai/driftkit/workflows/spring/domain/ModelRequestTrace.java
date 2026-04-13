package ai.driftkit.workflows.spring.domain;

import ai.driftkit.common.domain.client.ModelImageResponse;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.domain.ModelTrace;
import ai.driftkit.common.utils.AIUtils;
import ai.driftkit.context.core.util.PromptUtils;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "model_request_traces")
public class ModelRequestTrace {

    @Id
    private String id;
    private String contextId;
    private ContextType contextType;
    private RequestType requestType;
    private long timestamp;
    private String promptTemplate;
    private String systemMessage;
    private String promptId;
    private Map<String, String> variables;
    private List<TraceMessage> messages;
    private String modelId;
    private String responseId;
    private String response;
    private String errorMessage;
    private ModelTrace trace;
    private WorkflowInfo workflowInfo;
    private String purpose;
    private String chatId;
    private Map<String, String> messageProperties;
    
    @SneakyThrows
    public static ModelRequestTrace fromTextResponse(
            String contextId,
            ContextType contextType,
            RequestType requestType,
            String promptTemplate,
            String promptId,
            Map<String, Object> variables,
            String modelId,
            ModelTextResponse response,
            WorkflowInfo workflowInfo,
            String purpose,
            String chatId) {

        ModelRequestTrace trace = ModelRequestTrace.builder()
                .contextId(contextId)
                .contextType(contextType)
                .requestType(requestType)
                .timestamp(System.currentTimeMillis())
                .promptTemplate(promptTemplate)
                .promptId(promptId)
                .variables(PromptUtils.convertVariables(variables))
                .modelId(modelId)
                .workflowInfo(workflowInfo)
                .purpose(purpose)
                .chatId(chatId)
                .build();
        
        if (response != null) {
            trace.setResponseId(response.getId());
            trace.setResponse(response.getResponse());

            if (response.getTrace() != null) {
                trace.setTrace(response.getTrace());

                if (response.getTrace().isHasError()) {
                    trace.setErrorMessage(response.getTrace().getErrorMessage());
                }

                // Ensure cache usage is populated (fallback if TraceableModelClient wasn't used)
                if (response.getTrace().getCacheUsage() == null
                        && response.getUsage() != null
                        && response.getUsage().getCacheUsage() != null) {
                    response.getTrace().setCacheUsage(response.getUsage().getCacheUsage());
                }
            }
        }

        return trace;
    }
    
    public static ModelRequestTrace fromImageResponse(
            String contextId,
            ContextType contextType,
            String promptTemplate,
            String promptId,
            Map<String, Object> variables,
            String modelId,
            ModelImageResponse response,
            WorkflowInfo workflowInfo,
            String purpose,
            String chatId) {
        
        ModelRequestTrace trace = ModelRequestTrace.builder()
                .contextId(contextId)
                .contextType(contextType)
                .requestType(RequestType.TEXT_TO_IMAGE)
                .timestamp(System.currentTimeMillis())
                .promptTemplate(promptTemplate)
                .promptId(promptId)
                .variables(PromptUtils.convertVariables(variables))
                .modelId(modelId)
                .workflowInfo(workflowInfo)
                .purpose(purpose)
                .chatId(chatId)
                .build();
        
        if (response != null) {
            if (response.getTrace() != null) {
                trace.setTrace(response.getTrace());
                
                if (response.getTrace().isHasError()) {
                    trace.setErrorMessage(response.getTrace().getErrorMessage());
                }
            }
        }
        
        return trace;
    }


    public enum RequestType {
        TEXT_TO_TEXT,
        TEXT_TO_IMAGE,
        IMAGE_TO_TEXT
    }

    public enum ContextType {
        WORKFLOW,
        MESSAGE_TASK,
        IMAGE_TASK,
        AGENT,
        CUSTOM
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowInfo {
        private String workflowId;
        private String workflowType;
        private String workflowStep;
    }

    /**
     * Lightweight representation of a message in the conversation context.
     * Stores role and text content without binary image data to keep traces manageable.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TraceMessage {
        private String role;      // system, user, assistant
        private String content;   // text content of the message
        private boolean hasImage; // true if message contained image data (not stored)
    }
}