package ai.driftkit.clients.gemini.client;

import ai.driftkit.clients.gemini.domain.*;
import ai.driftkit.clients.gemini.domain.GeminiContent.Part;
import ai.driftkit.clients.gemini.domain.GeminiGenerationConfig.ThinkingConfig;
import ai.driftkit.clients.gemini.domain.GeminiImageRequest.ImageGenerationConfig;
import ai.driftkit.clients.gemini.utils.GeminiUtils;
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
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
public class GeminiModelClient extends ModelClient implements ModelClientInit {
    
    public static final String GEMINI_DEFAULT = GeminiUtils.GEMINI_FLASH_2_5;
    public static final String GEMINI_SMART_DEFAULT = GeminiUtils.GEMINI_PRO_2_5;
    public static final String GEMINI_MINI_DEFAULT = GeminiUtils.GEMINI_FLASH_LITE_2_5;
    public static final String GEMINI_IMAGE_DEFAULT = GeminiUtils.GEMINI_IMAGE_MODEL;
    
    public static final String GEMINI_PREFIX = "gemini";
    
    private GeminiApiClient client;
    private VaultConfig config;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    
    @Override
    public ModelClient init(VaultConfig config) {
        this.config = config;
        this.client = GeminiClientFactory.createClient(
                config.getApiKey(),
                Optional.ofNullable(config.getBaseUrl()).orElse(null)
        );
        this.setTemperature(config.getTemperature());
        this.setModel(config.getModel());
        this.setStop(config.getStop());
        this.jsonObjectSupport = config.isJsonObject();
        return this;
    }
    
    public static ModelClient create(VaultConfig config) {
        GeminiModelClient modelClient = new GeminiModelClient();
        modelClient.init(config);
        return modelClient;
    }
    
    @Override
    public Set<Capability> getCapabilities() {
        return Set.of(
                Capability.TEXT_TO_TEXT,
                Capability.TEXT_TO_IMAGE,
                Capability.IMAGE_TO_TEXT,
                Capability.FUNCTION_CALLING,
                Capability.JSON_OBJECT,
                Capability.JSON_SCHEMA,
                Capability.TOOLS
                // Note: TTS (Text-to-Speech) and native audio capabilities are available
                // through experimental models but not yet exposed through standard capabilities
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
        super.textToImage(prompt);
        
        String message = prompt.getPrompt();
        String imageModel = Optional.ofNullable(prompt.getModel())
                .orElse(Optional.ofNullable(config.getImageModel())
                .orElse(GEMINI_IMAGE_DEFAULT));
        
        // For Gemini image generation, we need to use the special model with responseModalities
        GeminiContent userContent = GeminiContent.builder()
                .role("user")
                .parts(List.of(Part.builder()
                        .text(message)
                        .build()))
                .build();
        
        ImageGenerationConfig generationConfig = ImageGenerationConfig.builder()
                .temperature(getTemperature())
                .candidateCount(prompt.getN())
                .responseModalities(List.of("TEXT", "IMAGE"))
                .build();
        
        GeminiImageRequest imageRequest = GeminiImageRequest.builder()
                .contents(List.of(userContent))
                .generationConfig(generationConfig)
                .build();
        
        // Convert to standard chat request for the API
        GeminiChatRequest chatRequest = GeminiChatRequest.builder()
                .contents(imageRequest.getContents())
                .generationConfig(GeminiGenerationConfig.builder()
                        .temperature(generationConfig.getTemperature())
                        .candidateCount(generationConfig.getCandidateCount())
                        .build())
                .safetySettings(imageRequest.getSafetySettings())
                .build();
        
        try {
            GeminiChatResponse response = client.generateContent(imageModel, chatRequest);
            
            List<ModelContentElement.ImageData> images = new ArrayList<>();
            
            if (response.getCandidates() != null) {
                for (GeminiChatResponse.Candidate candidate : response.getCandidates()) {
                    if (candidate.getContent() == null || candidate.getContent().getParts() == null) {
                        continue;
                    }

                    for (Part part : candidate.getContent().getParts()) {
                        if (part.getInlineData() == null) {
                            continue;
                        }

                        GeminiUtils.ImageData imageData = GeminiUtils.base64toBytes(
                                part.getInlineData().getMimeType(),
                                part.getInlineData().getData()
                        );

                        images.add(new ModelContentElement.ImageData(
                                imageData.getImage(),
                                imageData.getMimeType()
                        ));
                    }
                }
            }
            
            return ModelImageResponse.builder()
                    .model(imageModel)
                    .bytes(images)
                    .createdTime(System.currentTimeMillis())
                    .build();
            
        } catch (Exception e) {
            log.error("Error generating image with Gemini", e);
            throw new RuntimeException("Failed to generate image", e);
        }
    }
    
    @Nullable
    private ModelTextResponse processPrompt(ModelTextRequest prompt) {
        String model = Optional.ofNullable(prompt.getModel()).orElse(getModel());
        
        // Build contents from messages
        List<GeminiContent> contents = new ArrayList<>();
        GeminiContent systemInstruction = null;
        
        for (ModelContentMessage message : prompt.getMessages()) {
            String role = message.getRole().name().toLowerCase();
            
            // Handle system messages as system instruction
            if ("system".equals(role)) {
                List<Part> parts = new ArrayList<>();
                for (ModelContentElement element : message.getContent()) {
                    if (element.getType() != ModelTextRequest.MessageType.text) {
                        continue;
                    }

                    parts.add(Part.builder()
                            .text(element.getText())
                            .build());
                }

                systemInstruction = GeminiContent.builder()
                        .parts(parts)
                        .build();

                continue;
            }
            
            // Map role appropriately
            if ("assistant".equals(role)) {
                role = "model";
            }
            
            List<Part> parts = new ArrayList<>();
            
            for (ModelContentElement element : message.getContent()) {
                switch (element.getType()) {
                    case text:
                        parts.add(Part.builder()
                                .text(element.getText())
                                .build());
                        break;
                    case image:
                        if (element.getImage() == null) {
                            continue;
                        }

                        parts.add(Part.builder()
                                .inlineData(GeminiUtils.createInlineData(
                                        element.getImage().getImage(),
                                        element.getImage().getMimeType()
                                ))
                                .build());
                        break;
                }
            }
            
            contents.add(GeminiContent.builder()
                    .role(role)
                    .parts(parts)
                    .build());
        }
        
        // Add system messages from config if not already present
        if (systemInstruction == null && CollectionUtils.isNotEmpty(getSystemMessages())) {
            List<String> systemMessages = getSystemMessages();
            List<Part> systemParts = systemMessages.stream()
                    .map(msg -> Part.builder().text(msg).build())
                    .collect(Collectors.toList());
            systemInstruction = GeminiContent.builder()
                    .parts(systemParts)
                    .build();
        }
        
        // Build generation config
        GeminiGenerationConfig.GeminiGenerationConfigBuilder configBuilder = GeminiGenerationConfig.builder()
                .temperature(Optional.ofNullable(prompt.getTemperature()).orElse(getTemperature()))
                .topP(getTopP())
                .maxOutputTokens(Optional.ofNullable(getMaxCompletionTokens()).orElse(getMaxTokens()))
                .stopSequences(getStop())
                .presencePenalty(getPresencePenalty())
                .frequencyPenalty(getFrequencyPenalty());
        
        // Handle structured output - works regardless of jsonObjectSupport flag
        if (prompt.getResponseFormat() != null) {
            if (prompt.getResponseFormat().getType() == ResponseFormat.ResponseType.JSON_OBJECT) {
                configBuilder.responseMimeType("application/json");
            } else if (prompt.getResponseFormat().getType() == ResponseFormat.ResponseType.JSON_SCHEMA) {
                configBuilder.responseMimeType("application/json");
                configBuilder.responseJsonSchema(GeminiUtils.convertToGeminiSchema(prompt.getResponseFormat().getJsonSchema()));
            }
        }
        
        // Handle logprobs
        if (Boolean.TRUE.equals(prompt.getLogprobs()) || Boolean.TRUE.equals(getLogprobs())) {
            configBuilder.responseLogprobs(true);
            configBuilder.logprobs(Optional.ofNullable(prompt.getTopLogprobs()).orElse(getTopLogprobs()));
        }
        
        // Handle reasoning/thinking for Gemini 2.5 models
        if (model != null && model.contains("2.5") && prompt.getReasoningEffort() != null) {
            ThinkingConfig.ThinkingConfigBuilder thinkingBuilder = ThinkingConfig.builder();
            
            switch (prompt.getReasoningEffort()) {
                case none:
                    thinkingBuilder.thinkingBudget(0); // Disable thinking
                    break;
                case low:
                    thinkingBuilder.thinkingBudget(4096); // Disable thinking
                    break;
                case medium:
                    thinkingBuilder.thinkingBudget(8192); // Disable thinking
                    break;
                case dynamic:
                    thinkingBuilder.thinkingBudget(-1); // Dynamic thinking
                    break;
                //128 to 32768
                case high:
                    // Use higher budget for pro models
                    if (model.contains("pro")) {
                        thinkingBuilder.thinkingBudget(32768); // Max for Pro
                    } else if (model.contains("lite")) {
                        //512 to 24576
                        thinkingBuilder.thinkingBudget(16384); // Mid-range for Lite
                    } else {
                        //0 to 24576
                        thinkingBuilder.thinkingBudget(16384); // Mid-range for Flash
                    }
                    thinkingBuilder.includeThoughts(true);
                    break;
            }
            
            configBuilder.thinkingConfig(thinkingBuilder.build());
        }
        
        GeminiGenerationConfig generationConfig = configBuilder.build();
        
        // Handle tools/functions
        List<GeminiTool> tools = null;
        GeminiChatRequest.ToolConfig toolConfig = null;
        
        if (prompt.getToolMode() != ToolMode.none) {
            List<Tool> modelTools = CollectionUtils.isNotEmpty(prompt.getTools()) ? prompt.getTools() : getTools();
            if (CollectionUtils.isNotEmpty(modelTools)) {
                tools = List.of(GeminiUtils.convertToGeminiTool(modelTools));
                
                // Set tool config based on mode
                String mode = prompt.getToolMode() == ToolMode.auto ? "AUTO" : "ANY";
                toolConfig = GeminiChatRequest.ToolConfig.builder()
                        .functionCallingConfig(GeminiChatRequest.ToolConfig.FunctionCallingConfig.builder()
                                .mode(mode)
                                .build())
                        .build();
            }
        }
        
        // Build the request
        GeminiChatRequest request = GeminiChatRequest.builder()
                .contents(contents)
                .systemInstruction(systemInstruction)
                .generationConfig(generationConfig)
                .tools(tools)
                .toolConfig(toolConfig)
                .build();
        
        try {
            GeminiChatResponse response = client.generateContent(model, request);
            return mapToModelTextResponse(response);
        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            throw new RuntimeException("Failed to call Gemini API", e);
        }
    }
    
    private ModelTextResponse mapToModelTextResponse(GeminiChatResponse response) {
        if (response == null) {
            return null;
        }
        
        List<ResponseMessage> choices = new ArrayList<>();
        
        if (response.getCandidates() != null) {
            for (int i = 0; i < response.getCandidates().size(); i++) {
                GeminiChatResponse.Candidate candidate = response.getCandidates().get(i);
                
                // Extract text content
                StringBuilder contentBuilder = new StringBuilder();
                List<ToolCall> toolCalls = new ArrayList<>();
                
                if (candidate.getContent() != null && candidate.getContent().getParts() != null) {
                    for (Part part : candidate.getContent().getParts()) {
                        if (part.getText() != null) {
                            contentBuilder.append(part.getText());
                        } else if (part.getFunctionCall() != null) {
                            // Convert function call to tool call
                            Map<String, JsonNode> arguments = new HashMap<>();
                            if (part.getFunctionCall().getArgs() != null) {
                                part.getFunctionCall().getArgs().forEach((key, value) -> {
                                    try {
                                        JsonNode node = ModelUtils.OBJECT_MAPPER.valueToTree(value);
                                        arguments.put(key, node);
                                    } catch (Exception e) {
                                        log.error("Error converting argument to JsonNode", e);
                                    }
                                });
                            }
                            
                            toolCalls.add(ToolCall.builder()
                                    .id(UUID.randomUUID().toString())
                                    .type("function")
                                    .function(ToolCall.FunctionCall.builder()
                                            .name(part.getFunctionCall().getName())
                                            .arguments(arguments)
                                            .build())
                                    .build());
                        }
                    }
                }
                
                String content = contentBuilder.toString();
                if (JsonUtils.isJSON(content) && !JsonUtils.isValidJSON(content)) {
                    content = JsonUtils.fixIncompleteJSON(content);
                }
                
                ModelMessage message = ModelMessage.builder()
                        .role(Role.assistant)
                        .content(content)
                        .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                        .build();
                
                // Handle logprobs if present
                LogProbs logProbs = null;
                if (candidate.getLogprobsResult() != null) {
                    List<LogProbs.TokenLogProb> tokenLogProbs = new ArrayList<>();
                    
                    if (candidate.getLogprobsResult().getChosenCandidates() != null) {
                        for (GeminiChatResponse.TopCandidate topCandidate : candidate.getLogprobsResult().getChosenCandidates()) {
                            List<LogProbs.TopLogProb> topLogProbs = new ArrayList<>();
                            
                            // Add the chosen candidate as the first top logprob
                            topLogProbs.add(LogProbs.TopLogProb.builder()
                                    .token(topCandidate.getToken())
                                    .logprob(topCandidate.getLogProbability())
                                    .build());
                            
                            // Add other top candidates if available
                            if (candidate.getLogprobsResult().getTopCandidates() != null) {
                                for (GeminiChatResponse.TopCandidate tc : candidate.getLogprobsResult().getTopCandidates()) {
                                    if (!tc.getToken().equals(topCandidate.getToken())) {
                                        topLogProbs.add(LogProbs.TopLogProb.builder()
                                                .token(tc.getToken())
                                                .logprob(tc.getLogProbability())
                                                .build());
                                    }
                                }
                            }
                            
                            tokenLogProbs.add(LogProbs.TokenLogProb.builder()
                                    .token(topCandidate.getToken())
                                    .logprob(topCandidate.getLogProbability())
                                    .topLogprobs(topLogProbs)
                                    .build());
                        }
                    }
                    
                    logProbs = LogProbs.builder()
                            .content(tokenLogProbs)
                            .build();
                }
                
                choices.add(ResponseMessage.builder()
                        .index(i)
                        .message(message)
                        .finishReason(candidate.getFinishReason())
                        .logprobs(logProbs)
                        .build());
            }
        }
        
        // Map usage
        Usage usage = null;
        if (response.getUsageMetadata() != null) {
            usage = new Usage(
                    response.getUsageMetadata().getPromptTokenCount(),
                    response.getUsageMetadata().getCandidatesTokenCount(),
                    response.getUsageMetadata().getTotalTokenCount()
            );
        }
        
        return ModelTextResponse.builder()
                .id(UUID.randomUUID().toString())
                .method("gemini.chat.completions")
                .createdTime(System.currentTimeMillis())
                .model(response.getModelVersion())
                .choices(choices)
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
            private SSESubscriber sseSubscriber;
            
            @Override
            public void subscribe(StreamingCallback<String> callback) {
                if (!active.compareAndSet(false, true)) {
                    callback.onError(new IllegalStateException("Stream already subscribed"));
                    return;
                }
                
                streamFuture = CompletableFuture.runAsync(() -> {
                    try {
                        sseSubscriber = processStreamingPrompt(prompt, callback, cancelled);
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
                if (sseSubscriber != null) {
                    sseSubscriber.cancel();
                }
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
    
    private SSESubscriber processStreamingPrompt(ModelTextRequest prompt, StreamingCallback<String> callback, AtomicBoolean cancelled) throws Exception {
        // Build Gemini request
        GeminiChatRequest request = buildGeminiRequest(prompt);
        
        // Add streaming specific configuration
        if (request.getGenerationConfig() == null) {
            request.setGenerationConfig(new GeminiGenerationConfig());
        }
        
        String model = Optional.ofNullable(prompt.getModel()).orElse(getModel());
        String apiKey = config.getApiKey();
        String baseUrl = Optional.ofNullable(config.getBaseUrl()).orElse("https://generativelanguage.googleapis.com");
        
        String requestBody = JsonUtils.toJson(request);
        
        // Build HTTP request for streaming with alt=sse parameter for SSE format
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1beta/models/" + model + ":streamGenerateContent?alt=sse"))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofMinutes(5))
                .build();
        
        // Create SSE subscriber
        SSESubscriber sseSubscriber = new SSESubscriber(callback, cancelled);
        
        // Send request with streaming response asynchronously
        httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.fromLineSubscriber(sseSubscriber))
                .thenAccept(response -> {
                    // Check for errors
                    if (response.statusCode() >= 400) {
                        callback.onError(new RuntimeException("Gemini API error: HTTP " + response.statusCode()));
                    }
                })
                .exceptionally(throwable -> {
                    callback.onError(throwable);
                    return null;
                });
        
        return sseSubscriber;
    }
    
    /**
     * SSE Subscriber for handling streaming responses from Gemini
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
        
        public void cancel() {
            if (subscription != null) {
                subscription.cancel();
            }
            completed = true;
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
                // Skip empty lines
                if (line.trim().isEmpty()) {
                    return;
                }
                
                // Handle SSE format
                String data = line;
                if (line.startsWith("data: ")) {
                    data = line.substring(6);
                    // Skip "[DONE]" marker
                    if (data.equals("[DONE]")) {
                        completed = true;
                        callback.onComplete();
                        return;
                    }
                }
                
                // Try to parse as JSON directly (each line should be a complete JSON object)
                if (data.trim().startsWith("{")) {
                    try {
                        GeminiChatResponse chunk = JsonUtils.fromJson(data, GeminiChatResponse.class);
                        
                        // Extract text from the chunk
                        if (chunk != null && chunk.getCandidates() != null && !chunk.getCandidates().isEmpty()) {
                            GeminiChatResponse.Candidate candidate = chunk.getCandidates().get(0);
                            if (candidate.getContent() != null && candidate.getContent().getParts() != null) {
                                for (Part part : candidate.getContent().getParts()) {
                                    if (part.getText() != null && !part.getText().isEmpty()) {
                                        callback.onNext(part.getText());
                                    }
                                }
                            }
                            
                            // Check if stream is finished
                            if (candidate.getFinishReason() != null) {
                                completed = true;
                                callback.onComplete();
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse chunk as JSON: {}", e.getMessage());
                        // Could accumulate in buffer for multi-line JSON, but Gemini typically sends complete JSON per line
                    }
                }
            } catch (Exception e) {
                log.error("Error processing streaming line: {}", line, e);
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
    
    private GeminiChatRequest buildGeminiRequest(ModelTextRequest prompt) {
        // Extract configuration
        Double temperature = Optional.ofNullable(prompt.getTemperature()).orElse(getTemperature());
        String model = Optional.ofNullable(prompt.getModel()).orElse(getModel());

        // Build Gemini contents
        List<GeminiContent> contents = new ArrayList<>();

        // Process messages
        for (ModelContentMessage message : prompt.getMessages()) {
            List<Part> parts = new ArrayList<>();

            if (message.getContent() != null) {
                for (ModelContentElement element : message.getContent()) {
                    if (element.getText() != null) {
                        parts.add(Part.builder().text(element.getText()).build());
                    }
                }
            }

            String role = message.getRole() == Role.assistant ? "model" : message.getRole().name();
            contents.add(GeminiContent.builder()
                    .role(role)
                    .parts(parts)
                    .build());
        }

        // Build generation config
        GeminiGenerationConfig.GeminiGenerationConfigBuilder configBuilder = GeminiGenerationConfig.builder()
                .temperature(temperature)
                .candidateCount(1);

        // Handle structured output for streaming
        if (prompt.getResponseFormat() != null) {
            if (prompt.getResponseFormat().getType() == ResponseFormat.ResponseType.JSON_OBJECT) {
                configBuilder.responseMimeType("application/json");
            } else if (prompt.getResponseFormat().getType() == ResponseFormat.ResponseType.JSON_SCHEMA) {
                configBuilder.responseMimeType("application/json");
                configBuilder.responseJsonSchema(GeminiUtils.convertToGeminiSchema(prompt.getResponseFormat().getJsonSchema()));
            }
        }

        GeminiGenerationConfig config = configBuilder.build();

        // Add tools if present
        List<GeminiTool> tools = null;
        if (prompt.getTools() != null && !prompt.getTools().isEmpty()) {
            GeminiTool tool = GeminiUtils.convertToGeminiTool(prompt.getTools());
            if (tool != null) {
                tools = List.of(tool);
            }
        }

        return GeminiChatRequest.builder()
                .contents(contents)
                .generationConfig(config)
                .tools(tools)
                .build();
    }
}