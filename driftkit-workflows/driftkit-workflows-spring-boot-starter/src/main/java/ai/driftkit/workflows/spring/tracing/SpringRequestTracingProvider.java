package ai.driftkit.workflows.spring.tracing;

import ai.driftkit.common.domain.client.*;
import ai.driftkit.workflows.core.agent.RequestTracingProvider;
import ai.driftkit.workflows.core.agent.RequestTracingRegistry;
import ai.driftkit.workflows.spring.domain.ModelRequestTrace;
import ai.driftkit.workflows.spring.repository.ModelRequestTraceRepository;
import ai.driftkit.common.utils.AIUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Spring-based implementation of RequestTracingProvider that provides full
 * tracing capabilities for LLMAgent requests using ModelRequestTrace repository.
 */
@Slf4j
@Component
public class SpringRequestTracingProvider implements RequestTracingProvider {

    private final ModelRequestTraceRepository traceRepository;
    private final Executor traceExecutor;

    public SpringRequestTracingProvider(ModelRequestTraceRepository traceRepository,
                                      @Qualifier("workflowTraceExecutor") Executor traceExecutor) {
        this.traceRepository = traceRepository;
        this.traceExecutor = traceExecutor;
    }

    @PostConstruct
    public void registerSelf() {
        RequestTracingRegistry.register(this);
        log.info("SpringRequestTracingProvider registered in RequestTracingRegistry");
    }

    @Override
    public void traceTextRequest(ModelTextRequest request, ModelTextResponse response, RequestContext context) {
        if (response == null || response.getTrace() == null || context.getContextId() == null) {
            return;
        }

        try {
            ModelRequestTrace trace = buildTextTrace(request, response, context,
                ModelRequestTrace.RequestType.TEXT_TO_TEXT);
            CompletableFuture.runAsync(() -> saveTrace(trace), traceExecutor);

            log.debug("Traced text request for agent: {} with context type: {}",
                context.getContextId(), context.getContextType());
        } catch (Exception e) {
            log.error("Error tracing text request for agent: {}", context.getContextId(), e);
        }
    }

    @Override
    public void traceImageRequest(ModelImageRequest request, ModelImageResponse response, RequestContext context) {
        if (response == null || response.getTrace() == null || context.getContextId() == null) {
            return;
        }

        try {
            ModelRequestTrace trace = buildImageTrace(request, response, context);
            CompletableFuture.runAsync(() -> saveTrace(trace), traceExecutor);

            log.debug("Traced image generation request for agent: {} with context type: {}",
                context.getContextId(), context.getContextType());
        } catch (Exception e) {
            log.error("Error tracing image request for agent: {}", context.getContextId(), e);
        }
    }

    @Override
    public void traceImageToTextRequest(ModelTextRequest request, ModelTextResponse response, RequestContext context) {
        if (response == null || response.getTrace() == null || context.getContextId() == null) {
            return;
        }

        try {
            ModelRequestTrace trace = buildTextTrace(request, response, context,
                ModelRequestTrace.RequestType.IMAGE_TO_TEXT);
            CompletableFuture.runAsync(() -> saveTrace(trace), traceExecutor);

            log.debug("Traced image-to-text request for agent: {} with context type: {}",
                context.getContextId(), context.getContextType());
        } catch (Exception e) {
            log.error("Error tracing image-to-text request for agent: {}", context.getContextId(), e);
        }
    }

    private ModelRequestTrace buildTextTrace(ModelTextRequest request, ModelTextResponse response,
                                            RequestContext context, ModelRequestTrace.RequestType requestType) {
        ModelRequestTrace.ModelRequestTraceBuilder builder = ModelRequestTrace.builder()
            .id(AIUtils.generateId())
            .requestType(requestType)
            .contextType(ModelRequestTrace.ContextType.AGENT)
            .contextId(context.getContextId())
            .timestamp(System.currentTimeMillis())
            .promptTemplate(extractPromptTemplate(request))
            .systemMessage(extractSystemMessage(request, context))
            .promptId(context.getPromptId())
            .variables(convertVariables(context.getVariables()))
            .messages(convertMessages(request, context))
            .modelId(response.getTrace() != null ? response.getTrace().getModel() : null)
            .responseId(response.getId())
            .response(response.getResponse())
            .trace(response.getTrace())
            .chatId(context.getChatId())
            .purpose(context.getPurpose())
            .messageProperties(context.getMessageProperties());

        // Add workflow information if available
        if (context.getWorkflowId() != null) {
            builder.workflowInfo(ModelRequestTrace.WorkflowInfo.builder()
                .workflowId(context.getWorkflowId())
                .workflowType(context.getWorkflowType())
                .workflowStep(context.getWorkflowStep())
                .build());
        }

        return builder.build();
    }

    private ModelRequestTrace buildImageTrace(ModelImageRequest request, ModelImageResponse response,
                                             RequestContext context) {
        ModelRequestTrace.ModelRequestTraceBuilder builder = ModelRequestTrace.builder()
            .id(AIUtils.generateId())
            .requestType(ModelRequestTrace.RequestType.TEXT_TO_IMAGE)
            .contextType(ModelRequestTrace.ContextType.AGENT)
            .contextId(context.getContextId())
            .timestamp(System.currentTimeMillis())
            .promptTemplate(request.getPrompt())
            .systemMessage(context.getSystemMessage())
            .promptId(context.getPromptId())
            .variables(convertVariables(context.getVariables()))
            .modelId(response.getTrace() != null ? response.getTrace().getModel() : null)
            .trace(response.getTrace())
            .chatId(context.getChatId())
            .purpose(context.getPurpose())
            .messageProperties(context.getMessageProperties());

        // Add workflow information if available
        if (context.getWorkflowId() != null) {
            builder.workflowInfo(ModelRequestTrace.WorkflowInfo.builder()
                .workflowId(context.getWorkflowId())
                .workflowType(context.getWorkflowType())
                .workflowStep(context.getWorkflowStep())
                .build());
        }

        return builder.build();
    }

    private Map<String, String> convertVariables(Map<String, Object> variables) {
        if (variables == null) {
            return null;
        }

        return variables.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue() != null ? entry.getValue().toString() : null
            ));
    }

    /**
     * Extract text content from ModelContentMessage content elements.
     * Handles both simple text and multimodal (text + image) content.
     */
    private String extractTextFromContent(ModelContentMessage message) {
        if (message == null || message.getContent() == null) {
            return null;
        }

        List<ModelContentMessage.ModelContentElement> elements = message.getContent();
        StringBuilder sb = new StringBuilder();
        for (ModelContentMessage.ModelContentElement element : elements) {
            if (element.getText() != null) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(element.getText());
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Check if a message contains image data.
     */
    private boolean hasImageContent(ModelContentMessage message) {
        if (message == null || message.getContent() == null) {
            return false;
        }
        return message.getContent().stream()
            .anyMatch(e -> e.getImage() != null || e.getType() == ModelTextRequest.MessageType.image
                        || e.getType() == ModelTextRequest.MessageType.image_url);
    }

    /**
     * Extract the last user message text as prompt template.
     */
    private String extractPromptTemplate(ModelTextRequest request) {
        if (request.getMessages() == null || CollectionUtils.isEmpty(request.getMessages())) {
            return null;
        }

        return request.getMessages().stream()
            .filter(msg -> msg.getRole() == Role.user)
            .reduce((first, second) -> second)
            .map(this::extractTextFromContent)
            .orElse(null);
    }

    /**
     * Extract system message from request messages or from context.
     * Prefers context.systemMessage (the original text), falls back to extracting from request.
     */
    private String extractSystemMessage(ModelTextRequest request, RequestContext context) {
        if (context != null && StringUtils.isNotBlank(context.getSystemMessage())) {
            return context.getSystemMessage();
        }

        if (request.getMessages() == null || CollectionUtils.isEmpty(request.getMessages())) {
            return null;
        }

        return request.getMessages().stream()
            .filter(msg -> msg.getRole() == Role.system)
            .findFirst()
            .map(this::extractTextFromContent)
            .orElse(null);
    }

    /**
     * Convert all request messages to lightweight TraceMessage objects.
     * Preserves the full conversation context (system, history, user message)
     * without storing binary image data.
     */
    private List<ModelRequestTrace.TraceMessage> convertMessages(ModelTextRequest request, RequestContext context) {
        List<ModelContentMessage> sourceMessages = context != null && context.getMessages() != null
            ? context.getMessages()
            : request.getMessages();

        if (sourceMessages == null || sourceMessages.isEmpty()) {
            return null;
        }

        List<ModelRequestTrace.TraceMessage> traceMessages = new ArrayList<>();
        for (ModelContentMessage msg : sourceMessages) {
            traceMessages.add(ModelRequestTrace.TraceMessage.builder()
                .role(msg.getRole() != null ? msg.getRole().name() : "unknown")
                .content(extractTextFromContent(msg))
                .hasImage(hasImageContent(msg))
                .build());
        }
        return traceMessages;
    }

    private void saveTrace(ModelRequestTrace trace) {
        try {
            traceRepository.save(trace);
            log.trace("Saved trace: {} for agent: {}", trace.getId(), trace.getContextId());
        } catch (Exception e) {
            log.error("Failed to save trace: {} for agent: {}", trace.getId(), trace.getContextId(), e);
        }
    }
}
