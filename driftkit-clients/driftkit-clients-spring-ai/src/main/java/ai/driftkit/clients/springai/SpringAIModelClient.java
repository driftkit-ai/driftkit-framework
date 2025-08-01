package ai.driftkit.clients.springai;

import ai.driftkit.common.domain.client.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.MimeType;
import org.springframework.core.io.ByteArrayResource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ModelClient implementation that wraps Spring AI's ChatModel.
 * This allows using Spring AI models within DriftKit's workflow system
 * with full tracing support.
 * 
 * Usage example:
 * <pre>
 * @Configuration
 * public class ModelClientConfig {
 *     
 *     @Bean
 *     public ModelClient springAIModelClient(ChatModel chatModel) {
 *         return new SpringAIModelClient(chatModel)
 *             .withModel("gpt-4")
 *             .withTemperature(0.7)
 *             .withMaxTokens(1000);
 *     }
 * }
 * </pre>
 */
@Slf4j
public class SpringAIModelClient extends ModelClient<Object> {
    
    private final ChatModel chatModel;
    private final ChatClient chatClient;
    
    // Configuration fields
    private String model;
    private List<String> systemMessages = new ArrayList<>();
    private Double temperature = 0.7;
    private Double topP;
    private List<String> stop;
    private boolean jsonObjectSupport = true;
    private Boolean logprobs;
    private Integer topLogprobs;
    private Integer maxTokens;
    private Integer maxCompletionTokens;
    
    public SpringAIModelClient(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.chatClient = ChatClient.builder(chatModel).build();
    }
    
    @Override
    public Set<Capability> getCapabilities() {
        Set<Capability> capabilities = new HashSet<>();
        capabilities.add(Capability.TEXT_TO_TEXT);
        capabilities.add(Capability.IMAGE_TO_TEXT);
        // Add more capabilities based on the specific ChatModel implementation
        return capabilities;
    }
    
    @Override
    public ModelTextResponse textToText(ModelTextRequest request) throws UnsupportedCapabilityException {
        try {
            // Convert DriftKit request to Spring AI format
            List<Message> messages = convertToSpringAIMessages(request.getMessages());
            
            // Build chat options
            ChatOptions options = buildChatOptions(request);
            
            // Create prompt
            Prompt prompt = new Prompt(messages, options);
            
            // Call the model
            ChatResponse response = chatModel.call(prompt);
            
            // Convert response back to DriftKit format
            return convertToDriftKitResponse(response, request);
            
        } catch (Exception e) {
            log.error("Error in textToText call", e);
            throw new RuntimeException("Failed to execute Spring AI model", e);
        }
    }
    
    @Override
    public ModelImageResponse textToImage(ModelImageRequest prompt) throws UnsupportedCapabilityException {
        throw new UnsupportedCapabilityException("Text to image not supported by Spring AI ChatModel");
    }
    
    @Override
    public ModelTextResponse imageToText(ModelTextRequest request) throws UnsupportedCapabilityException {
        try {
            // Convert messages including images
            List<Message> messages = convertToSpringAIMessagesWithImages(request.getMessages());
            
            // Build chat options
            ChatOptions options = buildChatOptions(request);
            
            // Create prompt
            Prompt prompt = new Prompt(messages, options);
            
            // Call the model
            ChatResponse response = chatModel.call(prompt);
            
            // Convert response back to DriftKit format
            return convertToDriftKitResponse(response, request);
            
        } catch (Exception e) {
            log.error("Error in imageToText call", e);
            throw new RuntimeException("Failed to execute Spring AI model with images", e);
        }
    }
    
    // Conversion methods
    
    private List<Message> convertToSpringAIMessages(List<ModelImageResponse.ModelContentMessage> driftKitMessages) {
        List<Message> messages = new ArrayList<>();
        
        // Add system messages first
        for (String systemMessage : systemMessages) {
            messages.add(new SystemMessage(systemMessage));
        }
        
        // Convert DriftKit messages
        for (ModelImageResponse.ModelContentMessage msg : driftKitMessages) {
            Message springAIMessage = convertMessage(msg);
            if (springAIMessage != null) {
                messages.add(springAIMessage);
            }
        }
        
        return messages;
    }
    
    private List<Message> convertToSpringAIMessagesWithImages(List<ModelImageResponse.ModelContentMessage> driftKitMessages) {
        List<Message> messages = new ArrayList<>();
        
        // Add system messages first
        for (String systemMessage : systemMessages) {
            messages.add(new SystemMessage(systemMessage));
        }
        
        // Convert DriftKit messages including multimodal content
        for (ModelImageResponse.ModelContentMessage msg : driftKitMessages) {
            Message springAIMessage = convertMessageWithImages(msg);
            if (springAIMessage != null) {
                messages.add(springAIMessage);
            }
        }
        
        return messages;
    }
    
    private Message convertMessage(ModelImageResponse.ModelContentMessage msg) {
        String content = extractTextContent(msg);
        
        switch (msg.getRole()) {
            case system:
                return new SystemMessage(content);
            case user:
                return new UserMessage(content);
            case assistant:
                return new AssistantMessage(content);
            default:
                log.warn("Unknown role: {}", msg.getRole());
                return new UserMessage(content);
        }
    }
    
    private Message convertMessageWithImages(ModelImageResponse.ModelContentMessage msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return convertMessage(msg);
        }
        
        // Check if message contains images
        List<Media> mediaList = new ArrayList<>();
        String textContent = "";
        
        for (ModelImageResponse.ModelContentMessage.ModelContentElement element : msg.getContent()) {
            if (element.getType() == ModelTextRequest.MessageType.text) {
                textContent += element.getText() + " ";
            } else if (element.getType() == ModelTextRequest.MessageType.image && element.getImage() != null) {
                // Convert to Spring AI Media using ByteArrayResource
                ByteArrayResource resource = new ByteArrayResource(element.getImage().getImage());
                Media media = new Media(
                    MimeType.valueOf(element.getImage().getMimeType()),
                    resource
                );
                mediaList.add(media);
            }
        }
        
        // Create message with media if available
        if (!mediaList.isEmpty() && msg.getRole() == Role.user) {
            return UserMessage.builder().media(mediaList.toArray(new Media[0])).text(textContent.trim()).build();
        } else {
            return convertMessage(msg);
        }
    }
    
    private String extractTextContent(ModelImageResponse.ModelContentMessage msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return "";
        }
        
        return msg.getContent().stream()
            .filter(element -> element.getType() == ModelTextRequest.MessageType.text)
            .map(ModelImageResponse.ModelContentMessage.ModelContentElement::getText)
            .collect(Collectors.joining(" "));
    }
    
    private ChatOptions buildChatOptions(ModelTextRequest request) {
        // Create a dynamic ChatOptions implementation
        return new ChatOptions() {
            private final String modelValue = request.getModel() != null ? request.getModel() : model;
            private final Double temperatureValue = request.getTemperature() != null ? request.getTemperature() : temperature;
            private final Double topPValue = topP;
            private final Integer topKValue = null; // Not supported in ModelTextRequest yet
            private final Integer maxTokensValue = maxTokens;
            private final List<String> stopSequencesValue = stop;
            private final Map<String, Object> additionalOptions = new HashMap<>();
            
            @Override
            public String getModel() {
                return modelValue;
            }
            
            @Override
            public Double getFrequencyPenalty() {
                return null; // Not supported in ModelTextRequest
            }
            
            @Override
            public Integer getMaxTokens() {
                return maxTokensValue;
            }
            
            @Override
            public Double getPresencePenalty() {
                return null; // Not supported in ModelTextRequest
            }
            
            @Override
            public List<String> getStopSequences() {
                return stopSequencesValue;
            }
            
            @Override
            public Double getTemperature() {
                return temperatureValue;
            }
            
            @Override
            public Integer getTopK() {
                return topKValue;
            }
            
            @Override
            public Double getTopP() {
                return topPValue;
            }
            
            @Override
            public ChatOptions copy() {
                return this; // Immutable, so return self
            }
            
            // Handle function calling if needed
            private final List<FunctionToolCallback> functionCallbacks;
            {
                if (CollectionUtils.isNotEmpty(request.getTools())) {
                    functionCallbacks = convertToSpringAIFunctions(request.getTools());
                    // Store both original tools and converted callbacks
                    additionalOptions.put("tools", request.getTools());
                    additionalOptions.put("functionCallbacks", functionCallbacks);
                    log.debug("Converted {} tools to Spring AI function callbacks", request.getTools().size());
                } else {
                    functionCallbacks = null;
                }
            }
            
            // Note: getFunctionCallbacks() is not a method in ChatOptions interface
            // Tool callbacks are handled differently in Spring AI 1.0.1
            
            // Method to retrieve additional options
            public Map<String, Object> getAdditionalOptions() {
                return Collections.unmodifiableMap(additionalOptions);
            }
        };
    }
    
    private List<FunctionToolCallback> convertToSpringAIFunctions(List<ModelClient.Tool> tools) {
        // Convert DriftKit tools to Spring AI 1.0.1 ToolCallback format
        if (CollectionUtils.isEmpty(tools)) {
            return new ArrayList<>();
        }
        
        List<FunctionToolCallback> callbacks = new ArrayList<>();
        
        for (ModelClient.Tool tool : tools) {
            try {
                // Create a function implementation using Java Function interface
                java.util.function.Function<Map<String, Object>, Map<String, Object>> functionImpl = params -> {
                    String toolName = tool.getFunction() != null ? tool.getFunction().getName() : "unknown";
                    log.debug("Executing tool: {} with params: {}", toolName, params);
                    
                    // Tool execution would be handled by the workflow/client consumer
                    Map<String, Object> result = new HashMap<>();
                    result.put("tool", toolName);
                    result.put("params", params);
                    result.put("status", "pending");
                    result.put("message", "Tool execution not implemented in Spring AI adapter");
                    
                    return result;
                };
                
                // Build the FunctionToolCallback with new API
                String name = tool.getFunction() != null ? tool.getFunction().getName() : "unknown";
                String description = tool.getFunction() != null ? tool.getFunction().getDescription() : "";
                
                FunctionToolCallback callback = FunctionToolCallback.builder(name, functionImpl)
                    .description(description)
                    .build();
                    
                callbacks.add(callback);
                
            } catch (Exception e) {
                String toolName = tool.getFunction() != null ? tool.getFunction().getName() : "unknown";
                log.error("Failed to convert tool {} to Spring AI format", toolName, e);
            }
        }
        
        log.debug("Converted {} DriftKit tools to Spring AI FunctionToolCallbacks", callbacks.size());
        return callbacks;
    }
    
    private ModelTextResponse convertToDriftKitResponse(ChatResponse springAIResponse, ModelTextRequest request) {
        ModelTextResponse.ModelTextResponseBuilder builder = ModelTextResponse.builder();
        
        // Set model
        builder.model(request.getModel() != null ? request.getModel() : model);
        
        // Convert generations to choices
        List<ModelTextResponse.ResponseMessage> choices = new ArrayList<>();
        
        if (springAIResponse.getResults() != null) {
            for (Generation generation : springAIResponse.getResults()) {
                String content = generation.getOutput().getText();
                
                ModelTextResponse.ResponseMessage choice = ModelTextResponse.ResponseMessage.builder()
                    .message(ModelImageResponse.ModelMessage.builder()
                        .role(Role.assistant)
                        .content(content)
                        .build())
                    .build();
                    
                choices.add(choice);
            }
        }
        
        builder.choices(choices);

        // Extract usage if available
        // Note: Spring AI 1.0.1 Usage API has changed
        if (springAIResponse.getMetadata() != null && springAIResponse.getMetadata().getUsage() != null) {
            var usage = springAIResponse.getMetadata().getUsage();
            
            // Try to extract token counts - API may vary by provider
            try {
                Integer promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0;
                Integer totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens().intValue() : 0;
                Integer completionTokens = totalTokens - promptTokens;
                
                builder.usage(ModelTextResponse.Usage.builder()
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .build());
            } catch (Exception e) {
                log.debug("Unable to extract token usage from Spring AI response", e);
            }
        }
        
        return builder.build();
    }
    
    // Builder pattern methods
    
    public SpringAIModelClient withModel(String model) {
        this.model = model;
        return this;
    }
    
    public SpringAIModelClient withSystemMessage(String systemMessage) {
        this.systemMessages.add(systemMessage);
        return this;
    }
    
    public SpringAIModelClient withTemperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }
    
    public SpringAIModelClient withTopP(Double topP) {
        this.topP = topP;
        return this;
    }
    
    public SpringAIModelClient withMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }
    
    public SpringAIModelClient withStop(List<String> stop) {
        this.stop = stop;
        return this;
    }
    
    // Getters and setters for ModelClient interface
    
    @Override
    public String getModel() {
        return model;
    }
    
    @Override
    public void setModel(String model) {
        this.model = model;
    }
    
    @Override
    public List<String> getSystemMessages() {
        return systemMessages;
    }
    
    @Override
    public void setSystemMessages(List<String> systemMessages) {
        this.systemMessages = systemMessages;
    }
    
    @Override
    public Double getTemperature() {
        return temperature;
    }
    
    @Override
    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }
    
    @Override
    public Double getTopP() {
        return topP;
    }
    
    @Override
    public void setTopP(Double topP) {
        this.topP = topP;
    }
    
    @Override
    public List<String> getStop() {
        return stop;
    }
    
    @Override
    public void setStop(List<String> stop) {
        this.stop = stop;
    }
    
    @Override
    public boolean isJsonObjectSupport() {
        return jsonObjectSupport;
    }
    
    @Override
    public void setJsonObjectSupport(boolean jsonObjectSupport) {
        this.jsonObjectSupport = jsonObjectSupport;
    }
    
    @Override
    public Boolean getLogprobs() {
        return logprobs;
    }
    
    @Override
    public void setLogprobs(Boolean logprobs) {
        this.logprobs = logprobs;
    }
    
    @Override
    public Integer getTopLogprobs() {
        return topLogprobs;
    }
    
    @Override
    public void setTopLogprobs(Integer topLogprobs) {
        this.topLogprobs = topLogprobs;
    }
    
    @Override
    public Integer getMaxTokens() {
        return maxTokens;
    }
    
    @Override
    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
    
    @Override
    public Integer getMaxCompletionTokens() {
        return maxCompletionTokens;
    }
    
    @Override
    public void setMaxCompletionTokens(Integer maxCompletionTokens) {
        this.maxCompletionTokens = maxCompletionTokens;
    }
}