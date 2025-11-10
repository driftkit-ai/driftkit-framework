package ai.driftkit.workflow.engine.agent;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.client.*;
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
import lombok.Builder;
import lombok.Data;
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

    // Unique agent identifier
    private final String agentId;
    
    // Core components
    private final ChatStore chatStore;
    private final String chatId;
    private final PromptService promptService;
    private final ToolRegistry toolRegistry;
    
    // Tracing support
    private final RequestTracingProvider tracingProvider;
    
    // Workflow context fields for tracing
    private final String workflowId;
    private final String workflowType;
    private final String workflowStep;

    // Default temperature for structured extraction
    private static final double STRUCTURED_EXTRACTION_TEMPERATURE = 0.1;
    
    // Constructor
    protected LLMAgent(ModelClient modelClient, String name, String description, String systemMessage,
                       Double temperature, Integer maxTokens, String model, String imageModel, String agentId,
                       ChatStore chatStore, String chatId, PromptService promptService, ToolRegistry toolRegistry,
                       RequestTracingProvider tracingProvider, String workflowId, String workflowType, 
                       String workflowStep, boolean autoExecuteTools, ReasoningEffort reasoningEffort) {
        this.modelClient = modelClient;
        this.name = name;
        this.description = description;
        this.systemMessage = systemMessage;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
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
     * Execute with text and context variables
     */
    public AgentResponse<String> executeText(String message, Map<String, Object> variables) {
        try {
            // Process message with variables
            String processedMessage = processMessageWithVariables(message, variables);

            // Create conversation context with token limit
            ConversationContext context = ConversationContext.from(chatStore, workflowId, chatId, getEffectiveMaxTokens());
            
            // Add system message if present
            if (StringUtils.isNotBlank(systemMessage)) {
                context.addSystemMessage(systemMessage);
            }
            
            // Add user message (saves to store if in history mode)
            context.addUserMessage(processedMessage);
            
            // Build request
            List<ModelContentMessage> messages = convertToModelContentMessages(context.getMessagesForRequest());
            ModelTextRequest request = ModelTextRequest.builder()
                .model(getEffectiveModel())
                .temperature(getTemperature(null))
                .reasoningEffort(reasoningEffort)
                .messages(messages)
                .build();
            
            // Execute request
            ModelTextResponse response = modelClient.textToText(request);
            
            // Save assistant response
            String responseText = extractResponseText(response);
            if (StringUtils.isNotBlank(responseText)) {
                context.addAssistantMessage(responseText);
            }
            
            // Trace if provider is available
            RequestTracingProvider provider = getTracingProvider();
            if (provider != null) {
                String contextType = buildContextType("TEXT");
                RequestTracingProvider.RequestContext traceContext = RequestTracingProvider.RequestContext.builder()
                    .contextId(agentId)
                    .contextType(contextType)
                    .variables(variables)
                    .chatId(chatId)
                    .workflowId(workflowId)
                    .workflowType(workflowType)
                    .workflowStep(workflowStep)
                    .build();
                provider.traceTextRequest(request, response, traceContext);
            }
            
            // Check if response contains images
            List<ModelContentElement.ImageData> images = extractImages(response);
            if (CollectionUtils.isNotEmpty(images)) {
                return AgentResponse.multimodal(responseText, images);
            }
            
            return AgentResponse.text(responseText);
            
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
            // Process message with variables
            String processedMessage = processMessageWithVariables(message, variables);
            
            // Create conversation context with token limit
            ConversationContext context = ConversationContext.from(chatStore, workflowId, chatId, getEffectiveMaxTokens());
            
            // Add system message if present
            if (StringUtils.isNotBlank(systemMessage)) {
                context.addSystemMessage(systemMessage);
            }
            
            // Add user message (saves to store if in history mode)
            context.addUserMessage(processedMessage);
            
            // Build request with tools
            List<ModelContentMessage> messages = convertToModelContentMessages(context.getMessagesForRequest());
            ModelClient.Tool[] tools = toolRegistry != null ? toolRegistry.getTools() : new ModelClient.Tool[0];
            
            ModelTextRequest request = ModelTextRequest.builder()
                .model(getEffectiveModel())
                .temperature(getTemperature(null))
                .messages(messages)
                .reasoningEffort(reasoningEffort)
                .tools(tools.length > 0 ? Arrays.asList(tools) : null)
                .build();
            
            // Execute request
            ModelTextResponse response = modelClient.textToText(request);
            
            // Trace if provider is available
            RequestTracingProvider provider = getTracingProvider();
            if (provider != null) {
                String contextType = buildContextType("TOOL_CALLS");
                RequestTracingProvider.RequestContext traceContext = RequestTracingProvider.RequestContext.builder()
                    .contextId(agentId)
                    .contextType(contextType)
                    .variables(variables)
                    .chatId(chatId)
                    .workflowId(workflowId)
                    .workflowType(workflowType)
                    .workflowStep(workflowStep)
                    .build();
                provider.traceTextRequest(request, response, traceContext);
            }
            
            // Extract tool calls
            List<ToolCall> toolCalls = extractToolCalls(response);
            return AgentResponse.toolCalls(toolCalls);
            
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
            // Process message with variables
            String processedMessage = processMessageWithVariables(message, variables);
            
            // Create conversation context with token limit
            ConversationContext context = ConversationContext.from(chatStore, workflowId, chatId, getEffectiveMaxTokens());
            
            // Add system message if present
            if (StringUtils.isNotBlank(systemMessage)) {
                context.addSystemMessage(systemMessage);
            }
            
            // Add user message (saves to store if in history mode)
            context.addUserMessage(processedMessage);
            
            // Build request with tools
            List<ModelContentMessage> messages = convertToModelContentMessages(context.getMessagesForRequest());
            ModelClient.Tool[] tools = toolRegistry != null ? toolRegistry.getTools() : new ModelClient.Tool[0];
            
            ModelTextRequest request = ModelTextRequest.builder()
                .model(getEffectiveModel())
                .temperature(getTemperature(null))
                .messages(messages)
                .reasoningEffort(reasoningEffort)
                .tools(tools.length > 0 ? Arrays.asList(tools) : null)
                .build();
            
            // Execute request
            ModelTextResponse response = modelClient.textToText(request);
            
            // Trace if provider is available
            RequestTracingProvider provider = getTracingProvider();
            if (provider != null) {
                String contextType = buildContextType("TOOLS_EXEC");
                RequestTracingProvider.RequestContext traceContext = RequestTracingProvider.RequestContext.builder()
                    .contextId(agentId)
                    .contextType(contextType)
                    .variables(variables)
                    .chatId(chatId)
                    .workflowId(workflowId)
                    .workflowType(workflowType)
                    .workflowStep(workflowStep)
                    .build();
                provider.traceTextRequest(request, response, traceContext);
            }
            
            // Check for tool calls
            if (hasToolCalls(response)) {
                // Extract tool calls and assistant message
                List<ToolCall> toolCalls = extractToolCalls(response);
                String assistantContent = extractResponseText(response);
                
                // ALWAYS add assistant response to context for building follow-up request
                if (StringUtils.isNotBlank(assistantContent)) {
                    context.addAssistantMessage(assistantContent);
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
    public <T> AgentResponse<T> executeStructured(String userMessage, Class<T> targetClass) {
        try {
            // Create response format for structured output
            ResponseFormat responseFormat = ResponseFormat.jsonSchema(targetClass);
            
            // Create conversation context with token limit
            ConversationContext context = ConversationContext.from(chatStore, workflowId, chatId, getEffectiveMaxTokens());
            
            // Add system message if present
            if (StringUtils.isNotBlank(systemMessage)) {
                context.addSystemMessage(systemMessage);
            }
            
            // Add user message (saves to store if in history mode)
            context.addUserMessage(userMessage);
            
            // Build request with structured output
            List<ModelContentMessage> messages = convertToModelContentMessages(context.getMessagesForRequest());
            ModelTextRequest request = ModelTextRequest.builder()
                .model(getEffectiveModel())
                .temperature(getTemperature(null))
                .messages(messages)
                .responseFormat(responseFormat)
                .reasoningEffort(reasoningEffort)
                .build();
            
            // Execute request
            ModelTextResponse response = modelClient.textToText(request);
            
            // Trace if provider is available
            RequestTracingProvider provider = getTracingProvider();
            if (provider != null) {
                String contextType = buildContextType("STRUCTURED");
                RequestTracingProvider.RequestContext traceContext = RequestTracingProvider.RequestContext.builder()
                    .contextId(agentId)
                    .contextType(contextType)
                    .chatId(chatId)
                    .workflowId(workflowId)
                    .workflowType(workflowType)
                    .workflowStep(workflowStep)
                    .build();
                provider.traceTextRequest(request, response, traceContext);
            }
            
            // Extract and parse response
            String jsonResponse = extractResponseText(response);
            
            // Save assistant response
            if (StringUtils.isNotBlank(jsonResponse)) {
                context.addAssistantMessage(jsonResponse);
            }
            
            T result = JsonUtils.fromJson(jsonResponse, targetClass);
            
            return AgentResponse.structured(result);
            
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
            // Use injected PromptService or fall back to registry
            PromptService effectivePromptService = promptService != null ? 
                promptService : PromptServiceRegistry.getInstance();
                
            if (effectivePromptService == null) {
                throw new IllegalStateException("PromptService not configured. " +
                    "Please ensure PromptService is available in your application context " +
                    "or register one via PromptServiceRegistry.register()");
            }
            
            // Get prompt by ID
            Optional<Prompt> promptOpt = effectivePromptService.getCurrentPrompt(promptId, language);
            if (promptOpt.isEmpty()) {
                throw new IllegalArgumentException("Prompt not found: " + promptId);
            }

            Prompt prompt = promptOpt.get();
            
            // Apply variables to prompt
            String processedMessage = PromptUtils.applyVariables(prompt.getMessage(), variables);
            
            // Create response format for structured output
            ResponseFormat responseFormat = ResponseFormat.jsonSchema(targetClass);
            
            // Create conversation context with token limit
            ConversationContext context = ConversationContext.from(chatStore, workflowId, chatId, getEffectiveMaxTokens());
            
            // Add system message - prefer prompt's system message
            String promptSystemMessage = prompt.getSystemMessage();
            if (StringUtils.isNotBlank(promptSystemMessage)) {
                promptSystemMessage = PromptUtils.applyVariables(promptSystemMessage, variables);
                context.addSystemMessage(promptSystemMessage);
            } else if (StringUtils.isNotBlank(systemMessage)) {
                context.addSystemMessage(systemMessage);
            }
            
            // Add user message
            context.addUserMessage(processedMessage);
            
            // Build request
            List<ModelContentMessage> messages = convertToModelContentMessages(context.getMessagesForRequest());
            ModelTextRequest request = ModelTextRequest.builder()
                .model(getEffectiveModel())
                .temperature(getTemperature(prompt))
                .messages(messages)
                .reasoningEffort(reasoningEffort)
                .responseFormat(responseFormat)
                .build();
            
            // Execute request
            ModelTextResponse response = modelClient.textToText(request);
            
            // Trace if provider is available
            RequestTracingProvider provider = getTracingProvider();
            if (provider != null) {
                String contextType = buildContextType("STRUCTURED_PROMPT");
                RequestTracingProvider.RequestContext traceContext = RequestTracingProvider.RequestContext.builder()
                    .contextId(agentId)
                    .contextType(contextType)
                    .promptId(promptId)
                    .variables(variables)
                    .chatId(chatId)
                    .workflowId(workflowId)
                    .workflowType(workflowType)
                    .workflowStep(workflowStep)
                    .build();
                provider.traceTextRequest(request, response, traceContext);
            }
            
            // Extract and parse response
            String jsonResponse = extractResponseText(response);
            
            // Add logging to see the actual JSON response
            log.info("Raw JSON response from AI: {}", jsonResponse);
            
            T result = JsonUtils.fromJson(jsonResponse, targetClass);
            
            return AgentResponse.structured(result);
            
        } catch (Exception e) {
            log.error("Error in executeStructuredWithPrompt", e);
            throw new RuntimeException("Failed to execute structured with prompt", e);
        }
    }

    private double getTemperature(Prompt prompt) {
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
            // Use injected PromptService or fall back to registry
            PromptService effectivePromptService = promptService != null ? 
                promptService : PromptServiceRegistry.getInstance();
                
            if (effectivePromptService == null) {
                throw new IllegalStateException("PromptService not configured. " +
                    "Please ensure PromptService is available in your application context " +
                    "or register one via PromptServiceRegistry.register()");
            }
            
            // Get prompt by ID
            Optional<Prompt> promptOpt = effectivePromptService.getCurrentPrompt(promptId, language);
            if (promptOpt.isEmpty()) {
                throw new IllegalArgumentException("Prompt not found: " + promptId);
            }
            
            Prompt prompt = promptOpt.get();
            
            // Apply variables to prompt
            String processedMessage = PromptUtils.applyVariables(prompt.getMessage(), variables);
            
            // Use system message from prompt if available
            String promptSystemMessage = prompt.getSystemMessage();
            if (StringUtils.isNotBlank(promptSystemMessage)) {
                promptSystemMessage = PromptUtils.applyVariables(promptSystemMessage, variables);
            }
            
            // Create conversation context with token limit
            ConversationContext context = ConversationContext.from(chatStore, workflowId, chatId, getEffectiveMaxTokens());
            
            // Add system message - prefer prompt's system message
            if (StringUtils.isNotBlank(promptSystemMessage)) {
                context.addSystemMessage(promptSystemMessage);
            } else if (StringUtils.isNotBlank(systemMessage)) {
                context.addSystemMessage(systemMessage);
            }
            
            // Add user message (saves to store if in history mode)
            context.addUserMessage(processedMessage);
            
            // Build request
            List<ModelContentMessage> messages = convertToModelContentMessages(context.getMessagesForRequest());
            ModelTextRequest request = ModelTextRequest.builder()
                .model(getEffectiveModel())
                .temperature(getTemperature(prompt))
                .messages(messages)
                .reasoningEffort(reasoningEffort)
                .build();
            
            // Execute request
            ModelTextResponse response = modelClient.textToText(request);
            
            // Trace if provider is available
            RequestTracingProvider provider = getTracingProvider();
            if (provider != null) {
                String contextType = buildContextType("PROMPT");
                RequestTracingProvider.RequestContext traceContext = RequestTracingProvider.RequestContext.builder()
                    .contextId(agentId)
                    .contextType(contextType)
                    .promptId(promptId)
                    .variables(variables)
                    .chatId(chatId)
                    .workflowId(workflowId)
                    .workflowType(workflowType)
                    .workflowStep(workflowStep)
                    .build();
                provider.traceTextRequest(request, response, traceContext);
            }
            
            // Extract response
            String responseText = extractResponseText(response);
            
            // Save assistant response
            if (StringUtils.isNotBlank(responseText)) {
                context.addAssistantMessage(responseText);
            }
            
            // Check if response contains images
            List<ModelContentElement.ImageData> images = extractImages(response);
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
            // Process prompt with variables if provided
            String processedPrompt = prompt;
            if (variables != null && !variables.isEmpty()) {
                processedPrompt = processMessageWithVariables(prompt, variables);
            }
            
            // Build image request using agent's imageModel field
            ModelImageRequest request = ModelImageRequest.builder()
                .prompt(processedPrompt)
                .model(imageModel)  // Use the agent's imageModel field!
                .build();
            
            // Execute request
            ModelImageResponse response = modelClient.textToImage(request);
            
            // Trace if provider is available
            RequestTracingProvider provider = getTracingProvider();
            if (provider != null) {
                String contextType = buildContextType("IMAGE_GEN");
                RequestTracingProvider.RequestContext traceContext = RequestTracingProvider.RequestContext.builder()
                    .contextId(agentId)
                    .contextType(contextType)
                    .variables(variables)
                    .chatId(chatId)
                    .workflowId(workflowId)
                    .workflowType(workflowType)
                    .workflowStep(workflowStep)
                    .build();
                provider.traceImageRequest(request, response, traceContext);
            }
            
            // Extract first image
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
            // Process text with variables if provided
            String processedText = text;
            if (variables != null && !variables.isEmpty()) {
                processedText = processMessageWithVariables(text, variables);
            }
            
            // Convert byte arrays to image data objects
            List<ModelContentElement.ImageData> imageDataObjects = imageDataList.stream()
                .map(bytes -> new ModelContentElement.ImageData(bytes, "image/jpeg"))
                .collect(Collectors.toList());
            
            // Build multimodal content
            List<ModelContentElement> content = buildMultimodalContent(processedText, imageDataObjects);
            
            // Create multimodal message
            ModelContentMessage userMessage = ModelContentMessage.builder()
                .role(Role.user)
                .content(content)
                .build();
            
            // Create conversation context with token limit
            ConversationContext context = ConversationContext.from(chatStore, workflowId, chatId, getEffectiveMaxTokens());
            
            // Add system message if present
            if (StringUtils.isNotBlank(systemMessage)) {
                context.addSystemMessage(systemMessage);
            }
            
            // Add to context (saves to store if in history mode)
            context.addUserMessage(processedText); // Add processed text version
            
            // Get all messages from context (includes system message and history)
            List<ModelContentMessage> messages = convertToModelContentMessages(context.getMessagesForRequest());
            
            // Add the multimodal message with image data
            messages.add(userMessage);
            
            // Build request
            ModelTextRequest request = ModelTextRequest.builder()
                .model(getEffectiveModel())
                .temperature(getTemperature(null))
                .messages(messages)
                .reasoningEffort(reasoningEffort)
                .build();
            
            // Execute request
            ModelTextResponse response = modelClient.imageToText(request);
            
            // Trace if provider is available
            RequestTracingProvider provider = getTracingProvider();
            if (provider != null) {
                String contextType = buildContextType("IMAGE");
                RequestTracingProvider.RequestContext traceContext = RequestTracingProvider.RequestContext.builder()
                    .contextId(agentId)
                    .contextType(contextType)
                    .variables(variables)
                    .chatId(chatId)
                    .workflowId(workflowId)
                    .workflowType(workflowType)
                    .workflowStep(workflowStep)
                    .build();
                provider.traceImageToTextRequest(request, response, traceContext);
            }
            
            // Extract response
            String responseText = extractResponseText(response);
            
            // Save assistant response
            if (StringUtils.isNotBlank(responseText)) {
                context.addAssistantMessage(responseText);
            }
            
            // Check if response contains images
            List<ModelContentElement.ImageData> images = extractImages(response);
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
            // Create response format for structured output
            ResponseFormat responseFormat = ResponseFormat.jsonSchema(targetClass);

            // Convert media content to ModelContentElement.ImageData (used for all media types)
            List<ModelContentElement.ImageData> mediaDataObjects = mediaContentList.stream()
                .map(media -> new ModelContentElement.ImageData(media.getData(), media.getMimeType()))
                .collect(Collectors.toList());

            // Build multimodal content
            List<ModelContentElement> content = buildMultimodalContent(text, mediaDataObjects);

            // Create multimodal message
            ModelContentMessage userMessage = ModelContentMessage.builder()
                .role(Role.user)
                .content(content)
                .build();

            // Create conversation context with token limit
            ConversationContext context = ConversationContext.from(chatStore, workflowId, chatId, getEffectiveMaxTokens());

            // Add system message if present
            if (StringUtils.isNotBlank(systemMessage)) {
                context.addSystemMessage(systemMessage);
            }

            // Add user message (saves to store if in history mode)
            context.addUserMessage(text);

            // Get all messages from context (includes system message and history)
            List<ModelContentMessage> messages = convertToModelContentMessages(context.getMessagesForRequest());

            // Add the multimodal message with media data
            messages.add(userMessage);

            // Build request with structured output
            ModelTextRequest request = ModelTextRequest.builder()
                .model(getEffectiveModel())
                .temperature(STRUCTURED_EXTRACTION_TEMPERATURE)
                .messages(messages)
                .reasoningEffort(reasoningEffort)
                .responseFormat(responseFormat)
                .build();

            // Execute request
            ModelTextResponse response = modelClient.imageToText(request);

            // Trace if provider is available
            RequestTracingProvider provider = getTracingProvider();
            if (provider != null) {
                String contextType = buildContextType("MEDIA_STRUCTURED");
                RequestTracingProvider.RequestContext traceContext = RequestTracingProvider.RequestContext.builder()
                    .contextId(agentId)
                    .contextType(contextType)
                    .chatId(chatId)
                    .workflowId(workflowId)
                    .workflowType(workflowType)
                    .workflowStep(workflowStep)
                    .build();
                provider.traceImageToTextRequest(request, response, traceContext);
            }

            // Extract and parse response
            String jsonResponse = extractResponseText(response);

            // Save assistant response
            if (StringUtils.isNotBlank(jsonResponse)) {
                context.addAssistantMessage(jsonResponse);
            }

            T result = JsonUtils.fromJson(jsonResponse, targetClass);

            return AgentResponse.structured(result);

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
        
        CompletableFuture<String> future = new CompletableFuture<>();
        
        try {
            // Process message with variables
            String processedMessage = processMessageWithVariables(input, variables);
            
            // Create conversation context with token limit
            ConversationContext context = ConversationContext.from(chatStore, workflowId, chatId, getEffectiveMaxTokens());
            
            // Add system message if present
            if (StringUtils.isNotBlank(systemMessage)) {
                context.addSystemMessage(systemMessage);
            }
            
            // Add user message (saves to store if in history mode)
            context.addUserMessage(processedMessage);
            
            // Build request
            List<ModelContentMessage> messages = convertToModelContentMessages(context.getMessagesForRequest());
            ModelTextRequest request = ModelTextRequest.builder()
                .model(getEffectiveModel())
                .temperature(getTemperature(null))
                .reasoningEffort(reasoningEffort)
                .messages(messages)
                .build();

            // Get streaming response from model client
            StreamingResponse<String> streamingResponse = modelClient.streamTextToText(request);
            
            // Create wrapper for memory and tracing
            final StringBuilder fullResponse = new StringBuilder();
            
            // Subscribe immediately with the provided callback
            streamingResponse.subscribe(new StreamingCallback<String>() {
                @Override
                public void onNext(String item) {
                    fullResponse.append(item);
                    callback.onNext(item);
                }
                
                @Override
                public void onError(Throwable error) {
                    log.error("Streaming error in LLMAgent", error);
                    callback.onError(error);
                    future.completeExceptionally(error);
                }
                
                @Override
                public void onComplete() {
                    // Save complete response
                    String finalResponse = fullResponse.toString();
                    if (finalResponse.length() > 0) {
                        context.addAssistantMessage(finalResponse);
                    }
                    
                    // Trace if provider is available
                    RequestTracingProvider provider = getTracingProvider();
                    if (provider != null) {
                        String contextType = buildContextType("STREAM");
                        RequestTracingProvider.RequestContext traceContext = RequestTracingProvider.RequestContext.builder()
                            .contextId(agentId)
                            .contextType(contextType)
                            .variables(variables)
                            .chatId(chatId)
                            .workflowId(workflowId)
                            .workflowType(workflowType)
                            .workflowStep(workflowStep)
                            .build();
                        // Create a synthetic response for tracing
                        ModelTextResponse syntheticResponse = ModelTextResponse.builder()
                            .choices(Collections.singletonList(
                                ResponseMessage.builder()
                                    .message(ModelMessage.builder()
                                        .role(Role.assistant)
                                        .content(finalResponse)
                                        .build())
                                    .build()
                            ))
                            .build();
                        provider.traceTextRequest(request, syntheticResponse, traceContext);
                    }
                    
                    callback.onComplete();
                    future.complete(finalResponse);
                }
            });
            
        } catch (Exception e) {
            log.error("Error in executeStreaming", e);
            callback.onError(new RuntimeException("Failed to execute streaming", e));
            future.completeExceptionally(new RuntimeException("Failed to execute streaming", e));
        }
        
        return future;
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
            .map(msg -> ModelContentMessage.create(
                msg.getRole(), 
                msg.getContent()
            ))
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
    
    
    private List<ModelContentElement.ImageData> extractImages(ModelTextResponse response) {
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
    
    private String extractResponseText(ModelTextResponse response) {
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
    private String getEffectiveModel() {
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
    private RequestTracingProvider getTracingProvider() {
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

        private List<ToolInfo> pendingTools = new ArrayList<>();
        
        public CustomLLMAgentBuilder() {
            // Set defaults
            this.autoExecuteTools = true;
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
            
            return new LLMAgent(modelClient, name, description, systemMessage,
                    temperature, maxTokens, model, imageModel, agentId,
                    chatStore, chatId, promptService, toolRegistry,
                    tracingProvider, workflowId, workflowType, workflowStep, 
                    autoExecuteTools, reasoningEffort);
        }
    }
}