package ai.driftkit.workflow.engine.agent;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.CachePolicy;
import ai.driftkit.common.domain.client.ModelImageRequest;
import ai.driftkit.common.domain.client.ModelImageResponse;
import ai.driftkit.common.domain.client.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelContentMessage.ModelContentElement;
import ai.driftkit.common.domain.client.ModelMessage;
import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.ModelTextRequest.ReasoningEffort;
import ai.driftkit.common.domain.client.ModelTextResponse.ResponseMessage;
import ai.driftkit.common.domain.streaming.StreamingResponse;
import ai.driftkit.common.domain.streaming.StreamingCallback;
import java.util.concurrent.CompletableFuture;
import ai.driftkit.common.service.ChatStore;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.common.tools.ToolInfo;
import ai.driftkit.common.tools.ToolRegistry;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.common.utils.AIUtils;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.context.core.registry.PromptServiceRegistry;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.context.core.util.PromptUtils;
import ai.driftkit.vector.spring.domain.ContentType;
import ai.driftkit.workflow.engine.agent.loop.AgentLoopResult;
import ai.driftkit.workflow.engine.agent.loop.AgenticOptions;
import ai.driftkit.workflow.engine.agent.loop.ApprovalDecision;
import ai.driftkit.workflow.engine.agent.loop.LoopState;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A simplified LLM agent that wraps the complex DriftKit ModelClient interface
 * with an easy-to-use builder pattern and unified execute() methods.
 * 
 * Features:
 * - Unified execution interface with typed responses
 * - Tool/function calling with automatic execution
 * - Structured output extraction
 * - Multi-modal support (text + images)
 * - Conversation memory management
 * - Prompt template support
 */
@Slf4j
@Data
@EqualsAndHashCode(exclude = {"workflowId", "workflowType", "workflowStep", "memoryMode"})
public class LLMAgent implements Agent {
    
    private final ModelClient modelClient;
    private final String name;
    private final String description;
    private final String systemMessage;
    private final Double temperature;
    private final Integer maxTokens;
    private final String model;
    private final String imageModel;
    private final ReasoningEffort reasoningEffort;
    private final CachePolicy cachePolicy;
    private final StoreContentExtractor storeContentExtractor;

    // Unique agent identifier
    private final String agentId;
    
    // Core components
    private final ChatStore chatStore;
    private final String chatId;
    private final PromptService promptService;
    private final ToolRegistry toolRegistry;
    
    // Tracing support
    private final RequestTracingProvider tracingProvider;
    
    // Custom properties to attach to saved messages (e.g. persona name)
    private final Map<String, String> messageProperties;

    // Explicit history mode (default AUTO keeps the legacy workflowId-based detection)
    private volatile ConversationContext.MemoryMode memoryMode;

    // Workflow context fields for tracing (mutable + volatile for hierarchical agent context injection)
    private volatile String workflowId;
    private volatile String workflowType;
    private volatile String workflowStep;

    /**
     * Set workflow context for hierarchical tracing.
     * Called by SequentialAgent/LoopAgent to link sub-agent traces to parent pipeline.
     * Thread-safe: fields are volatile, written together before agent execution.
     */
    public synchronized void setWorkflowContext(String workflowId, String workflowStep) {
        this.workflowId = workflowId;
        this.workflowStep = workflowStep;
    }

    // Default temperature for structured extraction
    private static final double STRUCTURED_EXTRACTION_TEMPERATURE = 0.1;
    
    // Constructor
    protected LLMAgent(ModelClient modelClient, String name, String description, String systemMessage,
                       Double temperature, Integer maxTokens, String model, String imageModel, String agentId,
                       ChatStore chatStore, String chatId, PromptService promptService, ToolRegistry toolRegistry,
                       RequestTracingProvider tracingProvider, String workflowId, String workflowType,
                       String workflowStep, boolean autoExecuteTools, ReasoningEffort reasoningEffort,
                       Map<String, String> messageProperties, CachePolicy cachePolicy,
                       StoreContentExtractor storeContentExtractor) {
        this.modelClient = modelClient;
        this.name = name;
        this.description = description;
        this.systemMessage = systemMessage;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.cachePolicy = cachePolicy;
        this.storeContentExtractor = storeContentExtractor;
        this.imageModel = imageModel;
        this.agentId = agentId != null ? agentId : AIUtils.generateId();
        this.chatStore = chatStore;
        this.chatId = chatId != null ? chatId : agentId; // Use agentId as chatId if not provided
        this.promptService = promptService;
        this.toolRegistry = toolRegistry;
        this.tracingProvider = tracingProvider;
        this.workflowId = workflowId;
        this.workflowType = workflowType;
        this.workflowStep = workflowStep;
        this.messageProperties = messageProperties;
        this.memoryMode = ConversationContext.MemoryMode.AUTO;
    }

    /**
     * Explicit history mode; AUTO (default) keeps the legacy detection
     * {@code chatStore != null && workflowId blank}.
     */
    public void setMemoryMode(ConversationContext.MemoryMode memoryMode) {
        this.memoryMode = memoryMode != null ? memoryMode : ConversationContext.MemoryMode.AUTO;
    }
    
    /**
     * Create a new LLMAgent builder.
     * 
     * @return A new LLMAgent builder
     */
    public static CustomLLMAgentBuilder builder() {
        return new CustomLLMAgentBuilder();
    }
    
    /**
     * Execute with simple text input
     */
    public AgentResponse<String> executeText(String message) {
        return executeText(message, Collections.emptyMap());
    }

    /**
     * Execute with text and context variables.
     */
    public AgentResponse<String> executeText(String message, Map<String, Object> variables) {
        try {
            AgentExecutionPipeline.Outcome outcome = pipeline().run(AgentExecutionPipeline.ExecutionSpec.builder()
                .userMessage(message)
                .variables(variables)
                .mode(AgentExecutionPipeline.Mode.SINGLE_SHOT)
                .persistence(AgentExecutionPipeline.Persistence.ASSISTANT_TEXT)
                .traceSuffix("TEXT")
                .build());

            ModelTextResponse response = outcome.getResponse();
            String responseText = outcome.getResponseText();

            String reasoning = extractReasoningContent(response);
            CacheUsage cacheUsage = response != null && response.getUsage() != null
                    ? response.getUsage().getCacheUsage() : null;
            Integer reasoningTokens = response != null && response.getUsage() != null
                    ? response.getUsage().getReasoningTokens() : null;

            List<ModelContentElement.ImageData> images = extractImages(response);
            if (CollectionUtils.isNotEmpty(images)) {
                return AgentResponse.<String>builder()
                        .text(responseText)
                        .images(images)
                        .type(AgentResponse.ResponseType.MULTIMODAL)
                        .reasoningContent(reasoning)
                        .cacheUsage(cacheUsage)
                        .reasoningTokens(reasoningTokens)
                        .rawResponse(response)
                        .build();
            }

            return AgentResponse.<String>builder()
                    .text(responseText)
                    .type(AgentResponse.ResponseType.TEXT)
                    .reasoningContent(reasoning)
                    .cacheUsage(cacheUsage)
                    .reasoningTokens(reasoningTokens)
                    .rawResponse(response)
                    .build();

        } catch (Exception e) {
            log.error("Error in executeText", e);
            throw new RuntimeException("Failed to execute text", e);
        }
    }
    
    /**
     * Execute with tools - returns tool calls for manual execution
     */
    public AgentResponse<List<ToolCall>> executeForToolCalls(String message) {
        return executeForToolCalls(message, Collections.emptyMap());
    }
    
    /**
     * Execute with tools - returns tool calls for manual execution with variables
     */
    public AgentResponse<List<ToolCall>> executeForToolCalls(String message, Map<String, Object> variables) {
        try {
            AgentExecutionPipeline.Outcome outcome = pipeline().run(AgentExecutionPipeline.ExecutionSpec.builder()
                .userMessage(message)
                .variables(variables)
                .includeTools(true)
                .mode(AgentExecutionPipeline.Mode.SINGLE_SHOT)
                .persistence(AgentExecutionPipeline.Persistence.NONE)
                .traceSuffix("TOOL_CALLS")
                .build());

            return AgentResponse.toolCalls(AgentExecutionPipeline.toolCallsOf(outcome.getResponse()));

        } catch (Exception e) {
            log.error("Error getting tool calls", e);
            throw new RuntimeException("Failed to get tool calls", e);
        }
    }
    
    /**
     * Execute with tools and automatic execution - returns typed results
     */
    public AgentResponse<List<ToolExecutionResult>> executeWithTools(String message) {
        return executeWithTools(message, Collections.emptyMap());
    }
    
    /**
     * Execute with tools and automatic execution - returns typed results with variables
     */
    public AgentResponse<List<ToolExecutionResult>> executeWithTools(String message, Map<String, Object> variables) {
        try {
            AgentExecutionPipeline.Outcome outcome = pipeline().run(AgentExecutionPipeline.ExecutionSpec.builder()
                .userMessage(message)
                .variables(variables)
                .includeTools(true)
                .mode(AgentExecutionPipeline.Mode.SINGLE_SHOT)
                .persistence(AgentExecutionPipeline.Persistence.NONE)
                .traceSuffix("TOOLS_EXEC")
                .build());

            ModelTextResponse response = outcome.getResponse();

            if (hasToolCalls(response)) {
                List<ToolCall> toolCalls = extractToolCalls(response);
                String assistantContent = extractResponseText(response);

                if (StringUtils.isNotBlank(assistantContent)) {
                    outcome.getContext().addAssistantMessage(assistantContent);
                }

                List<ToolExecutionResult> results = new ArrayList<>();
                for (ToolCall toolCall : toolCalls) {
                    ToolExecutionResult result = executeToolCall(toolCall);
                    results.add(result);
                }

                return AgentResponse.toolResults(results);
            }

            // No tool calls, return regular response
            return AgentResponse.toolResults(Collections.emptyList());

        } catch (Exception e) {
            log.error("Error in executeWithTools", e);
            throw new RuntimeException("Failed to execute with tools", e);
        }
    }
    
    /**
     * Execute with structured output extraction
     */
    /**
     * Execute with structured output.
     */
    public <T> AgentResponse<T> executeStructured(String userMessage, Class<T> targetClass) {
        try {
            AgentExecutionPipeline.Outcome outcome = pipeline().run(AgentExecutionPipeline.ExecutionSpec.builder()
                .userMessage(userMessage)
                .responseFormat(ResponseFormat.jsonSchema(targetClass))
                .mode(AgentExecutionPipeline.Mode.SINGLE_SHOT)
                .persistence(AgentExecutionPipeline.Persistence.ASSISTANT_TEXT)
                .traceSuffix("STRUCTURED")
                .build());

            ModelTextResponse response = outcome.getResponse();
            CacheUsage cacheUsage = response != null && response.getUsage() != null
                    ? response.getUsage().getCacheUsage() : null;
            String reasoning = extractReasoningContent(response);
            Integer reasoningTokens = response != null && response.getUsage() != null
                    ? response.getUsage().getReasoningTokens() : null;

            T result = JsonUtils.fromJson(outcome.getResponseText(), targetClass);

            return AgentResponse.<T>builder()
                    .structuredData(result)
                    .type(AgentResponse.ResponseType.STRUCTURED_DATA)
                    .cacheUsage(cacheUsage)
                    .reasoningContent(reasoning)
                    .reasoningTokens(reasoningTokens)
                    .rawResponse(response)
                    .build();

        } catch (Exception e) {
            log.error("Error extracting structured data", e);
            throw new RuntimeException("Failed to extract structured data", e);
        }
    }
    
    /**
     * Execute structured extraction using prompt template by ID
     */
    public <T> AgentResponse<T> executeStructuredWithPrompt(String promptId, Map<String, Object> variables, Class<T> targetClass) {
        return executeStructuredWithPrompt(promptId, variables, targetClass, Language.GENERAL);
    }
    
    /**
     * Execute structured extraction using prompt template by ID with language
     */
    public <T> AgentResponse<T> executeStructuredWithPrompt(String promptId, Map<String, Object> variables, 
                                                           Class<T> targetClass, Language language) {
        try {
            AgentExecutionPipeline.Outcome outcome = pipeline().run(AgentExecutionPipeline.ExecutionSpec.builder()
                .promptId(promptId)
                .language(language)
                .variables(variables)
                .responseFormat(ResponseFormat.jsonSchema(targetClass))
                .mode(AgentExecutionPipeline.Mode.SINGLE_SHOT)
                .persistence(AgentExecutionPipeline.Persistence.ASSISTANT_TEXT)
                .traceSuffix("STRUCTURED_PROMPT")
                .build());

            ModelTextResponse response = outcome.getResponse();
            CacheUsage cacheUsage2 = response != null && response.getUsage() != null
                    ? response.getUsage().getCacheUsage() : null;
            String reasoning2 = extractReasoningContent(response);

            String jsonResponse = outcome.getResponseText();
            log.info("Raw JSON response from AI: {}", jsonResponse);

            T result = JsonUtils.fromJson(jsonResponse, targetClass);

            return AgentResponse.<T>builder()
                    .structuredData(result)
                    .type(AgentResponse.ResponseType.STRUCTURED_DATA)
                    .cacheUsage(cacheUsage2)
                    .reasoningContent(reasoning2)
                    .rawResponse(response)
                    .build();

        } catch (Exception e) {
            log.error("Error in executeStructuredWithPrompt", e);
            throw new RuntimeException("Failed to execute structured with prompt", e);
        }
    }

    double getTemperature(Prompt prompt) {
        if (temperature != null) {
            return temperature;
        }

        if (prompt != null && prompt.getTemperature() != null) {
            return prompt.getTemperature();
        }

        return modelClient.getTemperature();
    }

    /**
     * Execute using prompt template by ID
     */
    public AgentResponse<String> executeWithPrompt(String promptId, Map<String, Object> variables) {
        return executeWithPrompt(promptId, variables, Language.GENERAL);
    }
    
    /**
     * Execute using prompt template by ID with language
     */
    public AgentResponse<String> executeWithPrompt(String promptId, Map<String, Object> variables, Language language) {
        try {
            AgentExecutionPipeline.Outcome outcome = pipeline().run(AgentExecutionPipeline.ExecutionSpec.builder()
                .promptId(promptId)
                .language(language)
                .variables(variables)
                .mode(AgentExecutionPipeline.Mode.SINGLE_SHOT)
                .persistence(AgentExecutionPipeline.Persistence.ASSISTANT_TEXT)
                .traceSuffix("PROMPT")
                .build());

            String responseText = outcome.getResponseText();

            List<ModelContentElement.ImageData> images = extractImages(outcome.getResponse());
            if (CollectionUtils.isNotEmpty(images)) {
                return AgentResponse.multimodal(responseText, images);
            }

            return AgentResponse.text(responseText);

        } catch (Exception e) {
            log.error("Error in executeWithPrompt", e);
            throw new RuntimeException("Failed to execute with prompt", e);
        }
    }
    
    /**
     * Execute image generation using the agent's imageModel field
     */
    public AgentResponse<ModelContentElement.ImageData> executeImageGeneration(String prompt) {
        return executeImageGeneration(prompt, null);
    }
    
    /**
     * Execute image generation with variables
     */
    public AgentResponse<ModelContentElement.ImageData> executeImageGeneration(String prompt, Map<String, Object> variables) {
        try {
            ModelImageResponse response = pipeline().runImageGeneration(AgentExecutionPipeline.ExecutionSpec.builder()
                .userMessage(prompt)
                .variables(variables)
                .traceSuffix("IMAGE_GEN")
                .build());

            if (response != null && response.getBytes() != null && !response.getBytes().isEmpty()) {
                ModelContentElement.ImageData imageData = response.getBytes().get(0);
                return AgentResponse.image(imageData);
            }

            throw new RuntimeException("No image generated");

        } catch (Exception e) {
            log.error("Error generating image", e);
            throw new RuntimeException("Failed to generate image", e);
        }
    }
    
    /**
     * Execute with images
     */
    public AgentResponse<String> executeWithImages(String text, byte[] imageData) {
        return executeWithImages(text, Collections.singletonList(imageData));
    }
    
    /**
     * Execute with images and variables
     */
    public AgentResponse<String> executeWithImages(String text, byte[] imageData, Map<String, Object> variables) {
        return executeWithImages(text, Collections.singletonList(imageData), variables);
    }
    
    /**
     * Execute with multiple images
     */
    public AgentResponse<String> executeWithImages(String text, List<byte[]> imageDataList) {
        return executeWithImages(text, imageDataList, null);
    }
    
    /**
     * Execute with multiple images and variables
     */
    public AgentResponse<String> executeWithImages(String text, List<byte[]> imageDataList, Map<String, Object> variables) {
        try {
            List<ModelContentElement.ImageData> imageDataObjects = imageDataList.stream()
                .map(bytes -> new ModelContentElement.ImageData(bytes, "image/jpeg"))
                .collect(Collectors.toList());

            AgentExecutionPipeline.Outcome outcome = pipeline().run(AgentExecutionPipeline.ExecutionSpec.builder()
                .userMessage(text)
                .variables(variables)
                .media(imageDataObjects)
                .imageToText(true)
                .mode(AgentExecutionPipeline.Mode.SINGLE_SHOT)
                .persistence(AgentExecutionPipeline.Persistence.ASSISTANT_TEXT)
                .traceSuffix("IMAGE")
                .build());

            String responseText = outcome.getResponseText();

            List<ModelContentElement.ImageData> images = extractImages(outcome.getResponse());
            if (CollectionUtils.isNotEmpty(images)) {
                return AgentResponse.multimodal(responseText, images);
            }

            return AgentResponse.text(responseText);

        } catch (Exception e) {
            log.error("Error executing with images", e);
            throw new RuntimeException("Failed to execute with images", e);
        }
    }
    
    /**
     * Media content wrapper - binary data + content type
     */
    @Data
    @Builder
    public static class MediaContent {
        private byte[] data;
        private ContentType contentType;

        public String getMimeType() {
            return contentType.getMimeType();
        }

        public static MediaContent of(byte[] data, ContentType contentType) {
            return MediaContent.builder()
                .data(data)
                .contentType(contentType)
                .build();
        }
    }

    /**
     * Execute with binary media content and structured output extraction
     *
     * @param text Prompt text
     * @param mediaContent Single media content (image, video, audio, pdf, etc.)
     * @param targetClass Target class for structured output
     */
    public <T> AgentResponse<T> executeStructuredWithMedia(String text, MediaContent mediaContent, Class<T> targetClass) {
        return executeStructuredWithMedia(text, Collections.singletonList(mediaContent), targetClass);
    }

    /**
     * Execute with multiple binary media files and structured output extraction
     *
     * @param text Prompt text
     * @param mediaContentList List of media content (images, videos, audio, pdfs, etc.)
     * @param targetClass Target class for structured output
     */
    public <T> AgentResponse<T> executeStructuredWithMedia(String text, List<MediaContent> mediaContentList, Class<T> targetClass) {
        try {
            List<ModelContentElement.ImageData> mediaDataObjects = mediaContentList.stream()
                .map(media -> new ModelContentElement.ImageData(media.getData(), media.getMimeType()))
                .collect(Collectors.toList());

            AgentExecutionPipeline.Outcome outcome = pipeline().run(AgentExecutionPipeline.ExecutionSpec.builder()
                .userMessage(text)
                .media(mediaDataObjects)
                .imageToText(true)
                .responseFormat(ResponseFormat.jsonSchema(targetClass))
                .temperatureOverride(STRUCTURED_EXTRACTION_TEMPERATURE)
                .mode(AgentExecutionPipeline.Mode.SINGLE_SHOT)
                .persistence(AgentExecutionPipeline.Persistence.ASSISTANT_TEXT)
                .traceSuffix("MEDIA_STRUCTURED")
                .build());

            ModelTextResponse response = outcome.getResponse();
            CacheUsage mediaCacheUsage = response != null && response.getUsage() != null
                    ? response.getUsage().getCacheUsage() : null;

            T result = JsonUtils.fromJson(outcome.getResponseText(), targetClass);

            return AgentResponse.<T>builder()
                    .structuredData(result)
                    .type(AgentResponse.ResponseType.STRUCTURED_DATA)
                    .cacheUsage(mediaCacheUsage)
                    .rawResponse(response)
                    .build();

        } catch (Exception e) {
            log.error("Error extracting structured data from media", e);
            throw new RuntimeException("Failed to extract structured data from media", e);
        }
    }

    /**
     * Convenience method: Execute with single image and structured output
     */
    public <T> AgentResponse<T> executeStructuredWithImage(String text, byte[] imageData, Class<T> targetClass) {
        MediaContent media = MediaContent.of(imageData, ContentType.JPG);
        return executeStructuredWithMedia(text, media, targetClass);
    }

    /**
     * Convenience method: Execute with video and structured output
     */
    public <T> AgentResponse<T> executeStructuredWithVideo(String text, byte[] videoData, Class<T> targetClass) {
        MediaContent media = MediaContent.of(videoData, ContentType.VIDEO_MP4);
        return executeStructuredWithMedia(text, media, targetClass);
    }

    /**
     * Convenience method: Execute with PDF and structured output
     */
    public <T> AgentResponse<T> executeStructuredWithPDF(String text, byte[] pdfData, Class<T> targetClass) {
        MediaContent media = MediaContent.of(pdfData, ContentType.PDF);
        return executeStructuredWithMedia(text, media, targetClass);
    }

    /**
     * Execute a single tool call manually
     */
    public ToolExecutionResult executeToolCall(ToolCall toolCall) {
        try {
            Object result = toolRegistry.executeToolCall(toolCall);
            return ToolExecutionResult.success(toolCall.getFunction().getName(), result);
        } catch (Exception e) {
            log.error("Error executing tool: {}", toolCall.getFunction().getName(), e);
            return ToolExecutionResult.failure(toolCall.getFunction().getName(), e.getMessage());
        }
    }
    
    /**
     * Register a tool function using instance method
     */
    public LLMAgent registerTool(String methodName, Object instance) {
        toolRegistry.registerInstanceMethod(instance, methodName);
        return this;
    }
    
    /**
     * Register a tool function with description
     */
    public LLMAgent registerTool(String methodName, Object instance, String description) {
        toolRegistry.registerInstanceMethod(instance, methodName, description);
        return this;
    }
    
    /**
     * Register a static method as a tool
     */
    public LLMAgent registerStaticTool(String methodName, Class<?> clazz) {
        toolRegistry.registerStaticMethod(clazz, methodName);
        return this;
    }
    
    /**
     * Register all annotated tools from an instance
     */
    public LLMAgent registerTools(Object instance) {
        toolRegistry.registerClass(instance);
        return this;
    }
    
    /**
     * Register all static annotated tools from a class
     */
    public LLMAgent registerStaticTools(Class<?> clazz) {
        toolRegistry.registerStaticClass(clazz);
        return this;
    }
    
    /**
     * Clear conversation history
     */
    /**
     * Create ConversationContext with message properties if set.
     */
    ConversationContext createConversationContext() {
        ConversationContext context = ConversationContext.from(chatStore, workflowId, chatId,
                getEffectiveMaxTokens(), memoryMode);
        if (messageProperties != null && !messageProperties.isEmpty()) {
            context.setMessageProperties(messageProperties);
        }
        return context;
    }

    /**
     * Add user message to context, using storeContentExtractor if configured.
     * When extractor is set, the raw content goes to chatStore while full API content goes to messages.
     */
    void addUserMessageToContext(ConversationContext context, String apiContent,
                                         Map<String, Object> variables) {
        if (storeContentExtractor != null) {
            String storeContent = storeContentExtractor.extract(apiContent, variables);
            context.addUserMessage(storeContent, apiContent);
        } else {
            context.addUserMessage(apiContent);
        }
    }

    /**
     * Build a RequestContext for tracing with all available metadata.
     * Centralizes trace context creation to ensure no data is lost.
     */
    RequestTracingProvider.RequestContext buildTraceContext(
            String contextTypeSuffix,
            Map<String, Object> variables,
            String promptId,
            List<ModelContentMessage> messages) {
        return RequestTracingProvider.RequestContext.builder()
            .contextId(agentId)
            .contextType(buildContextType(contextTypeSuffix))
            .promptId(promptId)
            .variables(variables)
            .chatId(chatId)
            .workflowId(workflowId)
            .workflowType(workflowType)
            .workflowStep(workflowStep)
            .purpose(workflowType)
            .systemMessage(systemMessage)
            .messageProperties(messageProperties)
            .messages(messages)
            .build();
    }

    public void clearHistory() {
        if (chatStore != null) {
            chatStore.deleteAll(chatId);
        }
    }
    
    /**
     * Get conversation history
     */
    public List<ChatMessage> getConversationHistory() {
        if (chatStore != null) {
            return chatStore.getAll(chatId);
        }
        return Collections.emptyList();
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public String execute(String input) {
        return executeText(input).getText();
    }
    
    @Override
    public String execute(String text, byte[] imageData) {
        return executeWithImages(text, imageData).getText();
    }
    
    @Override
    public String execute(String text, List<byte[]> imageDataList) {
        return executeWithImages(text, imageDataList).getText();
    }
    
    @Override
    public String execute(String input, Map<String, Object> variables) {
        return executeText(input, variables).getText();
    }
    
    /**
     * Execute with streaming and required callback
     * @return CompletableFuture with the complete response text
     */
    public CompletableFuture<String> executeStreaming(String input, StreamingCallback<String> callback) {
        return executeStreaming(input, null, callback);
    }
    
    /**
     * Execute with streaming, variables and required callback
     * @return CompletableFuture with the complete response text
     */
    public CompletableFuture<String> executeStreaming(String input, Map<String, Object> variables, StreamingCallback<String> callback) {
        if (callback == null) {
            throw new IllegalArgumentException("Callback is required for streaming execution");
        }
        try {
            return pipeline().runStreaming(AgentExecutionPipeline.ExecutionSpec.builder()
                .userMessage(input)
                .variables(variables)
                .mode(AgentExecutionPipeline.Mode.SINGLE_SHOT)
                .persistence(AgentExecutionPipeline.Persistence.ASSISTANT_TEXT)
                .traceSuffix("STREAM")
                .build(), callback);
        } catch (Exception e) {
            log.error("Error in executeStreaming", e);
            callback.onError(new RuntimeException("Failed to execute streaming", e));
            CompletableFuture<String> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("Failed to execute streaming", e));
            return failed;
        }
    }
    
    /**
     * The single internal execution path (plan, D2). Stateless — created per call.
     */
    AgentExecutionPipeline pipeline() {
        return new AgentExecutionPipeline(this);
    }

    /**
     * Execute a full agentic loop (plan, D1): the model reasons, requests tools,
     * receives their results and continues until it finishes or a limit fires.
     * Tool calls go through hooks (D6) and may suspend for human approval (D7).
     *
     * @return the loop result with stop reason, final text, tool results and usage
     */
    public AgentLoopResult executeAgentic(String message) {
        return executeAgentic(message, Collections.emptyMap(), AgenticOptions.defaults());
    }

    public AgentLoopResult executeAgentic(String message, Map<String, Object> variables) {
        return executeAgentic(message, variables, AgenticOptions.defaults());
    }

    public AgentLoopResult executeAgentic(String message, Map<String, Object> variables, AgenticOptions options) {
        try {
            AgentExecutionPipeline.Outcome outcome = pipeline().run(AgentExecutionPipeline.ExecutionSpec.builder()
                .userMessage(message)
                .variables(variables)
                .includeTools(true)
                .mode(AgentExecutionPipeline.Mode.AGENTIC)
                .persistence(AgentExecutionPipeline.Persistence.ASSISTANT_TEXT)
                .traceSuffix("AGENTIC")
                .agenticOptions(options)
                .build());
            return outcome.getLoopResult();
        } catch (Exception e) {
            log.error("Error in executeAgentic", e);
            throw new RuntimeException("Failed to execute agentic loop", e);
        }
    }

    /**
     * Resume an agentic loop previously suspended with StopReason.PENDING_APPROVAL.
     */
    public AgentLoopResult resumeAgentic(LoopState state, ApprovalDecision decision, AgenticOptions options) {
        try {
            return pipeline().resumeAgentic(state, decision, options);
        } catch (Exception e) {
            log.error("Error resuming agentic loop", e);
            throw new RuntimeException("Failed to resume agentic loop", e);
        }
    }

    // Private helper methods
    
    private String processMessageWithVariables(String message, Map<String, Object> variables) {
        if (variables != null && !variables.isEmpty()) {
            return PromptUtils.applyVariables(message, variables);
        }
        return message;
    }
    
    // Convert ModelMessage list to ModelContentMessage list
    private List<ModelContentMessage> convertToModelContentMessages(List<ModelMessage> modelMessages) {
        return modelMessages.stream()
            .map(msg -> ModelContentMessage.builder()
                .role(msg.getRole())
                .content(List.of(ModelContentElement.create(msg.getContent() != null ? msg.getContent() : "")))
                .toolCalls(msg.getToolCalls())
                .toolCallId(msg.getToolCallId())
                .build())
            .collect(Collectors.toList());
    }
    
    private ModelContentMessage convertMessageToModelMessage(ChatMessage message) {
        Role role = switch (message.getType()) {
            case USER -> Role.user;
            case AI -> Role.assistant;
            case SYSTEM -> Role.system;
            default -> Role.user;
        };
        
        // Get message content from properties
        String content = message.getPropertiesMap().get(ChatMessage.PROPERTY_MESSAGE);
        if (content == null) {
            // Fallback to JSON representation of all properties
            try {
                content = JsonUtils.toJson(message.getPropertiesMap());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        return ModelContentMessage.create(role, content);
    }
    
    private boolean hasToolCalls(ModelTextResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getChoices())) {
            return false;
        }
        
        // Check if any choice has tool calls
        return response.getChoices().stream()
            .anyMatch(choice -> choice.getMessage() != null && 
                               CollectionUtils.isNotEmpty(choice.getMessage().getToolCalls()));
    }
    
    private List<ToolCall> extractToolCalls(ModelTextResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getChoices())) {
            return Collections.emptyList();
        }
        
        // Collect tool calls from all choices
        return response.getChoices().stream()
            .filter(choice -> choice.getMessage() != null)
            .map(choice -> choice.getMessage().getToolCalls())
            .filter(CollectionUtils::isNotEmpty)
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }
    
    
    List<ModelContentElement.ImageData> extractImages(ModelTextResponse response) {
        // For now, text-to-text responses don't contain images
        // This is a placeholder for future multimodal responses
        return Collections.emptyList();
    }
    
    
    private String convertResultToString(Object result) {
        if (result == null) {
            return "null";
        }
        if (result instanceof String) {
            return (String) result;
        }
        // For complex objects, serialize as JSON
        try {
            return JsonUtils.toJson(result);
        } catch (Exception e) {
            return result.toString();
        }
    }
    
    private List<ModelContentElement> buildMultimodalContent(String text, 
                                                           List<ModelContentElement.ImageData> imageDataList) {
        List<ModelContentElement> content = new ArrayList<>();
        
        // Add text content if present
        if (StringUtils.isNotBlank(text)) {
            content.add(ModelContentElement.builder()
                .type(ModelTextRequest.MessageType.text)
                .text(text)
                .build());
        }
        
        // Add image content
        if (CollectionUtils.isNotEmpty(imageDataList)) {
            for (ModelContentElement.ImageData imageData : imageDataList) {
                content.add(ModelContentElement.builder()
                    .type(ModelTextRequest.MessageType.image)
                    .image(imageData)
                    .build());
            }
        }
        
        return content;
    }
    
    String extractReasoningContent(ModelTextResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getChoices())) {
            return null;
        }
        ResponseMessage first = response.getChoices().getFirst();
        if (first.getMessage() != null) {
            return first.getMessage().getReasoningContent();
        }
        return null;
    }

    String extractResponseText(ModelTextResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getChoices())) {
            return "";
        }

        StringBuilder allResponses = new StringBuilder();
        for (int i = 0; i < response.getChoices().size(); i++) {
            ResponseMessage choice = response.getChoices().get(i);
            if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                if (i > 0) {
                    allResponses.append("\n---\n"); // Separator between choices
                }
                allResponses.append(choice.getMessage().getContent());
            }
        }
        return allResponses.toString();
    }
    
    /**
     * Get effective model (from agent config or client default).
     */
    String getEffectiveModel() {
        return StringUtils.isNotBlank(model) ? model : modelClient.getModel();
    }

    /**
     * Get effective max tokens (from agent config or client default).
     */
    private Integer getEffectiveMaxTokens() {
        return maxTokens != null ? maxTokens : modelClient.getMaxTokens();
    }
    
    /**
     * Get tracing provider - first check injected provider, then registry
     */
    RequestTracingProvider getTracingProvider() {
        if (tracingProvider != null) {
            return tracingProvider;
        }
        return RequestTracingRegistry.getInstance();
    }
    
    /**
     * Build context type based on agent name/description and operation type
     */
    private String buildContextType(String operationType) {
        if (StringUtils.isNotBlank(name)) {
            return String.format("%s_%s", name.toUpperCase().replace(" ", "_"), operationType);
        } else if (StringUtils.isNotBlank(description)) {
            // Use first few words of description
            String[] words = description.split("\\s+");
            String shortDesc = words[0].toUpperCase();
            return String.format("%s_%s", shortDesc, operationType);
        } else {
            // Default to agent ID and operation
            return String.format("AGENT_%s_%s", agentId, operationType);
        }
    }
    
    /**
     * Custom builder to set default values and validation.
     */
    public static class CustomLLMAgentBuilder {
        
        private ModelClient modelClient;
        private String name;
        private String description;
        private String systemMessage;
        private Double temperature;
        private Integer maxTokens;
        private String model;
        private String imageModel;
        private String agentId;
        private ChatStore chatStore;
        private String chatId;
        private PromptService promptService;
        private ToolRegistry toolRegistry;
        private RequestTracingProvider tracingProvider;
        private String workflowId;
        private String workflowType;
        private String workflowStep;
        private boolean autoExecuteTools = true;
        private ReasoningEffort reasoningEffort;
        private CachePolicy cachePolicy;
        private StoreContentExtractor storeContentExtractor;
        private Map<String, String> messageProperties;
        private ConversationContext.MemoryMode memoryMode;

        private List<ToolInfo> pendingTools = new ArrayList<>();
        
        public CustomLLMAgentBuilder() {
            // Set defaults
            this.autoExecuteTools = true;
        }

        /**
         * Explicit history mode (plan, D2): CONVERSATIONAL / STATELESS instead of
         * the implicit workflowId-based detection. Default: AUTO (legacy behavior).
         */
        public CustomLLMAgentBuilder memoryMode(ConversationContext.MemoryMode memoryMode) {
            this.memoryMode = memoryMode;
            return this;
        }

        public CustomLLMAgentBuilder reasoningEffort(ReasoningEffort reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public CustomLLMAgentBuilder modelClient(ModelClient modelClient) {
            this.modelClient = modelClient;
            return this;
        }
        
        public CustomLLMAgentBuilder name(String name) {
            this.name = name;
            return this;
        }
        
        public CustomLLMAgentBuilder description(String description) {
            this.description = description;
            return this;
        }
        
        public CustomLLMAgentBuilder systemMessage(String systemMessage) {
            this.systemMessage = systemMessage;
            return this;
        }
        
        public CustomLLMAgentBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }
        
        public CustomLLMAgentBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }
        
        public CustomLLMAgentBuilder model(String model) {
            this.model = model;
            return this;
        }
        
        public CustomLLMAgentBuilder imageModel(String imageModel) {
            this.imageModel = imageModel;
            return this;
        }
        
        public CustomLLMAgentBuilder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }
        
        public CustomLLMAgentBuilder chatStore(ChatStore chatStore) {
            this.chatStore = chatStore;
            return this;
        }
        
        public CustomLLMAgentBuilder chatId(String chatId) {
            this.chatId = chatId;
            return this;
        }
        
        public CustomLLMAgentBuilder promptService(PromptService promptService) {
            this.promptService = promptService;
            return this;
        }
        
        public CustomLLMAgentBuilder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }
        
        public CustomLLMAgentBuilder tracingProvider(RequestTracingProvider tracingProvider) {
            this.tracingProvider = tracingProvider;
            return this;
        }
        
        public CustomLLMAgentBuilder autoExecuteTools(boolean autoExecuteTools) {
            this.autoExecuteTools = autoExecuteTools;
            return this;
        }
        
        public CustomLLMAgentBuilder workflowId(String workflowId) {
            this.workflowId = workflowId;
            return this;
        }
        
        public CustomLLMAgentBuilder workflowType(String workflowType) {
            this.workflowType = workflowType;
            return this;
        }
        
        public CustomLLMAgentBuilder workflowStep(String workflowStep) {
            this.workflowStep = workflowStep;
            return this;
        }

        public CustomLLMAgentBuilder messageProperties(Map<String, String> messageProperties) {
            this.messageProperties = messageProperties;
            return this;
        }

        public CustomLLMAgentBuilder cachePolicy(CachePolicy cachePolicy) {
            this.cachePolicy = cachePolicy;
            return this;
        }

        public CustomLLMAgentBuilder storeContentExtractor(StoreContentExtractor storeContentExtractor) {
            this.storeContentExtractor = storeContentExtractor;
            return this;
        }

        /**
         * Add a tool to the agent
         */
        public CustomLLMAgentBuilder addTool(ToolInfo toolInfo) {
            pendingTools.add(toolInfo);
            return this;
        }
        
        public LLMAgent build() {
            // Auto-discover PromptService if not explicitly set
            if (promptService == null) {
                promptService = PromptServiceRegistry.getInstance();
                if (promptService != null) {
                    log.debug("Auto-discovered PromptService from registry: {}", 
                            promptService.getClass().getSimpleName());
                }
            }
            
            // Create tool registry if tools were added
            if (!pendingTools.isEmpty()) {
                if (toolRegistry == null) {
                    toolRegistry = new ToolRegistry();
                }
                for (ToolInfo toolInfo : pendingTools) {
                    toolRegistry.registerTool(toolInfo);
                }
            } else if (toolRegistry == null) {
                // Set default empty registry
                toolRegistry = new ToolRegistry();
            }
            
            LLMAgent agent = new LLMAgent(modelClient, name, description, systemMessage,
                    temperature, maxTokens, model, imageModel, agentId,
                    chatStore, chatId, promptService, toolRegistry,
                    tracingProvider, workflowId, workflowType, workflowStep,
                    autoExecuteTools, reasoningEffort, messageProperties, cachePolicy,
                    storeContentExtractor);
            if (memoryMode != null) {
                agent.setMemoryMode(memoryMode);
            }
            return agent;
        }
    }
}