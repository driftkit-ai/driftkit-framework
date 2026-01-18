package ai.driftkit.clients.claude.client;

import ai.driftkit.clients.claude.domain.*;
import ai.driftkit.clients.claude.domain.ClaudeMessageRequest.ToolChoice;
import ai.driftkit.clients.claude.utils.ClaudeSchemaConverter;
import ai.driftkit.clients.claude.utils.ClaudeUtils;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ModelClient.ModelClientInit;
import ai.driftkit.common.domain.client.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelContentMessage.ModelContentElement;
import ai.driftkit.common.domain.client.ModelMessage;
import ai.driftkit.common.domain.client.ModelTextRequest.ToolMode;
import ai.driftkit.common.domain.client.ModelTextResponse.ResponseMessage;
import ai.driftkit.common.domain.client.ModelTextResponse.Usage;
import ai.driftkit.common.domain.streaming.StreamingCallback;
import ai.driftkit.common.domain.streaming.StreamingResponse;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.common.utils.ModelUtils;
import ai.driftkit.config.EtlConfig.VaultConfig;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ClaudeModelClient extends ModelClient implements ModelClientInit {
    
    public static final String CLAUDE_DEFAULT = ClaudeUtils.CLAUDE_SONNET_4;
    public static final String CLAUDE_SMART_DEFAULT = ClaudeUtils.CLAUDE_OPUS_4;
    public static final String CLAUDE_MINI_DEFAULT = ClaudeUtils.CLAUDE_HAIKU_3_5;
    
    public static final String CLAUDE_PREFIX = ClaudeUtils.CLAUDE_PREFIX;
    public static final int MAX_TOKENS = 8192;

    private ClaudeApiClient client;
    private VaultConfig config;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    
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

        // Apply structured outputs and tools configuration
        applyStructuredOutputsAndTools(requestBuilder, prompt);

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
    
    @Override
    public StreamingResponse<String> streamTextToText(ModelTextRequest prompt) {
        // Check if streaming is supported
        if (!getCapabilities().contains(Capability.TEXT_TO_TEXT)) {
            throw new UnsupportedCapabilityException("Text to text is not supported");
        }
        
        return new StreamingResponse<String>() {
            private final AtomicBoolean active = new AtomicBoolean(false);
            private final AtomicBoolean cancelled = new AtomicBoolean(false);
            private CompletableFuture<Void> streamFuture;
            
            @Override
            public void subscribe(StreamingCallback<String> callback) {
                if (!active.compareAndSet(false, true)) {
                    callback.onError(new IllegalStateException("Stream already subscribed"));
                    return;
                }
                
                streamFuture = CompletableFuture.runAsync(() -> {
                    try {
                        processStreamingPrompt(prompt, callback, cancelled);
                    } catch (Exception e) {
                        if (!cancelled.get()) {
                            callback.onError(e);
                        }
                    } finally {
                        active.set(false);
                    }
                });
            }
            
            @Override
            public void cancel() {
                cancelled.set(true);
                if (streamFuture != null) {
                    streamFuture.cancel(true);
                }
                active.set(false);
            }
            
            @Override
            public boolean isActive() {
                return active.get();
            }
        };
    }
    
    private void processStreamingPrompt(ModelTextRequest prompt, StreamingCallback<String> callback, AtomicBoolean cancelled) throws Exception {
        // Build Claude request with streaming enabled
        ClaudeMessageRequest request = buildClaudeRequest(prompt);
        request.setStream(true);
        
        String apiKey = config.getApiKey();
        String baseUrl = Optional.ofNullable(config.getBaseUrl()).orElse("https://api.anthropic.com");
        String requestBody = JsonUtils.toJson(request);
        
        // Build HTTP request for streaming
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/messages"))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofMinutes(5))
                .build();
        
        // Create SSE subscriber
        SSESubscriber sseSubscriber = new SSESubscriber(callback, cancelled);
        
        // Send request with streaming response
        HttpResponse<Void> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.fromLineSubscriber(sseSubscriber));
        
        // Check for errors
        if (response.statusCode() >= 400) {
            // Try to get error body
            HttpRequest errorRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> errorResponse = httpClient.send(errorRequest, HttpResponse.BodyHandlers.ofString());
            log.error("Error response: {}", errorResponse.body());
            throw new RuntimeException("Claude API error: HTTP " + response.statusCode() + " - " + errorResponse.body());
        }
    }
    
    /**
     * SSE Subscriber for handling streaming responses from Claude
     */
    private static class SSESubscriber implements Flow.Subscriber<String> {
        private final StreamingCallback<String> callback;
        private final AtomicBoolean cancelled;
        private Flow.Subscription subscription;
        private boolean completed = false;
        
        public SSESubscriber(StreamingCallback<String> callback, AtomicBoolean cancelled) {
            this.callback = callback;
            this.cancelled = cancelled;
        }
        
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }
        
        @Override
        public void onNext(String line) {
            if (cancelled.get() || completed) {
                // Don't cancel if already completed - just ignore further messages
                if (cancelled.get() && !completed) {
                    subscription.cancel();
                }
                return;
            }
            
            try {
                // Claude uses SSE format
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    
                    // Parse the JSON event
                    JsonNode eventNode = JsonUtils.fromJson(data, JsonNode.class);
                    if (eventNode != null) {
                        String eventType = eventNode.path("type").asText();
                        
                        switch (eventType) {
                            case "content_block_delta":
                                // Extract text delta
                                JsonNode delta = eventNode.path("delta");
                                String text = delta.path("text").asText(null);
                                if (text != null) {
                                    callback.onNext(text);
                                }
                                break;
                                
                            case "message_stop":
                                // Stream completed
                                completed = true;
                                callback.onComplete();
                                break;
                                
                            case "error":
                                // Error occurred
                                String errorMessage = eventNode.path("error").path("message").asText("Unknown error");
                                completed = true;
                                callback.onError(new RuntimeException("Claude API error: " + errorMessage));
                                break;
                                
                            // Ignore other event types like message_start, content_block_start, etc.
                            default:
                                break;
                        }
                    }
                } else if (line.startsWith("event: ")) {
                    // Claude also sends event type in separate line, we can ignore it
                    // as we parse the type from the JSON data
                }
            } catch (Exception e) {
                log.error("Error processing SSE line: {}", line, e);
                // Continue processing other lines
            }
        }
        
        @Override
        public void onError(Throwable throwable) {
            if (!cancelled.get() && !completed) {
                completed = true;
                callback.onError(throwable);
            }
        }
        
        @Override
        public void onComplete() {
            if (!cancelled.get() && !completed) {
                completed = true;
                callback.onComplete();
            }
        }
    }
    
    private ClaudeMessageRequest buildClaudeRequest(ModelTextRequest prompt) {
        // Extract messages and separate system message
        String systemMessage = null;
        List<ClaudeMessage> messages = new ArrayList<>();
        
        for (ModelContentMessage msg : prompt.getMessages()) {
            if (msg.getRole() == Role.system) {
                // Claude expects system message separately
                if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                    ModelContentElement element = msg.getContent().get(0);
                    if (element.getText() != null) {
                        systemMessage = element.getText();
                    }
                }
            } else {
                // Convert to Claude message format
                List<ClaudeContent> contents = new ArrayList<>();
                if (msg.getContent() != null) {
                    for (ModelContentElement element : msg.getContent()) {
                        if (element.getText() != null) {
                            contents.add(ClaudeContent.builder()
                                    .type("text")
                                    .text(element.getText())
                                    .build());
                        } else if (element.getImage() != null) {
                            contents.add(ClaudeContent.builder()
                                    .type("image")
                                    .source(ClaudeContent.ImageSource.builder()
                                            .type("base64")
                                            .mediaType(element.getImage().getMimeType())
                                            .data(Base64.getEncoder().encodeToString(element.getImage().getImage()))
                                            .build())
                                    .build());
                        }
                    }
                }
                
                messages.add(ClaudeMessage.builder()
                        .role(msg.getRole().name())
                        .content(contents)
                        .build());
            }
        }
        
        // Build request
        ClaudeMessageRequest.ClaudeMessageRequestBuilder builder = ClaudeMessageRequest.builder()
                .model(Optional.ofNullable(prompt.getModel()).orElse(getModel()))
                .messages(messages)
                .maxTokens(MAX_TOKENS)
                .temperature(Optional.ofNullable(prompt.getTemperature()).orElse(getTemperature()));

        if (systemMessage != null) {
            builder.system(systemMessage);
        }

        // Apply structured outputs and tools configuration
        applyStructuredOutputsAndTools(builder, prompt);

        return builder.build();
    }

    /**
     * Applies structured outputs and tools configuration to the request builder.
     * Extracted to avoid code duplication between processPrompt and buildClaudeRequest.
     *
     * @param builder Request builder to configure
     * @param prompt  Source prompt with format and tools settings
     */
    private void applyStructuredOutputsAndTools(
            ClaudeMessageRequest.ClaudeMessageRequestBuilder builder,
            ModelTextRequest prompt) {

        // Handle structured outputs (JSON schema)
        boolean strictMode = false;
        if (prompt.getResponseFormat() != null) {
            ClaudeOutputFormat outputFormat = ClaudeSchemaConverter.convert(prompt.getResponseFormat());
            if (outputFormat != null) {
                builder.outputFormat(outputFormat);
                log.debug("Structured output enabled with schema type: {}", outputFormat.getType());
            }
            // Check if strict mode should be enabled for tools
            if (prompt.getResponseFormat().getJsonSchema() != null &&
                Boolean.TRUE.equals(prompt.getResponseFormat().getJsonSchema().getStrict())) {
                strictMode = true;
            }
        }

        // Handle tools/functions
        List<Tool> modelTools = prompt.getTools();
        if (modelTools == null || modelTools.isEmpty()) {
            modelTools = getTools();
        }

        if (CollectionUtils.isNotEmpty(modelTools) && prompt.getToolMode() != ToolMode.none) {
            builder.tools(ClaudeUtils.convertToClaudeTools(modelTools, strictMode));

            if (prompt.getToolMode() == ToolMode.auto) {
                builder.toolChoice(ToolChoice.builder()
                        .type("auto")
                        .build());
            }
        }
    }
}