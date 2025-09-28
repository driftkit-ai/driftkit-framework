package ai.driftkit.context.springai;

import ai.driftkit.common.domain.ModelTrace;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.workflows.core.agent.RequestTracingProvider;
import ai.driftkit.workflows.core.agent.RequestTracingRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spring AI Advisor that provides DriftKit tracing for ChatClient calls.
 * This advisor intercepts Spring AI requests and responses to provide
 * detailed tracing information compatible with DriftKit's monitoring system.
 * 
 * Usage example:
 * <pre>
 * @Configuration
 * public class ChatClientConfig {
 *     
 *     @Bean
 *     public ChatClient chatClient(ChatClient.Builder builder, 
 *                                  DriftKitTracingAdvisor tracingAdvisor) {
 *         return builder
 *             .defaultAdvisors(tracingAdvisor)
 *             .build();
 *     }
 * }
 * </pre>
 */
@Slf4j
public class DriftKitTracingAdvisor implements CallAdvisor, StreamAdvisor {
    
    private final RequestTracingProvider tracingProvider;
    private final String applicationName;
    
    // Thread-local storage for request timing
    private static final ThreadLocal<RequestContext> requestContextHolder = new ThreadLocal<>();
    
    public DriftKitTracingAdvisor() {
        this(RequestTracingRegistry.getInstance(), "spring-ai-app");
    }
    
    public DriftKitTracingAdvisor(RequestTracingProvider tracingProvider) {
        this(tracingProvider, "spring-ai-app");
    }
    
    public DriftKitTracingAdvisor(RequestTracingProvider tracingProvider, String applicationName) {
        this.tracingProvider = tracingProvider;
        this.applicationName = applicationName;
    }
    
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // Create request context for tracing
        RequestContext reqContext = getRequestContext(request);

        // Store in thread-local for response handling
        requestContextHolder.set(reqContext);
        
        // Log request start
        log.debug("Starting Spring AI request: {}", reqContext.requestId);
        
        try {
            // Call the next advisor in chain
            ChatClientResponse response = chain.nextCall(request);
            
            // Calculate execution time
            long executionTimeMs = Duration.between(reqContext.startTime, Instant.now()).toMillis();
            
            // Build DriftKit trace
            ModelTrace trace = buildTrace(reqContext, response.chatResponse(), executionTimeMs);
            
            // Build tracing context
            RequestTracingProvider.RequestContext traceContext = RequestTracingProvider.RequestContext.builder()
                .contextId(reqContext.requestId)
                .contextType("SPRING_AI_CHAT")
                .variables(reqContext.advisorParams)
                .build();
            
            // Convert Spring AI request/response to DriftKit format for tracing
            if (tracingProvider != null) {
                // Build ModelTextRequest representation
                ModelTextRequest modelRequest = buildModelTextRequest(reqContext);
                
                // Build ModelTextResponse representation
                ModelTextResponse modelResponse = buildModelTextResponse(response.chatResponse(), trace);
                
                // Trace using existing interface
                tracingProvider.traceTextRequest(modelRequest, modelResponse, traceContext);
                log.debug("Traced Spring AI request: {} in {}ms", reqContext.requestId, executionTimeMs);
            }

            return response;
        } catch (Exception e) {
            log.error("Error tracing Spring AI response", e);
            throw new RuntimeException("Error in DriftKit tracing advisor", e);
        } finally {
            // Clean up thread-local
            requestContextHolder.remove();
        }
    }

    private static @NotNull RequestContext getRequestContext(ChatClientRequest request) {
        RequestContext reqContext = new RequestContext();
        reqContext.requestId = UUID.randomUUID().toString();
        reqContext.startTime = Instant.now();
        reqContext.userMessage = request.prompt().getUserMessage().getText();
        reqContext.systemMessage = request.prompt().getSystemMessage().getText();
        reqContext.advisorParams = request.context();
        reqContext.chatOptions = request.prompt().getOptions();
        return reqContext;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        // Create request context for tracing
        RequestContext reqContext = getRequestContext(request);
        
        log.debug("Starting Spring AI streaming request: {}", reqContext.requestId);
        
        // For streaming, we'll trace the initial request and log completion
        return Mono.just(request)
            .publishOn(Schedulers.boundedElastic())
            .map(req -> {
                // Log request details
                if (tracingProvider != null) {
                    try {
                        ModelTextRequest modelRequest = buildModelTextRequest(reqContext);
                        log.debug("Tracing streaming request: {}", reqContext.requestId);
                    } catch (Exception e) {
                        log.error("Error preparing trace for streaming request", e);
                    }
                }
                return req;
            })
            .flatMapMany(req -> chain.nextStream(req))
            .doOnComplete(() -> {
                long executionTimeMs = Duration.between(reqContext.startTime, Instant.now()).toMillis();
                log.debug("Completed Spring AI streaming request: {} in {}ms", 
                    reqContext.requestId, executionTimeMs);
            })
            .doOnError(error -> {
                log.error("Error in Spring AI streaming request: {}", reqContext.requestId, error);
            });
    }
    
    @Override
    public String getName() {
        return "DriftKitTracingAdvisor";
    }
    
    @Override
    public int getOrder() {
        // Run early in the advisor chain to capture full execution time
        return 0; // Lower values execute first
    }
    
    private ModelTrace buildTrace(RequestContext reqContext, ChatResponse response, long executionTimeMs) {
        ModelTrace trace = ModelTrace.builder()
            .executionTimeMs(executionTimeMs)
            .hasError(false)
            .build();
        
        // Extract model information
        if (response.getMetadata() != null) {
            Object model = response.getMetadata().get("model");
            if (model != null) {
                trace.setModel(model.toString());
            }
        }
        
        // Extract options information
        if (reqContext.chatOptions != null) {
            if (reqContext.chatOptions.getTemperature() != null) {
                trace.setTemperature(reqContext.chatOptions.getTemperature());
            }
            if (reqContext.chatOptions.getModel() != null) {
                trace.setModel(reqContext.chatOptions.getModel());
            }
        }
        
        // Extract token usage if available
        if (response.getMetadata() != null && response.getMetadata().containsKey("usage")) {
            extractTokenUsage(trace, response.getMetadata().get("usage"));
        }
        
        // Extract response format
        if (response.getResults() != null && !response.getResults().isEmpty()) {
            var result = response.getResults().get(0);
            if (result.getMetadata() != null && result.getMetadata().containsKey("finishReason")) {
                trace.setResponseFormat(result.getMetadata().get("finishReason").toString());
            }
        }
        
        return trace;
    }
    
    private void extractTokenUsage(ModelTrace trace, Object usage) {
        if (usage instanceof Map) {
            Map<?, ?> usageMap = (Map<?, ?>) usage;
            
            Object promptTokens = usageMap.get("promptTokens");
            if (promptTokens instanceof Number) {
                trace.setPromptTokens(((Number) promptTokens).intValue());
            }
            
            Object completionTokens = usageMap.get("completionTokens");
            if (completionTokens instanceof Number) {
                trace.setCompletionTokens(((Number) completionTokens).intValue());
            }
        }
    }
    
    /**
     * Create a named instance of the tracing advisor
     */
    public static DriftKitTracingAdvisor named(String applicationName) {
        return new DriftKitTracingAdvisor(RequestTracingRegistry.getInstance(), applicationName);
    }
    
    /**
     * Create an instance with a specific tracing provider
     */
    public static DriftKitTracingAdvisor withProvider(RequestTracingProvider provider) {
        return new DriftKitTracingAdvisor(provider);
    }
    
    /**
     * Build ModelTextRequest from Spring AI request context
     */
    private ModelTextRequest buildModelTextRequest(RequestContext reqContext) {
        List<ModelContentMessage> messages = new ArrayList<>();
        
        // Add system message if present
        if (reqContext.systemMessage != null && !reqContext.systemMessage.isBlank()) {
            messages.add(ModelContentMessage.create(Role.system, reqContext.systemMessage));
        }
        
        // Add user message
        if (reqContext.userMessage != null && !reqContext.userMessage.isBlank()) {
            messages.add(ModelContentMessage.create(Role.user, reqContext.userMessage));
        }
        
        // Build request
        var requestBuilder = ModelTextRequest.builder()
            .messages(messages);
        
        // Apply options if available
        if (reqContext.chatOptions != null) {
            if (reqContext.chatOptions.getModel() != null) {
                requestBuilder.model(reqContext.chatOptions.getModel());
            }
            if (reqContext.chatOptions.getTemperature() != null) {
                requestBuilder.temperature(reqContext.chatOptions.getTemperature());
            }
            // Note: ModelTextRequest doesn't have maxTokens, topP, topK fields
            // These would need to be added if required for tracing
        }
        
        return requestBuilder.build();
    }
    
    /**
     * Build ModelTextResponse from Spring AI response
     */
    private ModelTextResponse buildModelTextResponse(ChatResponse response, ModelTrace trace) {
        var responseBuilder = ModelTextResponse.builder()
            .trace(trace);
        
        // Extract model from metadata
        if (response.getMetadata() != null && response.getMetadata().containsKey("model")) {
            responseBuilder.model(response.getMetadata().get("model").toString());
        }
        
        // Build choices from results
        if (response.getResults() != null && !response.getResults().isEmpty()) {
            List<ModelTextResponse.ResponseMessage> choices = new ArrayList<>();
            
            for (var result : response.getResults()) {
                String content = result.getOutput() != null ? result.getOutput().getText() : "";
                
                var message = ModelTextResponse.ResponseMessage.builder()
                    .message(ModelMessage.builder()
                        .role(Role.assistant)
                        .content(content)
                        .build())
                    .build();
                    
                choices.add(message);
            }
            
            responseBuilder.choices(choices);
            
            // Note: ModelTextResponse doesn't have a response field - it's computed from choices
        }
        
        // Extract usage if available
        if (response.getMetadata() != null && response.getMetadata().containsKey("usage")) {
            Object usage = response.getMetadata().get("usage");
            if (usage instanceof Map) {
                Map<?, ?> usageMap = (Map<?, ?>) usage;
                
                var usageBuilder = ModelTextResponse.Usage.builder();
                
                Object promptTokens = usageMap.get("promptTokens");
                if (promptTokens instanceof Number) {
                    usageBuilder.promptTokens(((Number) promptTokens).intValue());
                }
                
                Object completionTokens = usageMap.get("completionTokens");
                if (completionTokens instanceof Number) {
                    usageBuilder.completionTokens(((Number) completionTokens).intValue());
                }
                
                Object totalTokens = usageMap.get("totalTokens");
                if (totalTokens instanceof Number) {
                    usageBuilder.totalTokens(((Number) totalTokens).intValue());
                }
                
                responseBuilder.usage(usageBuilder.build());
            }
        }
        
        return responseBuilder.build();
    }
    
    /**
     * Internal context holder for request data
     */
    private static class RequestContext {
        String requestId;
        Instant startTime;
        String userMessage;
        String systemMessage;
        Map<String, Object> advisorParams;
        ChatOptions chatOptions;
    }
}