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
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.common.utils.ModelUtils;
import ai.driftkit.config.EtlConfig.VaultConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
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

    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String VERTEX_LOCATION_GLOBAL = "global";

    /**
     * Lenient mapper for SSE stream chunks. Gemini adds fields over time (e.g.
     * {@code thoughtSignature} on think-model parts) that the domain classes don't model;
     * the Feign/unary decoder ignores unknown fields, so streaming must too — otherwise a
     * chunk silently drops and its text is lost from the accumulated response.
     */
    private static final ObjectMapper STREAM_CHUNK_MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private GeminiApiClient client;
    private VaultConfig config;
    private boolean vertexMode;
    private boolean regionalVertex;
    private GoogleCredentials vertexCredentials;
    private String vertexProject;
    private String vertexLocation;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static GeminiChatResponse parseStreamChunk(String data) throws IOException {
        return STREAM_CHUNK_MAPPER.readValue(data, GeminiChatResponse.class);
    }
    
    @Override
    public ModelClient init(VaultConfig config) {
        this.config = config;

        String vProject = config.getVertexProject();
        if (vProject != null && !vProject.isBlank()) {
            try {
                this.vertexCredentials = GoogleCredentials.getApplicationDefault()
                        .createScoped("https://www.googleapis.com/auth/cloud-platform");
                this.vertexMode = true;
                this.vertexProject = vProject;
                this.vertexLocation = config.getVertexLocation() != null
                        ? config.getVertexLocation() : VERTEX_LOCATION_GLOBAL;
                this.regionalVertex = !VERTEX_LOCATION_GLOBAL.equals(vertexLocation);

                this.client = GeminiClientFactory.createClient(
                        createBearerInterceptor(), resolveBaseUrl(),
                        config.getConnectTimeout(), config.getReadTimeout());
                log.info("Gemini client initialized in Vertex AI mode: project={}, location={}",
                        vertexProject, vertexLocation);
            } catch (IOException e) {
                log.error("Failed to load ADC for Vertex AI, falling back to API key: {}", e.getMessage());
                this.vertexMode = false;
            }
        }

        if (!vertexMode) {
            this.client = GeminiClientFactory.createClient(
                    config.getApiKey(),
                    Optional.ofNullable(config.getBaseUrl()).orElse(null),
                    config.getConnectTimeout(),
                    config.getReadTimeout()
            );
            log.info("Gemini client initialized in API key mode");
        }

        this.setTemperature(config.getTemperature());
        this.setModel(config.getModel());
        this.setStop(config.getStop());
        this.jsonObjectSupport = config.isJsonObject();
        return this;
    }

    private RequestInterceptor createBearerInterceptor() {
        return requestTemplate -> {
            try {
                vertexCredentials.refreshIfExpired();
                requestTemplate.header("Authorization",
                        "Bearer " + vertexCredentials.getAccessToken().getTokenValue());
            } catch (IOException e) {
                throw new RuntimeException("Failed to refresh Vertex AI credentials", e);
            }
        };
    }

    private String resolveBaseUrl() {
        if (vertexMode) {
            if (regionalVertex) {
                return "https://" + vertexLocation + "-aiplatform.googleapis.com";
            }
            return "https://aiplatform.googleapis.com";
        }
        return Optional.ofNullable(config.getBaseUrl()).orElse(DEFAULT_BASE_URL);
    }

    private void applyAuth(HttpRequest.Builder builder) throws IOException {
        if (vertexMode) {
            vertexCredentials.refreshIfExpired();
            builder.header("Authorization",
                    "Bearer " + vertexCredentials.getAccessToken().getTokenValue());
        } else {
            builder.header("x-goog-api-key", config.getApiKey());
        }
    }

    private String buildEndpointUrl(String model, String action) {
        if (vertexMode) {
            String vertexPath = "/projects/" + vertexProject + "/locations/" + vertexLocation
                    + "/publishers/google/models/" + model + ":" + action;
            if (regionalVertex) {
                return "https://" + vertexLocation + "-aiplatform.googleapis.com/v1" + vertexPath;
            }
            return "https://aiplatform.googleapis.com/v1beta1" + vertexPath;
        }
        String baseUrl = Optional.ofNullable(config.getBaseUrl()).orElse(DEFAULT_BASE_URL);
        return baseUrl + "/v1beta/models/" + model + ":" + action;
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
    public boolean supportsToolMessages() {
        return true;
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
            GeminiChatResponse response = callGenerateContent(imageModel, chatRequest);
            
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
    
    private GeminiChatResponse callGenerateContent(String model, GeminiChatRequest request) {
        if (vertexMode) {
            if (regionalVertex) {
                return client.vertexGenerateContent(vertexProject, vertexLocation, model, request);
            }
            return client.vertexBetaGenerateContent(vertexProject, vertexLocation, model, request);
        }
        return client.generateContent(model, request);
    }

    /**
     * Streaming generateContent call that ACCUMULATES chunks into a single
     * {@link GeminiChatResponse} equivalent to the unary response.
     *
     * Why: the server-side (Vertex/GFE) deadline on UNARY {@code generateContent} is ~300s —
     * long responses from think-models drop the connection at 300s ("Connection reset"). The
     * {@code streamGenerateContent?alt=sse} endpoint keeps the connection alive with chunks and
     * has no such limit (verified: unary breaks at 300.1s, stream lives 366s). For the caller
     * the result is identical to unary — the text is simply assembled from deltas.
     *
     * Images / system instructions / tools arrive in the same {@link GeminiChatRequest} as in
     * the unary path (built by {@code processPrompt}), so streaming sees exactly the same input.
     */
    private GeminiChatResponse streamGenerateContentAccumulating(String model, GeminiChatRequest request) {
        if (request.getGenerationConfig() == null) {
            request.setGenerationConfig(new GeminiGenerationConfig());
        }
        String streamUrl = buildEndpointUrl(model, "streamGenerateContent") + "?alt=sse";
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(streamUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(request)))
                    .timeout(streamTimeout());
            applyAuth(requestBuilder);

            HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() >= 400) {
                String err = response.body().collect(Collectors.joining("\n"));
                throw new RuntimeException("Gemini streaming API error: HTTP "
                        + response.statusCode() + " — " + err);
            }

            StringBuilder text = new StringBuilder();
            List<Part> extraParts = new ArrayList<>(); // functionCall / inlineData etc. — pass-through
            String finishReason = null;
            String role = "model";
            GeminiChatResponse.UsageMetadata usage = null;
            String modelVersion = null;
            ContentLoopDetector loopDetector = new ContentLoopDetector();

            // try-with-resources: closing the body stream cancels the HTTP subscription and
            // releases the connection when we stop reading early (loop detector / error).
            try (java.util.stream.Stream<String> body = response.body()) {
                Iterator<String> lines = body.iterator();
                while (lines.hasNext()) {
                    String line = lines.next();
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    String data = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
                    if (data.isEmpty() || "[DONE]".equals(data) || data.charAt(0) != '{') {
                        continue;
                    }
                    GeminiChatResponse chunk;
                    try {
                        chunk = parseStreamChunk(data);
                    } catch (Exception e) {
                        log.debug("Skipping unparsable stream chunk: {}", e.getMessage());
                        continue;
                    }
                    if (chunk == null) {
                        continue;
                    }
                    if (chunk.getUsageMetadata() != null) {
                        usage = chunk.getUsageMetadata();
                    }
                    if (chunk.getModelVersion() != null) {
                        modelVersion = chunk.getModelVersion();
                    }
                    if (CollectionUtils.isEmpty(chunk.getCandidates())) {
                        continue;
                    }
                    GeminiChatResponse.Candidate candidate = chunk.getCandidates().get(0);
                    if (candidate.getContent() != null) {
                        if (candidate.getContent().getRole() != null) {
                            role = candidate.getContent().getRole();
                        }
                        if (candidate.getContent().getParts() != null) {
                            for (Part part : candidate.getContent().getParts()) {
                                if (part.getText() != null) {
                                    text.append(part.getText());
                                    // Gemini think-models degenerate into a repetition loop up to
                                    // maxTokens (known 2.5/3.x bug on structured output). On the
                                    // stream we catch the repeat early and abort → a cheap retryable
                                    // failure instead of 7 min / 65k tokens of garbage.
                                    if (loopDetector.append(part.getText())) {
                                        log.warn("Gemini stream repetition loop (model={}, {} chars) — aborting",
                                                model, text.length());
                                        throw new ContentLoopException("Gemini output degenerated into a "
                                                + "repetition loop after " + text.length() + " chars (model="
                                                + model + ")");
                                    }
                                } else {
                                    extraParts.add(part);
                                }
                            }
                        }
                    }
                    if (candidate.getFinishReason() != null) {
                        finishReason = candidate.getFinishReason();
                    }
                }
            }

            List<Part> parts = new ArrayList<>();
            if (text.length() > 0) {
                parts.add(Part.builder().text(text.toString()).build());
            }
            parts.addAll(extraParts);

            GeminiContent content = GeminiContent.builder().role(role).parts(parts).build();
            GeminiChatResponse.Candidate merged = GeminiChatResponse.Candidate.builder()
                    .content(content)
                    .finishReason(finishReason)
                    .index(0)
                    .build();
            return GeminiChatResponse.builder()
                    .candidates(List.of(merged))
                    .usageMetadata(usage)
                    .modelVersion(modelVersion)
                    .build();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Gemini streaming API", e);
        }
    }

    /**
     * Streaming-call timeout. This is the java.net.http limit on receiving the RESPONSE
     * (headers), not a server-side deadline: generous, so a think-model has time to start
     * streaming. Takes readTimeout from config, otherwise defaults to 10 minutes.
     */
    private Duration streamTimeout() {
        Integer rt = config != null ? config.getReadTimeout() : null;
        return Duration.ofSeconds(rt != null && rt > 0 ? rt : 600L);
    }

    /**
     * Signals that the model degenerated into repetition (see {@link ContentLoopDetector}).
     * Unchecked → propagated up as a retryable error (a fresh attempt often does not loop).
     */
    public static class ContentLoopException extends RuntimeException {
        public ContentLoopException(String message) {
            super(message);
        }
    }

    /**
     * Repetition-loop detector over streamed text — inspired by gemini-cli LoopDetectionService.
     * Sliding window of {@value #CHUNK} chars; if the same window is seen {@value #THRESHOLD}+
     * times it is a degeneration loop. A conservative threshold plus a small window means
     * virtually no false positives on legitimate structured output. History is capped at
     * {@value #MAX_HISTORY} chars (with hysteresis) so the cost is amortized O(1) per char.
     *
     * Note: code/lists are NOT stripped (unlike gemini-cli) — for our short, heterogeneous
     * responses a threshold of 10 is enough; raise THRESHOLD if false positives appear.
     */
    // Package-private (not private) so the detector can be unit-tested directly.
    static final class ContentLoopDetector {
        static final int CHUNK = 50;
        static final int THRESHOLD = 10;
        private static final int MAX_HISTORY = 5000;
        private static final int TRIM_TRIGGER = 6000;

        private final StringBuilder history = new StringBuilder();
        private final Map<String, Integer> counts = new HashMap<>();
        private int scanned = 0; // index of the next unprocessed window start

        /** @return true as soon as a repetition loop is detected. */
        boolean append(String delta) {
            if (delta == null || delta.isEmpty()) {
                return false;
            }
            history.append(delta);
            if (history.length() > TRIM_TRIGGER) {
                history.delete(0, history.length() - MAX_HISTORY);
                counts.clear();
                scanned = 0;
            }
            int limit = history.length() - CHUNK;
            for (; scanned <= limit; scanned++) {
                String window = history.substring(scanned, scanned + CHUNK);
                if (counts.merge(window, 1, Integer::sum) >= THRESHOLD) {
                    return true;
                }
            }
            return false;
        }
    }

    @Nullable
    private ModelTextResponse processPrompt(ModelTextRequest prompt) {
        String model = Optional.ofNullable(prompt.getModel()).orElse(getModel());
        
        // Build contents from messages
        List<GeminiContent> contents = new ArrayList<>();
        GeminiContent systemInstruction = null;
        // Gemini pairs tool results by function NAME, our protocol pairs by call id —
        // pre-pass over ALL messages so a result is resolvable regardless of ordering.
        Map<String, String> toolCallNames = new HashMap<>();
        for (ModelContentMessage message : prompt.getMessages()) {
            if (CollectionUtils.isNotEmpty(message.getToolCalls())) {
                for (ToolCall toolCall : message.getToolCalls()) {
                    if (toolCall.getId() != null && toolCall.getFunction() != null
                            && toolCall.getFunction().getName() != null) {
                        toolCallNames.put(toolCall.getId(), toolCall.getFunction().getName());
                    }
                }
            }
        }
        
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
            
            // Agentic loop: tool result -> user message with a functionResponse part
            if (message.getRole() == Role.tool) {
                String functionName = toolCallNames.getOrDefault(message.getToolCallId(), "tool");
                String resultText = message.getContent() == null ? "" : message.getContent().stream()
                        .filter(e -> e.getType() == ModelTextRequest.MessageType.text)
                        .map(ModelContentElement::getText)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(" "));
                contents.add(GeminiContent.builder()
                        .role("user")
                        .parts(List.of(Part.builder()
                                .functionResponse(Part.FunctionResponse.builder()
                                        .name(functionName)
                                        .response(Map.of("result", resultText))
                                        .build())
                                .build()))
                        .build());
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
                        // Gemini rejects empty text parts in tool-call echoes
                        if (element.getText() == null || (element.getText().isEmpty()
                                && CollectionUtils.isNotEmpty(message.getToolCalls()))) {
                            break;
                        }
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

            // Agentic loop: assistant echo carrying tool calls -> functionCall parts
            if (CollectionUtils.isNotEmpty(message.getToolCalls())) {
                for (ToolCall toolCall : message.getToolCalls()) {
                    String name = toolCall.getFunction() != null ? toolCall.getFunction().getName() : null;
                    Map<String, Object> args = new LinkedHashMap<>();
                    if (toolCall.getFunction() != null && toolCall.getFunction().getArguments() != null) {
                        toolCall.getFunction().getArguments().forEach((key, value) ->
                                args.put(key, ModelUtils.OBJECT_MAPPER.convertValue(value, Object.class)));
                    }
                    parts.add(Part.builder()
                            .functionCall(Part.FunctionCall.builder()
                                    .name(name)
                                    .args(args)
                                    .build())
                            .build());
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
        
        // Handle structured output
        if (jsonObjectSupport && prompt.getResponseFormat() != null) {
            if (prompt.getResponseFormat().getType() == ResponseFormat.ResponseType.JSON_OBJECT) {
                configBuilder.responseMimeType("application/json");
            } else if (prompt.getResponseFormat().getType() == ResponseFormat.ResponseType.JSON_SCHEMA) {
                configBuilder.responseMimeType("application/json");
                configBuilder.responseSchema(GeminiUtils.convertToGeminiSchema(prompt.getResponseFormat().getJsonSchema()));
            }
        }
        
        // Handle logprobs
        if (Boolean.TRUE.equals(prompt.getLogprobs()) || Boolean.TRUE.equals(getLogprobs())) {
            configBuilder.responseLogprobs(true);
            configBuilder.logprobs(Optional.ofNullable(prompt.getTopLogprobs()).orElse(getTopLogprobs()));
        }
        
        // Thinking control: map an EXPLICIT reasoningEffort to thinkingConfig. Works on 2.5
        // AND 3.x (verified: 3.5-flash accepts thinkingBudget=0 → HTTP 200, ~1s). When the
        // caller sets no reasoningEffort we send NO thinkingConfig and let the model use its
        // own native default — the framework must not silently override model behaviour.
        if (model != null && model.toLowerCase().contains("gemini") && prompt.getReasoningEffort() != null) {
            String modelLc = model.toLowerCase();
            ThinkingConfig.ThinkingConfigBuilder thinkingBuilder = ThinkingConfig.builder();
            switch (prompt.getReasoningEffort()) {
                case none:
                    thinkingBuilder.thinkingBudget(0);
                    break;
                case low:
                    thinkingBuilder.thinkingBudget(4096);
                    break;
                case medium:
                    thinkingBuilder.thinkingBudget(8192);
                    break;
                case dynamic:
                    thinkingBuilder.thinkingBudget(-1);
                    break;
                case high:
                    thinkingBuilder.thinkingBudget(modelLc.contains("pro") ? 32768 : 16384);
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
            // Vertex unary generateContent has a server-side ~300s deadline (drops long
            // responses from think-models). Text/structured calls go through
            // streamGenerateContent with chunk accumulation — no limit, same result.
            // Image generation (textToImage) stays on unary callGenerateContent.
            GeminiChatResponse response = vertexMode
                    ? streamGenerateContentAccumulating(model, request)
                    : callGenerateContent(model, request);
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
        String requestBody = JsonUtils.toJson(request);
        String streamUrl = buildEndpointUrl(model, "streamGenerateContent") + "?alt=sse";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(streamUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofMinutes(5));
        applyAuth(requestBuilder);

        HttpRequest httpRequest = requestBuilder.build();
        
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
                        GeminiChatResponse chunk = parseStreamChunk(data);
                        
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
        GeminiGenerationConfig config = GeminiGenerationConfig.builder()
                .temperature(temperature)
                .candidateCount(1)
                .build();
        
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