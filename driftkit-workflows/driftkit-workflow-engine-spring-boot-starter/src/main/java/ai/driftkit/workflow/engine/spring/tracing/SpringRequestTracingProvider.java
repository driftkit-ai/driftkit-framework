package ai.driftkit.workflow.engine.spring.tracing;

import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.utils.AIUtils;
import ai.driftkit.workflow.engine.agent.RequestTracingProvider;
import ai.driftkit.workflow.engine.spring.tracing.domain.ModelRequestTrace;
import ai.driftkit.workflow.engine.spring.tracing.repository.CoreModelRequestTraceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Spring implementation of RequestTracingProvider.
 * Provides comprehensive tracing of all LLM requests with MongoDB persistence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(CoreModelRequestTraceRepository.class)
public class SpringRequestTracingProvider implements RequestTracingProvider {
    
    private final CoreModelRequestTraceRepository traceRepository;
    private final Executor traceExecutor;
    
    @Override
    public void traceTextRequest(ModelTextRequest request, ModelTextResponse response, RequestContext context) {
        if (!shouldTrace(response, context)) {
            return;
        }
        
        try {
            ModelRequestTrace trace = buildTextTrace(request, response, context, 
                ModelRequestTrace.RequestType.TEXT_TO_TEXT);
            saveTraceAsync(trace);
            
            log.debug("Traced text request for context: {} with type: {}", 
                context.getContextId(), context.getContextType());
        } catch (Exception e) {
            log.error("Error tracing text request for context: {}", context.getContextId(), e);
        }
    }
    
    @Override
    public void traceImageRequest(ModelImageRequest request, ModelImageResponse response, RequestContext context) {
        if (!shouldTrace(response, context)) {
            return;
        }
        
        try {
            ModelRequestTrace trace = buildImageTrace(request, response, context);
            saveTraceAsync(trace);
            
            log.debug("Traced image generation request for context: {} with type: {}", 
                context.getContextId(), context.getContextType());
        } catch (Exception e) {
            log.error("Error tracing image request for context: {}", context.getContextId(), e);
        }
    }
    
    @Override
    public void traceImageToTextRequest(ModelTextRequest request, ModelTextResponse response, RequestContext context) {
        if (!shouldTrace(response, context)) {
            return;
        }
        
        try {
            ModelRequestTrace trace = buildTextTrace(request, response, context,
                ModelRequestTrace.RequestType.IMAGE_TO_TEXT);
            saveTraceAsync(trace);
            
            log.debug("Traced image-to-text request for context: {} with type: {}", 
                context.getContextId(), context.getContextType());
        } catch (Exception e) {
            log.error("Error tracing image-to-text request for context: {}", context.getContextId(), e);
        }
    }
    
    private boolean shouldTrace(Object response, RequestContext context) {
        if (response == null || context == null || context.getContextId() == null) {
            return false;
        }
        
        // Check if response has trace information
        if (response instanceof ModelTextResponse textResponse) {
            return textResponse.getTrace() != null;
        } else if (response instanceof ModelImageResponse imageResponse) {
            return imageResponse.getTrace() != null;
        }
        
        return false;
    }
    
    private ModelRequestTrace buildTextTrace(ModelTextRequest request, ModelTextResponse response, 
                                            RequestContext context, ModelRequestTrace.RequestType requestType) {
        ModelRequestTrace.ModelRequestTraceBuilder builder = ModelRequestTrace.builder()
            .id(AIUtils.generateId())
            .requestType(requestType)
            .contextType(getContextType(context))
            .contextId(context.getContextId())
            .timestamp(System.currentTimeMillis())
            .promptTemplate(extractPromptTemplate(request))
            .promptId(context.getPromptId())
            .variables(convertVariables(context.getVariables()))
            .chatId(context.getChatId());
        
        // Add response information
        if (response != null) {
            builder.responseId(response.getId())
                   .response(response.getResponse());
            
            if (response.getTrace() != null) {
                builder.trace(response.getTrace())
                       .modelId(response.getTrace().getModel());
                
                if (response.getTrace().isHasError()) {
                    builder.errorMessage(response.getTrace().getErrorMessage());
                }
            }
        }
        
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
            .contextType(getContextType(context))
            .contextId(context.getContextId())
            .timestamp(System.currentTimeMillis())
            .promptTemplate(request.getPrompt())
            .promptId(context.getPromptId())
            .variables(convertVariables(context.getVariables()))
            .chatId(context.getChatId());
        
        // Add response information
        if (response != null && response.getTrace() != null) {
            builder.trace(response.getTrace())
                   .modelId(response.getTrace().getModel());
            
            if (response.getTrace().isHasError()) {
                builder.errorMessage(response.getTrace().getErrorMessage());
            }
        }
        
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
    
    private ModelRequestTrace.ContextType getContextType(RequestContext context) {
        if (context.getContextType() == null) {
            return ModelRequestTrace.ContextType.CUSTOM;
        }
        
        return switch (context.getContextType().toLowerCase()) {
            case "workflow" -> ModelRequestTrace.ContextType.WORKFLOW;
            case "agent" -> ModelRequestTrace.ContextType.AGENT;
            case "message_task" -> ModelRequestTrace.ContextType.MESSAGE_TASK;
            case "image_task" -> ModelRequestTrace.ContextType.IMAGE_TASK;
            case "direct" -> ModelRequestTrace.ContextType.DIRECT;
            default -> ModelRequestTrace.ContextType.CUSTOM;
        };
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
            .map(msg -> {
                Object content = msg.getContent();
                if (content instanceof String) {
                    return (String) content;
                } else if (content instanceof List) {
                    // Handle multimodal content
                    return content.toString();
                }
                return null;
            })
            .orElse(null);
    }
    
    private void saveTraceAsync(ModelRequestTrace trace) {
        CompletableFuture.runAsync(() -> saveTrace(trace), traceExecutor);
    }
    
    private void saveTrace(ModelRequestTrace trace) {
        try {
            traceRepository.save(trace);
            log.trace("Saved trace: {} for context: {}", trace.getId(), trace.getContextId());
        } catch (Exception e) {
            log.error("Failed to save trace: {} for context: {}", trace.getId(), trace.getContextId(), e);
        }
    }
}