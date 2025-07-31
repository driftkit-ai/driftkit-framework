package ai.driftkit.clients.claude.client;

import ai.driftkit.clients.claude.domain.*;
import ai.driftkit.clients.claude.domain.ClaudeMessageRequest.ToolChoice;
import ai.driftkit.clients.claude.utils.ClaudeUtils;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ModelClient.ModelClientInit;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage.ModelContentElement;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelMessage;
import ai.driftkit.common.domain.client.ModelTextRequest.ToolMode;
import ai.driftkit.common.domain.client.ModelTextResponse.ResponseMessage;
import ai.driftkit.common.domain.client.ModelTextResponse.Usage;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.common.utils.ModelUtils;
import ai.driftkit.config.EtlConfig.VaultConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ClaudeModelClient extends ModelClient implements ModelClientInit {
    
    public static final String CLAUDE_DEFAULT = ClaudeUtils.CLAUDE_SONNET_4;
    public static final String CLAUDE_SMART_DEFAULT = ClaudeUtils.CLAUDE_OPUS_4;
    public static final String CLAUDE_MINI_DEFAULT = ClaudeUtils.CLAUDE_HAIKU_3_5;
    
    public static final String CLAUDE_PREFIX = ClaudeUtils.CLAUDE_PREFIX;
    public static final int MAX_TOKENS = 8192;

    private ClaudeApiClient client;
    private VaultConfig config;
    
    @Override
    public ModelClient init(VaultConfig config) {
        this.config = config;
        this.client = ClaudeClientFactory.createClient(
                config.getApiKey(),
                config.getBaseUrl()
        );
        this.setTemperature(config.getTemperature());
        this.setModel(config.getModel());
        this.setStop(config.getStop());
        this.jsonObjectSupport = config.isJsonObject();
        return this;
    }
    
    public static ModelClient create(VaultConfig config) {
        ClaudeModelClient modelClient = new ClaudeModelClient();
        modelClient.init(config);
        return modelClient;
    }
    
    @Override
    public Set<Capability> getCapabilities() {
        return Set.of(
                Capability.TEXT_TO_TEXT,
                Capability.IMAGE_TO_TEXT,
                Capability.FUNCTION_CALLING,
                Capability.JSON_OBJECT,
                Capability.JSON_SCHEMA,
                Capability.TOOLS
                // Note: TEXT_TO_IMAGE is not supported by Claude
        );
    }
    
    @Override
    public ModelTextResponse textToText(ModelTextRequest prompt) {
        super.textToText(prompt);
        return processPrompt(prompt);
    }
    
    @Override
    public ModelTextResponse imageToText(ModelTextRequest prompt) throws UnsupportedCapabilityException {
        super.imageToText(prompt);
        return processPrompt(prompt);
    }
    
    @Override
    public ModelImageResponse textToImage(ModelImageRequest prompt) {
        throw new UnsupportedCapabilityException("Claude does not support image generation");
    }
    
    private ModelTextResponse processPrompt(ModelTextRequest prompt) {
        String model = Optional.ofNullable(prompt.getModel())
                .orElse(Optional.ofNullable(getModel())
                .orElse(CLAUDE_DEFAULT));
        
        // Build messages
        List<ClaudeMessage> messages = new ArrayList<>();
        String systemPrompt = null;
        
        for (ModelContentMessage message : prompt.getMessages()) {
            String role = message.getRole().name().toLowerCase();
            
            // Handle system messages separately
            if ("system".equals(role)) {
                StringBuilder systemBuilder = new StringBuilder();
                for (ModelContentElement element : message.getContent()) {
                    if (element.getType() == ModelTextRequest.MessageType.text) {
                        systemBuilder.append(element.getText());
                    }
                }
                systemPrompt = systemBuilder.toString();
                continue;
            }
            
            // Convert content elements to Claude content blocks
            List<ClaudeContent> contents = new ArrayList<>();
            
            for (ModelContentElement element : message.getContent()) {
                switch (element.getType()) {
                    case text:
                        contents.add(ClaudeContent.text(element.getText()));
                        break;
                    case image:
                        if (element.getImage() != null) {
                            contents.add(ClaudeContent.image(
                                    ClaudeUtils.bytesToBase64(element.getImage().getImage()),
                                    element.getImage().getMimeType()
                            ));
                        }
                        break;
                }
            }
            
            messages.add(ClaudeMessage.contentMessage(role, contents));
        }
        
        // Add system messages from config if not already present
        if (systemPrompt == null && CollectionUtils.isNotEmpty(getSystemMessages())) {
            systemPrompt = String.join("\n", getSystemMessages());
        }
        
        // Build request
        Integer maxTokens = Optional.ofNullable(getMaxCompletionTokens()).orElse(getMaxTokens());

        if (maxTokens == null) {
            maxTokens = Optional.ofNullable(config.getMaxTokens()).orElse(MAX_TOKENS);
        }

        ClaudeMessageRequest.ClaudeMessageRequestBuilder requestBuilder = ClaudeMessageRequest.builder()
                .model(model)
                .messages(messages)
                .maxTokens(maxTokens)
                .temperature(Optional.ofNullable(prompt.getTemperature()).orElse(getTemperature()))
                .topP(getTopP())
                .stopSequences(getStop())
                .system(systemPrompt);
        
        // Handle tools/functions
        if (prompt.getToolMode() != ToolMode.none) {
            List<Tool> modelTools = CollectionUtils.isNotEmpty(prompt.getTools()) ? prompt.getTools() : getTools();
            if (CollectionUtils.isNotEmpty(modelTools)) {
                requestBuilder.tools(ClaudeUtils.convertToClaudeTools(modelTools));
                
                // Set tool choice based on mode
                if (prompt.getToolMode() == ToolMode.auto) {
                    requestBuilder.toolChoice(ToolChoice.builder()
                            .type("auto")
                            .build());
                }
            }
        }
        
        ClaudeMessageRequest request = requestBuilder.build();
        
        try {
            ClaudeMessageResponse response = client.createMessage(request);
            return mapToModelTextResponse(response);
        } catch (Exception e) {
            log.error("Error calling Claude API", e);
            throw new RuntimeException("Failed to call Claude API", e);
        }
    }
    
    private ModelTextResponse mapToModelTextResponse(ClaudeMessageResponse response) {
        if (response == null) {
            return null;
        }
        
        // Extract content and tool calls
        StringBuilder contentBuilder = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        
        if (response.getContent() != null) {
            for (ClaudeContent content : response.getContent()) {
                if ("text".equals(content.getType())) {
                    contentBuilder.append(content.getText());
                } else if ("tool_use".equals(content.getType())) {
                    // Convert tool use to tool call
                    Map<String, JsonNode> arguments = new HashMap<>();
                    if (content.getInput() != null) {
                        content.getInput().forEach((key, value) -> {
                            try {
                                JsonNode node = ModelUtils.OBJECT_MAPPER.valueToTree(value);
                                arguments.put(key, node);
                            } catch (Exception e) {
                                log.error("Error converting argument to JsonNode", e);
                            }
                        });
                    }
                    
                    toolCalls.add(ToolCall.builder()
                            .id(content.getId())
                            .type("function")
                            .function(ToolCall.FunctionCall.builder()
                                    .name(content.getName())
                                    .arguments(arguments)
                                    .build())
                            .build());
                }
            }
        }
        
        String textContent = contentBuilder.toString();
        if (JsonUtils.isJSON(textContent) && !JsonUtils.isValidJSON(textContent)) {
            textContent = JsonUtils.fixIncompleteJSON(textContent);
        }
        
        ModelMessage message = ModelMessage.builder()
                .role(Role.assistant)
                .content(textContent)
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                .build();
        
        ResponseMessage choice = ResponseMessage.builder()
                .index(0)
                .message(message)
                .finishReason(response.getStopReason())
                .build();
        
        // Map usage
        Usage usage = null;
        if (response.getUsage() != null) {
            int totalTokens = (response.getUsage().getInputTokens() != null ? response.getUsage().getInputTokens() : 0) +
                             (response.getUsage().getOutputTokens() != null ? response.getUsage().getOutputTokens() : 0);
            usage = new Usage(
                    response.getUsage().getInputTokens(),
                    response.getUsage().getOutputTokens(),
                    totalTokens
            );
        }
        
        return ModelTextResponse.builder()
                .id(response.getId())
                .method("claude.messages")
                .createdTime(System.currentTimeMillis())
                .model(response.getModel())
                .choices(List.of(choice))
                .usage(usage)
                .build();
    }
}