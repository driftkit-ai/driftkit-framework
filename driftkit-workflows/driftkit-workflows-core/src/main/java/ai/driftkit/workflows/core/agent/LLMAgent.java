package ai.driftkit.workflows.core.agent;

import ai.driftkit.common.domain.ChatMessageType;
import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.MessageType;
import ai.driftkit.workflows.core.chat.Message;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ModelImageRequest;
import ai.driftkit.common.domain.client.ModelImageResponse;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage.ModelContentElement;
import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.ModelTextResponse.ResponseMessage;
import ai.driftkit.workflows.core.chat.ChatMemory;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.common.tools.ToolInfo;
import ai.driftkit.common.tools.ToolRegistry;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.common.utils.AIUtils;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.context.core.registry.PromptServiceRegistry;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.context.core.util.PromptUtils;
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
    
    // Unique agent identifier
    private final String agentId;
    
    // Core components
    private final ChatMemory chatMemory;
    private final PromptService promptService;
    private final ToolRegistry toolRegistry;
    
    // Tracing support
    private final RequestTracingProvider tracingProvider;
    
    // Enable automatic tool execution
    private final boolean autoExecuteTools;
    
    // Default temperature for structured extraction
    private static final double STRUCTURED_EXTRACTION_TEMPERATURE = 0.1;
    
    // Constructor
    protected LLMAgent(ModelClient modelClient, String name, String description, String systemMessage,
                       Double temperature, Integer maxTokens, String model, String imageModel, String agentId,
                       ChatMemory chatMemory, PromptService promptService, ToolRegistry toolRegistry,
                       RequestTracingProvider tracingProvider, boolean autoExecuteTools) {
        this.modelClient = modelClient;
        this.name = name;
        this.description = description;
        this.systemMessage = systemMessage;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.model = model;
        this.imageModel = imageModel;
        this.agentId = agentId != null ? agentId : AIUtils.generateId();
        this.chatMemory = chatMemory;
        this.promptService = promptService;
        this.toolRegistry = toolRegistry;
        this.tracingProvider = tracingProvider;
        this.autoExecuteTools = autoExecuteTools;
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
            
            // Add user message to memory
            addUserMessage(processedMessage);
            
            // Build and execute request
            ModelTextRequest request = buildChatRequest();
            ModelTextResponse response = modelClient.textToText(request);
            
            // Trace if provider is available
            RequestTracingProvider provider = getTracingProvider();
            if (provider != null) {
                String contextType = buildContextType("TEXT");
                RequestTracingProvider.RequestContext traceContext = RequestTracingProvider.RequestContext.builder()
                    .contextId(agentId)
                    .contextType(contextType)
                    .variables(variables)
                    .build();
                provider.traceTextRequest(request, response, traceContext);
            }
            
            // Extract response
            return extractResponse(response);
            
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
            
            // Add user message to memory
            addUserMessage(processedMessage);
            
            // Build and execute request with tools
            ModelTextRequest request = buildChatRequestWithTools();
            ModelTextResponse response = modelClient.textToText(request);
            
            // Trace if provider is available
            RequestTracingProvider provider = getTracingProvider();
            if (provider != null) {
                String contextType = buildContextType("TOOL_CALLS");
                RequestTracingProvider.RequestContext traceContext = RequestTracingProvider.RequestContext.builder()
                    .contextId(agentId)
                    .contextType(contextType)
                    .variables(variables)
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
            
            // Add user message to memory
            addUserMessage(processedMessage);
            
            // Build and execute request with tools
            ModelTextRequest request = buildChatRequestWithTools();
            ModelTextResponse response = modelClient.textToText(request);
            
            // Trace if provider is available
            RequestTracingProvider provider = getTracingProvider();
            if (provider != null) {
                String contextType = buildContextType("TOOLS_EXEC");
                RequestTracingProvider.RequestContext traceContext = RequestTracingProvider.RequestContext.builder()
                    .contextId(agentId)
                    .contextType(contextType)
                    .variables(variables)
                    .build();
                provider.traceTextRequest(request, response, traceContext);
            }
            
            // Check for tool calls
            if (hasToolCalls(response)) {
                // Execute tools and get typed results
                List<ToolExecutionResult> results = executeToolsAndGetResults(response);
                
                // Get final response from model
                ModelTextRequest followUpRequest = buildChatRequest();
                ModelTextResponse finalResponse = modelClient.textToText(followUpRequest);
                
                // Trace follow-up request if provider is available
                if (provider != null) {
                    String contextType = buildContextType("TOOLS_FOLLOWUP");
                    RequestTracingProvider.RequestContext traceContext = RequestTracingProvider.RequestContext.builder()
                        .contextId(agentId)
                        .contextType(contextType)
                        .variables(variables)
                        .build();
                    provider.traceTextRequest(followUpRequest, finalResponse, traceContext);
                }
                
                // Add final response to memory
                String finalText = extractResponseText(finalResponse);
                addAssistantMessage(finalText);
                
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
    public <T> AgentResponse<T> executeStructured(String text, Class<T> targetClass) {
        return executeStructured(text, targetClass, null);
    }
    
    /**
     * Execute with structured output extraction and custom prompt
     */
    public <T> AgentResponse<T> executeStructured(String text, Class<T> targetClass, String customPrompt) {
        try {
            // Build extraction prompt
            String prompt = customPrompt != null ? customPrompt : 
                "Extract the following information from the text:\n\n" + text;
            
            // Create response format for structured output
            ResponseFormat responseFormat = ResponseFormat.jsonSchema(targetClass);
            
            // Build messages
            List<ModelContentMessage> messages = buildBaseMessages();
            messages.add(ModelContentMessage.create(Role.user, prompt));
            
            // Build request with structured output
            ModelTextRequest request = ModelTextRequest.builder()
                .model(getEffectiveModel())
                .temperature(temperature != null ? temperature : STRUCTURED_EXTRACTION_TEMPERATURE)
                .messages(messages)
                .responseFormat(responseFormat)
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
                    .build();
                provider.traceTextRequest(request, response, traceContext);
            }
            
            // Extract and parse response
            String jsonResponse = extractResponseText(response);
            T result = JsonUtils.fromJson(jsonResponse, targetClass);
            
            return AgentResponse.structured(result);
            
        } catch (Exception e) {
            log.error("Error extracting structured data", e);
            throw new RuntimeException("Failed to extract structured data", e);
        }
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
            Optional<Prompt> promptOpt = effectivePromptService.getPromptById(promptId);
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
            
            // Add messages to memory
            addUserMessage(processedMessage);
            
            // Build messages with prompt system message
            List<ModelContentMessage> messages = new ArrayList<>();
            if (StringUtils.isNotBlank(promptSystemMessage)) {
                messages.add(ModelContentMessage.create(Role.system, promptSystemMessage));
            } else if (StringUtils.isNotBlank(systemMessage)) {
                messages.add(ModelContentMessage.create(Role.system, systemMessage));
            }
            
            // Add conversation history
            messages.addAll(convertMemoryToMessages());
            
            // Build request
            ModelTextRequest request = ModelTextRequest.builder()
                .model(getEffectiveModel())
                .temperature(prompt.getTemperature() != null ? prompt.getTemperature() : getEffectiveTemperature())
                .messages(messages)
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
                    .build();
                provider.traceTextRequest(request, response, traceContext);
            }
            
            // Extract response
            return extractResponse(response);
            
        } catch (Exception e) {
            log.error("Error in executeWithPrompt", e);
            throw new RuntimeException("Failed to execute with prompt", e);
        }
    }
    
    /**
     * Execute image generation using the agent's imageModel field
     */
    public AgentResponse<ModelContentElement.ImageData> executeImageGeneration(String prompt) {
        try {
            // Build image request using agent's imageModel field
            ModelImageRequest request = ModelImageRequest.builder()
                .prompt(prompt)
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
     * Execute with multiple images
     */
    public AgentResponse<String> executeWithImages(String text, List<byte[]> imageDataList) {
        try {
            // Convert byte arrays to image data objects
            List<ModelContentElement.ImageData> imageDataObjects = imageDataList.stream()
                .map(bytes -> new ModelContentElement.ImageData(bytes, "image/jpeg"))
                .collect(Collectors.toList());
            
            // Build multimodal content
            List<ModelContentElement> content = buildMultimodalContent(text, imageDataObjects);
            
            // Create multimodal message
            ModelContentMessage userMessage = ModelContentMessage.builder()
                .role(Role.user)
                .content(content)
                .build();
            
            // Add to memory
            addUserMessage(text); // Add text version to memory
            
            // Build messages with system and multimodal content
            List<ModelContentMessage> messages = buildBaseMessages();
            messages.add(userMessage);
            
            // Build request
            ModelTextRequest request = ModelTextRequest.builder()
                .model(getEffectiveModel())
                .temperature(getEffectiveTemperature())
                .messages(messages)
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
                    .build();
                provider.traceImageToTextRequest(request, response, traceContext);
            }
            
            // Extract response
            return extractResponse(response);
            
        } catch (Exception e) {
            log.error("Error executing with images", e);
            throw new RuntimeException("Failed to execute with images", e);
        }
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
        if (chatMemory != null) {
            chatMemory.clear();
        }
    }
    
    /**
     * Get conversation history
     */
    public List<Message> getConversationHistory() {
        if (chatMemory != null) {
            return new ArrayList<>(chatMemory.messages());
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
    
    // Private helper methods
    
    private String processMessageWithVariables(String message, Map<String, Object> variables) {
        if (variables != null && !variables.isEmpty()) {
            return PromptUtils.applyVariables(message, variables);
        }
        return message;
    }
    
    private void addUserMessage(String content) {
        if (chatMemory != null) {
            Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .message(content)
                .type(ChatMessageType.USER)
                .messageType(MessageType.TEXT)
                .createdTime(System.currentTimeMillis())
                .requestInitTime(System.currentTimeMillis())
                .build();
            chatMemory.add(message);
        }
    }
    
    private void addAssistantMessage(String content) {
        if (chatMemory != null) {
            Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .message(content)
                .type(ChatMessageType.AI)
                .messageType(MessageType.TEXT)
                .createdTime(System.currentTimeMillis())
                .requestInitTime(System.currentTimeMillis())
                .build();
            chatMemory.add(message);
        }
    }
    
    private ModelTextRequest buildChatRequest() {
        List<ModelContentMessage> messages = buildBaseMessages();
        messages.addAll(convertMemoryToMessages());
        
        return ModelTextRequest.builder()
            .model(getEffectiveModel())
            .temperature(getEffectiveTemperature())
            .messages(messages)
            .build();
    }
    
    private ModelTextRequest buildChatRequestWithTools() {
        List<ModelContentMessage> messages = buildBaseMessages();
        messages.addAll(convertMemoryToMessages());
        
        ModelClient.Tool[] tools = toolRegistry.getTools();
        
        return ModelTextRequest.builder()
            .model(getEffectiveModel())
            .temperature(getEffectiveTemperature())
            .messages(messages)
            .tools(tools.length > 0 ? Arrays.asList(tools) : null)
            .build();
    }
    
    private List<ModelContentMessage> buildBaseMessages() {
        List<ModelContentMessage> messages = new ArrayList<>();
        
        // Add system message if present
        if (StringUtils.isNotBlank(systemMessage)) {
            messages.add(ModelContentMessage.create(Role.system, systemMessage));
        }
        
        return messages;
    }
    
    private List<ModelContentMessage> convertMemoryToMessages() {
        if (chatMemory == null) {
            return Collections.emptyList();
        }
        
        return chatMemory.messages().stream()
            .map(this::convertMessageToModelMessage)
            .collect(Collectors.toList());
    }
    
    private ModelContentMessage convertMessageToModelMessage(Message message) {
        Role role = switch (message.getType()) {
            case USER -> Role.user;
            case AI -> Role.assistant;
            case SYSTEM -> Role.system;
            default -> Role.user;
        };
        
        return ModelContentMessage.create(role, message.getMessage());
    }
    
    private boolean hasToolCalls(ModelTextResponse response) {
        return response != null && 
               CollectionUtils.isNotEmpty(response.getChoices()) &&
               response.getChoices().get(0).getMessage() != null &&
               CollectionUtils.isNotEmpty(response.getChoices().get(0).getMessage().getToolCalls());
    }
    
    private List<ToolCall> extractToolCalls(ModelTextResponse response) {
        if (!hasToolCalls(response)) {
            return Collections.emptyList();
        }
        
        return response.getChoices().get(0).getMessage().getToolCalls();
    }
    
    private AgentResponse<String> extractResponse(ModelTextResponse response) {
        String responseText = extractResponseText(response);
        addAssistantMessage(responseText);
        
        // Check if response contains images
        List<ModelContentElement.ImageData> images = extractImages(response);
        if (CollectionUtils.isNotEmpty(images)) {
            return AgentResponse.multimodal(responseText, images);
        }
        
        return AgentResponse.text(responseText);
    }
    
    private List<ModelContentElement.ImageData> extractImages(ModelTextResponse response) {
        // For now, text-to-text responses don't contain images
        // This is a placeholder for future multimodal responses
        return Collections.emptyList();
    }
    
    private List<ToolExecutionResult> executeToolsAndGetResults(ModelTextResponse response) {
        List<ToolCall> toolCalls = extractToolCalls(response);
        List<ToolExecutionResult> results = new ArrayList<>();
        
        // Add assistant message with tool calls to memory
        String assistantContent = extractResponseText(response);
        if (StringUtils.isNotBlank(assistantContent)) {
            addAssistantMessage(assistantContent);
        }
        
        // Execute each tool call
        for (ToolCall toolCall : toolCalls) {
            ToolExecutionResult result = executeToolCall(toolCall);
            results.add(result);
            
            // Add tool result to memory as user message
            String resultStr = result.isSuccess() ? 
                String.format("[Tool: %s]\nResult: %s", result.getToolName(), convertResultToString(result.getResult())) :
                String.format("[Tool: %s]\nError: %s", result.getToolName(), result.getError());
            
            addUserMessage(resultStr);
        }
        
        return results;
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

        ResponseMessage choice = response.getChoices().get(0);
        if (choice.getMessage() == null) {
            return "";
        }
        
        return choice.getMessage().getContent();
    }
    
    /**
     * Get effective model (from agent config or client default).
     */
    private String getEffectiveModel() {
        return StringUtils.isNotBlank(model) ? model : modelClient.getModel();
    }
    
    /**
     * Get effective temperature (from agent config or client default).
     */
    private Double getEffectiveTemperature() {
        return temperature != null ? temperature : modelClient.getTemperature();
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
        private ChatMemory chatMemory;
        private PromptService promptService;
        private ToolRegistry toolRegistry;
        private RequestTracingProvider tracingProvider;
        private boolean autoExecuteTools = true;
        
        private List<ToolInfo> pendingTools = new ArrayList<>();
        
        public CustomLLMAgentBuilder() {
            // Set defaults
            this.autoExecuteTools = true;
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
        
        public CustomLLMAgentBuilder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
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
                    chatMemory, promptService, toolRegistry,
                    tracingProvider, autoExecuteTools);
        }
    }
}