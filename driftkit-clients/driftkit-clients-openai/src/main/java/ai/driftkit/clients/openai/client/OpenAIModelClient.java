package ai.driftkit.clients.openai.client;

import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ModelClient.ModelClientInit;
import ai.driftkit.common.domain.client.ModelTextRequest.ReasoningEffort;
import ai.driftkit.common.domain.client.ResponseFormat.ResponseType;
import ai.driftkit.common.domain.streaming.StreamingResponse;
import ai.driftkit.common.domain.streaming.StreamingCallback;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.config.EtlConfig.VaultConfig;
import ai.driftkit.common.domain.client.ModelContentMessage.ModelContentElement;
import ai.driftkit.common.domain.client.ModelTextRequest.ToolMode;
import ai.driftkit.common.domain.client.ModelTextResponse.ResponseMessage;
import ai.driftkit.common.domain.client.ModelTextResponse.Usage;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.common.utils.ModelUtils;
import ai.driftkit.clients.openai.domain.ChatCompletionRequest;
import ai.driftkit.clients.openai.domain.ChatCompletionRequest.ContentMessage;
import ai.driftkit.clients.openai.domain.ChatCompletionRequest.Message;
import ai.driftkit.clients.openai.domain.ChatCompletionRequest.Message.ContentElement;
import ai.driftkit.clients.openai.domain.ChatCompletionRequest.Message.ImageContentElement;
import ai.driftkit.clients.openai.domain.ChatCompletionRequest.Message.TextContentElement;
import ai.driftkit.clients.openai.domain.ChatCompletionRequest.StringMessage;
import ai.driftkit.clients.openai.domain.ChatCompletionResponse;
import ai.driftkit.clients.openai.domain.ChatCompletionChunk;
import ai.driftkit.clients.openai.domain.CreateImageRequest;
import ai.driftkit.clients.openai.domain.CreateImageRequest.CreateImageRequestBuilder;
import ai.driftkit.clients.openai.domain.CreateImageRequest.Quality;
import ai.driftkit.clients.openai.domain.CreateImageResponse;
import ai.driftkit.clients.openai.utils.OpenAIUtils;
import ai.driftkit.clients.openai.utils.OpenAIUtils.ImageData;
import ai.driftkit.common.domain.client.LogProbs.TokenLogProb;
import ai.driftkit.common.domain.client.LogProbs.TopLogProb;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
public class OpenAIModelClient extends ModelClient implements ModelClientInit {
    public static final String GPT_DEFAULT = "gpt-4o";
    public static final String GPT_SMART_DEFAULT = "o3-mini";
    public static final String GPT_MINI_DEFAULT = "gpt-4o-mini";
    public static final String IMAGE_MODEL_DEFAULT = "dall-e-3";

    public static final String OPENAI_PREFIX = "openai";
    public static final String GPT_IMAGE_1 = "gpt-image-1";
    
    // Shared HttpClient for downloading images
    private static final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    OpenAIApiClient client;
    VaultConfig config;

    @Override
    public ModelClient init(VaultConfig config) {
        this.config = config;
        this.client = OpenAIClientFactory.createClient(
                config.getApiKey(),
                Optional.ofNullable(config.getBaseUrl()).orElse("https://api.openai.com")
        );
        this.setTemperature(config.getTemperature());
        this.setModel(config.getModel());
        this.setStop(config.getStop());
        this.jsonObjectSupport = config.isJsonObject();
        return this;
    }

    public static ModelClient create(VaultConfig config) {
        OpenAIModelClient modelClient = new OpenAIModelClient();
        modelClient.init(config);
        return modelClient;
    }

    @Override
    public Set<Capability> getCapabilities() {
        return Set.of(
                Capability.TEXT_TO_TEXT,
                Capability.TEXT_TO_IMAGE,
                Capability.FUNCTION_CALLING,
                Capability.IMAGE_TO_TEXT,
                Capability.JSON_OBJECT,
                Capability.TOOLS,
                Capability.JSON_SCHEMA
        );
    }

    @Override
    public ModelTextResponse imageToText(ModelTextRequest prompt) throws UnsupportedCapabilityException {
        super.imageToText(prompt);

        return processPrompt(prompt);
    }

    @Override
    public ModelImageResponse textToImage(ModelImageRequest prompt) {
        super.textToImage(prompt);

        String message = prompt.getPrompt();
        
        // Determine which model to use
        String imageModel = prompt.getModel();
        if (StringUtils.isBlank(imageModel)) {
            imageModel = config.getImageModel();
            if (StringUtils.isBlank(imageModel)) {
                imageModel = IMAGE_MODEL_DEFAULT;
            }
        }
        
        // Determine quality
        String qualityStr = prompt.getQuality();
        if (StringUtils.isBlank(qualityStr)) {
            qualityStr = config.getImageQuality();
            if (StringUtils.isBlank(qualityStr)) {
                qualityStr = "low";
            }
        }
        
        // Convert quality string to enum
        Quality quality;
        try {
            quality = Quality.valueOf(qualityStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid image quality value: {}, using 'low' as default", qualityStr);
            quality = Quality.low;
        }
        
        // Determine size
        String outputFormat = null;
        Integer compression = null;
        String size;
        if (GPT_IMAGE_1.equals(imageModel)) {
            // For gpt-image-1, always use "auto"
            size = "auto";
            outputFormat = "jpeg";
            compression = 90;
        } else {
            // For other models, use size from request or config
            size = prompt.getSize();
            if (StringUtils.isBlank(size)) {
                size = config.getImageSize();
                if (StringUtils.isBlank(size)) {
                    size = "1024x1024";
                }
            }

            switch (quality) {
                case low:
                case medium:
                    quality = Quality.standard;
                    break;
                case high:
                    quality = Quality.hd;
                    break;
            }
        }

        CreateImageRequestBuilder style = CreateImageRequest.builder()
                .prompt(message)
                .quality(quality)
                .size(size)
                .outputFormat(outputFormat)
                .compression(compression)
                .n(prompt.getN())
                .model(imageModel);

        CreateImageResponse imageResponse = client.createImage(style.build());

        // Process images in parallel for performance
        List<CompletableFuture<ModelContentElement.ImageData>> imageFutures = imageResponse.getData()
                .stream()
                .map(e -> CompletableFuture.supplyAsync(() -> {
                    try {
                        if (StringUtils.isNotBlank(e.getB64Json())) {
                            // Handle base64 encoded image
                            ImageData openAIImage = OpenAIUtils.base64toBytes("image/jpeg", e.getB64Json());
                            return new ModelContentElement.ImageData(openAIImage.getImage(), openAIImage.getMimeType());
                        } else if (StringUtils.isNotBlank(e.getUrl())) {
                            // Handle URL - download the image
                            return downloadImage(e.getUrl());
                        } else {
                            log.warn("Image data has neither base64 nor URL");
                            return null;
                        }
                    } catch (Exception ex) {
                        log.error("Error processing image data", ex);
                        return null;
                    }
                }))
                .toList();
        
        // Wait for all downloads to complete
        List<ModelContentElement.ImageData> image = imageFutures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();

        return ModelImageResponse.builder()
                .model(imageModel)
                .bytes(image)
                .createdTime(imageResponse.getCreated())
                .revisedPrompt(imageResponse.getData().getFirst().getRevisedPrompt())
                .build();
    }

    @Override
    public ModelTextResponse textToText(ModelTextRequest prompt) {
        super.textToText(prompt);

        return processPrompt(prompt);
    }
    
    @Override
    public StreamingResponse<String> streamTextToText(ModelTextRequest prompt) throws UnsupportedCapabilityException {
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
        List<Tool> tools = prompt.getToolMode() == ToolMode.none ? null : getTools();
        
        if (CollectionUtils.isEmpty(tools)) {
            tools = prompt.getTools();
        }
        
        String model = Optional.ofNullable(prompt.getModel()).orElse(getModel());
        Double temperature = Optional.ofNullable(prompt.getTemperature()).orElse(getTemperature());
        Boolean logprobs = Optional.ofNullable(prompt.getLogprobs()).orElse(getLogprobs());
        Integer topLogprobs = Optional.ofNullable(prompt.getTopLogprobs()).orElse(getTopLogprobs());
        
        ChatCompletionRequest.ResponseFormat responseFormat = prompt.getResponseFormat() == null || prompt.getResponseFormat().getType() == ResponseType.TEXT ? null : new ChatCompletionRequest.ResponseFormat(
            prompt.getResponseFormat().getType().getValue(),
            convertModelJsonSchema(prompt.getResponseFormat().getJsonSchema())
        );
        
        ChatCompletionRequest.ChatCompletionRequestBuilder reqBuilder = ChatCompletionRequest.builder()
                .model(model)
                .n(1)
                .stream(true)  // Enable streaming
                .maxTokens(getMaxTokens())
                .maxCompletionTokens(getMaxCompletionTokens())
                .temperature(temperature)
                .tools(tools)
                .responseFormat(responseFormat)
                .logprobs(logprobs)
                .messages(
                        prompt.getMessages().stream()
                            .map(this::toStreamingMessage)  // Use special converter for streaming
                            .toList()
                );
        
        // Only add topLogprobs if logprobs is enabled
        if (Boolean.TRUE.equals(logprobs)) {
            reqBuilder.topLogprobs(topLogprobs);
        }
        
        ChatCompletionRequest req = reqBuilder.build();
        
        // Prepare the HTTP request for SSE streaming
        String apiKey = config.getApiKey();
        String baseUrl = Optional.ofNullable(config.getBaseUrl()).orElse("https://api.openai.com");
        
        String requestBody = JsonUtils.toJson(req);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "text/event-stream")
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
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> errorResponse = httpClient.send(errorRequest, HttpResponse.BodyHandlers.ofString());
            log.error("Error response: {}", errorResponse.body());
            throw new RuntimeException("OpenAI API error: HTTP " + response.statusCode() + " - " + errorResponse.body());
        }
    }
    
    /**
     * SSE Subscriber for handling streaming responses
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
                // Check if this is an error response (JSON object starting with {)
                if (line.trim().startsWith("{") && line.contains("\"error\"")) {
                    // This is an error response, not SSE
                    log.error("Received error response: {}", line);
                    completed = true;
                    callback.onError(new RuntimeException("OpenAI API error: " + line));
                    // Cancel subscription for error case
                    if (subscription != null) {
                        subscription.cancel();
                    }
                    return;
                }
                
                // SSE format: lines starting with "data: "
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    
                    // Check for end of stream
                    if ("[DONE]".equals(data)) {
                        completed = true;
                        callback.onComplete();
                        return;  // Don't cancel subscription yet - let it complete naturally
                    }
                    
                    // Parse the JSON chunk
                    ChatCompletionChunk chunk = JsonUtils.fromJson(data, ChatCompletionChunk.class);
                    
                    // Extract content from the chunk
                    if (chunk != null && chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                        ChatCompletionChunk.ChunkChoice choice = chunk.getChoices().get(0);
                        if (choice != null && choice.getDelta() != null && choice.getDelta().getContent() != null) {
                            String content = choice.getDelta().getContent();
                            callback.onNext(content);
                        }
                        
                        // Check if stream is finished
                        if (choice != null && choice.getFinishReason() != null) {
                            // Don't call onComplete here - wait for [DONE] message
                        }
                    }
                }
                // Empty lines are part of SSE format, ignore them
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

    @Nullable
    private ModelTextResponse processPrompt(ModelTextRequest prompt) {
        List<Tool> tools = prompt.getToolMode() == ToolMode.none ? null : getTools();

        if (CollectionUtils.isEmpty(tools)) {
            tools = prompt.getTools();
        }

        String model = Optional.ofNullable(prompt.getModel()).orElse(getModel());
        Double temperature = Optional.ofNullable(prompt.getTemperature()).orElse(getTemperature());
        Boolean logprobs = Optional.ofNullable(prompt.getLogprobs()).orElse(getLogprobs());
        Integer topLogprobs = Optional.ofNullable(prompt.getTopLogprobs()).orElse(getTopLogprobs());

        ChatCompletionRequest.ResponseFormat responseFormat = prompt.getResponseFormat() == null || prompt.getResponseFormat().getType() == ResponseType.TEXT ? null : new ChatCompletionRequest.ResponseFormat(
            prompt.getResponseFormat().getType().getValue(),
            convertModelJsonSchema(prompt.getResponseFormat().getJsonSchema())
        );

        ChatCompletionRequest req = ChatCompletionRequest.builder()
                .model(model)
                .n(1)
                .maxTokens(getMaxTokens())
                .maxCompletionTokens(getMaxCompletionTokens())
                .temperature(temperature)
                .tools(tools)
                .responseFormat(responseFormat)
                .logprobs(logprobs)
                .topLogprobs(topLogprobs)
                .messages(
                        prompt.getMessages().stream()
                            .map(this::toMessage)
                            .toList()
                )
                .build();

        try {
            if (model != null && (model.startsWith("o") || model.equals("gpt-5"))) {
                req.setTemperature(null);
                ReasoningEffort effort = prompt.getReasoningEffort();

                if (effort == null) {
                    effort = ReasoningEffort.medium;
                }

                switch (effort) {
                    case dynamic:
                        effort = ReasoningEffort.high;
                        break;
                    case none:
                        effort = ReasoningEffort.minimal;
                        break;
                }

                req.setReasoningEffort(effort.name());
            }

            if (BooleanUtils.isNotTrue(req.getLogprobs())) {
                req.setTopLogprobs(null);
            }

            ChatCompletionResponse completion = client.createChatCompletion(req);

            return mapToModelTextResponse(completion);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ChatCompletionRequest.ResponseFormat.JsonSchema convertModelJsonSchema(ResponseFormat.JsonSchema schemaA) {
        if (schemaA == null) {
            return null;
        }

        Map<String, ChatCompletionRequest.ResponseFormat.Property> properties = schemaA.getProperties() != null
                ? schemaA.getProperties().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> convertSchemaProperty(entry.getValue())
                ))
                : null;

        ChatCompletionRequest.ResponseFormat.SchemaDefinition schemaDefinition = 
                new ChatCompletionRequest.ResponseFormat.SchemaDefinition(
                        schemaA.getType(),
                        properties,
                        new ArrayList<>(properties.keySet())
                );
        schemaDefinition.setAdditionalProperties(schemaA.getAdditionalProperties());

        ChatCompletionRequest.ResponseFormat.JsonSchema result = 
                new ChatCompletionRequest.ResponseFormat.JsonSchema(
                        schemaA.getTitle(),
                        schemaDefinition
                );
        
        // Set strict mode if specified in schema
        if (schemaA.getStrict() != null && schemaA.getStrict()) {
            result.setStrict(true);
        }
        
        return result;
    }

    private static ChatCompletionRequest.ResponseFormat.Property convertSchemaProperty(ResponseFormat.SchemaProperty property) {
        if (property == null) {
            return null;
        }

        Map<String, ChatCompletionRequest.ResponseFormat.Property> nestedProperties = null;
        if (property.getProperties() != null) {
            nestedProperties = property.getProperties().entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> convertSchemaProperty(entry.getValue())
                ));
        }

        ChatCompletionRequest.ResponseFormat.Property items = null;
        if (property.getItems() != null) {
            items = convertSchemaProperty(property.getItems());
        }

        ChatCompletionRequest.ResponseFormat.Property result = new ChatCompletionRequest.ResponseFormat.Property(
                property.getType(),
                property.getDescription(),
                property.getEnumValues()
        );

        if (nestedProperties != null) {
            result.setProperties(nestedProperties);
            result.setRequired(new ArrayList<>(nestedProperties.keySet()));
        } else if (property.getRequired() != null) {
            result.setRequired(property.getRequired());
        }
        if (items != null) {
            result.setItems(items);
        }
        
        // For objects, always set additionalProperties to false if not explicitly set
        if (ResponseFormatType.Object.getType().equals(property.getType())) {
            result.setAdditionalProperties(property.getAdditionalProperties() != null ? 
                property.getAdditionalProperties() : false);
        } else if (property.getAdditionalProperties() != null) {
            result.setAdditionalProperties(property.getAdditionalProperties());
        }

        return result;
    }

    public static ModelTextResponse mapToModelTextResponse(ChatCompletionResponse completion) {
        if (completion == null) {
            return null;
        }

        var usage = completion.getUsage();

        List<ResponseMessage> choices = completion.getChoices() != null ?
                completion.getChoices().stream()
                        .map(OpenAIModelClient::mapChoice)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList())
                : null;

        return ModelTextResponse.builder()
                .id(completion.getId())
                .method(completion.getObject())
                .createdTime(completion.getCreated())
                .model(completion.getModel())
                .usage(usage == null ? null : new Usage(
                        usage.getPromptTokens(),
                        usage.getCompletionTokens(),
                        usage.getTotalTokens()
                ))
                .choices(choices)
                .build();
    }

    private static ModelTextResponse.ResponseMessage mapChoice(ChatCompletionResponse.Choice choice) {
        if (choice == null) {
            return null;
        }

        return ModelTextResponse.ResponseMessage.builder()
                .index(choice.getIndex())
                .message(mapMessage(choice.getMessage()))
                .finishReason(choice.getFinishReason())
                .logprobs(mapLogProbs(choice.getLogprobs()))
                .build();
    }
    
    private static LogProbs mapLogProbs(ChatCompletionResponse.LogProbs logProbs) {
        if (logProbs == null) {
            return null;
        }
        
        List<TokenLogProb> tokenLogprobs = null;
        
        if (logProbs.getContent() != null) {
            tokenLogprobs = logProbs.getContent().stream()
                .map(token -> {
                    List<TopLogProb> topLogprobs = null;
                    
                    if (token.getTopLogprobs() != null) {
                        topLogprobs = token.getTopLogprobs().stream()
                            .map(top -> TopLogProb.builder()
                                .token(top.getToken())
                                .logprob(top.getLogprob())
                                .build())
                            .toList();
                    }
                    
                    return TokenLogProb.builder()
                        .token(token.getToken())
                        .logprob(token.getLogprob())
                        .bytes(token.getBytes())
                        .topLogprobs(topLogprobs)
                        .build();
                })
                .toList();
        }
        
        return LogProbs.builder()
            .content(tokenLogprobs)
            .build();
    }

    private static ModelMessage mapMessage(ChatCompletionResponse.Message message) {
        if (message == null) {
            return null;
        }

        String content = message.getContent();

        if (JsonUtils.isJSON(content) && !JsonUtils.isValidJSON(content)) {
            content = JsonUtils.fixIncompleteJSON(content);
        }

        List<ToolCall> toolCalls = null;
        if (message.getToolCalls() != null) {
            toolCalls = message.getToolCalls().stream()
                    .map(OpenAIModelClient::mapToolCall)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        return ModelMessage.builder()
                .role(Role.valueOf(message.getRole()))
                .content(content)
                .toolCalls(toolCalls)
                .build();
    }

    private static ToolCall mapToolCall(ChatCompletionResponse.ToolCall toolCall) {
        if (toolCall == null) {
            return null;
        }

        ToolCall.FunctionCall function = null;
        if (toolCall.getFunction() != null) {
            Map<String, JsonNode> arguments = parseJsonStringToNodeMap(toolCall.getFunction().getArguments());
            function = ToolCall.FunctionCall.builder()
                    .name(toolCall.getFunction().getName())
                    .arguments(arguments)
                    .build();
        }

        return ToolCall.builder()
                .id(toolCall.getId())
                .type(toolCall.getType())
                .function(function)
                .build();
    }

    private static Map<String, JsonNode> parseJsonStringToNodeMap(String jsonString) {
        try {
            if (jsonString == null || jsonString.trim().isEmpty()) {
                return new HashMap<>();
            }
            JsonNode rootNode = ModelUtils.OBJECT_MAPPER.readTree(jsonString);
            
            if (!rootNode.isObject()) {
                throw new IllegalArgumentException("Expected JSON object but got: " + rootNode.getNodeType());
            }
            
            Map<String, JsonNode> result = new HashMap<>();
            rootNode.fields().forEachRemaining(entry -> {
                result.put(entry.getKey(), entry.getValue());
            });
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse tool call arguments: " + e.getMessage(), e);
        }
    }

    /**
     * Convert message for streaming requests - always uses StringMessage for simple text
     */
    private Message toStreamingMessage(ModelContentMessage message) {
        // For streaming, we prefer StringMessage when possible for better compatibility
        if (message.getContent() != null && !message.getContent().isEmpty()) {
            // Check if all elements are text
            boolean allText = message.getContent().stream()
                .allMatch(e -> e.getType() == ModelTextRequest.MessageType.text);
            
            if (allText) {
                // Concatenate all text elements into a single string
                String combinedText = message.getContent().stream()
                    .map(ModelContentElement::getText)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" "));
                
                return new StringMessage(message.getRole().name(), message.getName(), combinedText);
            }
        }
        
        // Fall back to regular conversion for complex messages
        return toMessage(message);
    }
    
    private Message toMessage(ModelContentMessage message) {
//        if (message.getContent().size() == 1) {
//            ModelContentElement mce = message.getContent().get(0);
//
//            if (mce.getType() == MessageType.text) {
//                String text = mce.getText();
//                return new StringMessage(message.getRole().name(), message.getName(), text);
//            }
//        }

        List<ContentElement> elements = message.getContent().stream()
                .map(e -> {
                    switch (e.getType()) {
                        case image -> {
                            return new ImageContentElement(e.getImage().getImage());
                        }
                        case text -> {
                            String text = e.getText();

                            if (text.startsWith("\"")) {
                                text = text.substring(1, text.length() - 1);
                            }

                            return new TextContentElement(text);
                        }
                    }
                    return null;
                }).filter(Objects::nonNull).toList();

        return new ContentMessage(message.getRole().name(), message.getName(), elements);
    }
    
    /**
     * Download image from URL asynchronously
     */
    private static ModelContentElement.ImageData downloadImage(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() == 200) {
                // Detect MIME type from Content-Type header
                String contentType = response.headers().firstValue("Content-Type")
                        .orElse("image/jpeg");
                
                return new ModelContentElement.ImageData(response.body(), contentType);
            } else {
                log.error("Failed to download image from URL: {}, status: {}", url, response.statusCode());
                return null;
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error downloading image from URL: {}", url, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new CompletionException("Failed to download image", e);
        }
    }
}
