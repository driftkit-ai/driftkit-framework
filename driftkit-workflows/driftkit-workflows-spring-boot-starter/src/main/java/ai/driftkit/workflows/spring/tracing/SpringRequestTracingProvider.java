package ai.driftkit.workflows.spring.tracing;

import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.domain.client.ModelImageRequest;
import ai.driftkit.common.domain.client.ModelImageResponse;
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.workflows.core.agent.RequestTracingProvider;
import ai.driftkit.workflows.core.agent.RequestTracingRegistry;
import ai.driftkit.workflows.spring.domain.ModelRequestTrace;
import ai.driftkit.workflows.spring.repository.ModelRequestTraceRepository;
import ai.driftkit.common.utils.AIUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

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
@RequiredArgsConstructor
public class SpringRequestTracingProvider implements RequestTracingProvider {
    
    private final ModelRequestTraceRepository traceRepository;
    private final Executor traceExecutor;
    
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
            ModelRequestTrace trace = buildTextTrace(request, response, context);
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
            ModelRequestTrace trace = buildImageToTextTrace(request, response, context);
            CompletableFuture.runAsync(() -> saveTrace(trace), traceExecutor);
            
            log.debug("Traced image-to-text request for agent: {} with context type: {}", 
                context.getContextId(), context.getContextType());
        } catch (Exception e) {
            log.error("Error tracing image-to-text request for agent: {}", context.getContextId(), e);
        }
    }
    
    private ModelRequestTrace buildTextTrace(ModelTextRequest request, ModelTextResponse response, RequestContext context) {
        return ModelRequestTrace.builder()
            .id(AIUtils.generateId()) // Unique trace ID
            .requestType(ModelRequestTrace.RequestType.TEXT_TO_TEXT)
            .contextType(ModelRequestTrace.ContextType.AGENT)
            .contextId(context.getContextId()) // Agent ID
            .timestamp(System.currentTimeMillis())
            .promptTemplate(extractPromptTemplate(request))
            .promptId(context.getPromptId())
            .variables(convertVariables(context.getVariables()))
            .modelId(response.getTrace() != null ? response.getTrace().getModel() : null)
            .responseId(response.getId())
            .response(response.getResponse())
            .trace(response.getTrace())
            .chatId(context.getChatId())
            .purpose("AGENT_" + context.getContextType())
            .build();
    }
    
    private ModelRequestTrace buildImageTrace(ModelImageRequest request, ModelImageResponse response, RequestContext context) {
        return ModelRequestTrace.builder()
            .id(AIUtils.generateId()) // Unique trace ID
            .requestType(ModelRequestTrace.RequestType.TEXT_TO_IMAGE)
            .contextType(ModelRequestTrace.ContextType.AGENT)
            .contextId(context.getContextId()) // Agent ID
            .timestamp(System.currentTimeMillis())
            .promptTemplate(request.getPrompt())
            .promptId(context.getPromptId())
            .variables(convertVariables(context.getVariables()))
            .modelId(response.getTrace() != null ? response.getTrace().getModel() : null)
            .trace(response.getTrace())
            .chatId(context.getChatId())
            .purpose("AGENT_" + context.getContextType())
            .build();
    }
    
    private ModelRequestTrace buildImageToTextTrace(ModelTextRequest request, ModelTextResponse response, RequestContext context) {
        return ModelRequestTrace.builder()
            .id(AIUtils.generateId()) // Unique trace ID
            .requestType(ModelRequestTrace.RequestType.IMAGE_TO_TEXT)
            .contextType(ModelRequestTrace.ContextType.AGENT)
            .contextId(context.getContextId()) // Agent ID
            .timestamp(System.currentTimeMillis())
            .promptTemplate(extractPromptTemplate(request))
            .promptId(context.getPromptId())
            .variables(convertVariables(context.getVariables()))
            .modelId(response.getTrace() != null ? response.getTrace().getModel() : null)
            .responseId(response.getId())
            .response(response.getResponse())
            .trace(response.getTrace())
            .chatId(context.getChatId())
            .purpose("AGENT_" + context.getContextType())
            .build();
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
    
    private String extractPromptTemplate(ModelTextRequest request) {
        if (request.getMessages() == null || CollectionUtils.isEmpty(request.getMessages())) {
            return null;
        }
        
        // Find the last user message as prompt template
        return request.getMessages().stream()
            .filter(msg -> msg.getRole() == Role.user)
            .reduce((first, second) -> second) // Get the last user message
            .map(msg -> msg.getContent() != null ? msg.getContent().toString() : null)
            .orElse(null);
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