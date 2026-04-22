package ai.driftkit.clients.deepseek.client;

import ai.driftkit.clients.deepseek.domain.DeepSeekChatCompletionRequest;
import ai.driftkit.clients.deepseek.domain.DeepSeekChatCompletionResponse;
import ai.driftkit.clients.deepseek.domain.DeepSeekChatCompletionResponse.DeepSeekUsage;
import ai.driftkit.clients.deepseek.domain.DeepSeekThinkingConfig;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ModelContentMessage.ModelContentElement;
import ai.driftkit.common.domain.client.ModelTextRequest.MessageType;
import ai.driftkit.common.domain.client.ModelTextRequest.ReasoningEffort;
import ai.driftkit.common.domain.client.ModelTextRequest.ToolMode;
import ai.driftkit.common.domain.client.ModelTextResponse.ResponseMessage;
import ai.driftkit.common.domain.client.ModelTextResponse.Usage;
import ai.driftkit.common.domain.client.ResponseFormat.ResponseType;
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
import java.util.stream.Collectors;

/**
 * DeepSeek model client. Uses DeepSeek's OpenAI-compatible API with
 * additional support for:
 * <ul>
 *   <li>Thinking/reasoning mode via {@code thinking} parameter</li>
 *   <li>{@code reasoning_content} in responses</li>
 *   <li>Prefix caching metrics ({@code prompt_cache_hit_tokens}/{@code prompt_cache_miss_tokens})</li>
 * </ul>
 */
@Slf4j
public class DeepSeekModelClient extends ModelClient implements ModelClient.ModelClientInit {

    public static final String DEEPSEEK_CHAT = "deepseek-chat";
    public static final String DEEPSEEK_REASONER = "deepseek-reasoner";
    public static final String DEEPSEEK_PREFIX = "deepseek";
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final int MAX_TOKENS = 8192;

    private DeepSeekApiClient client;
    private VaultConfig config;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public ModelClient init(VaultConfig config) {
        this.config = config;
        String baseUrl = Optional.ofNullable(config.getBaseUrl()).orElse(DEFAULT_BASE_URL);
        this.client = DeepSeekClientFactory.createClient(
                config.getApiKey(),
                baseUrl,
                config.getConnectTimeout(),
                config.getReadTimeout()
        );
        this.setTemperature(config.getTemperature());
        this.setModel(config.getModel());
        this.setStop(config.getStop());
        this.jsonObjectSupport = config.isJsonObject();
        return this;
    }

    public static ModelClient create(VaultConfig config) {
        DeepSeekModelClient modelClient = new DeepSeekModelClient();
        modelClient.init(config);
        return modelClient;
    }

    @Override
    public Set<Capability> getCapabilities() {
        return Set.of(
                Capability.TEXT_TO_TEXT,
                Capability.FUNCTION_CALLING,
                Capability.JSON_OBJECT,
                Capability.JSON_SCHEMA,
                Capability.TOOLS
        );
    }

    @Override
    public ModelTextResponse textToText(ModelTextRequest prompt) {
        super.textToText(prompt);
        return processPrompt(prompt);
    }

    @Override
    public ModelTextResponse imageToText(ModelTextRequest prompt) throws UnsupportedCapabilityException {
        throw new UnsupportedCapabilityException("DeepSeek does not support image-to-text");
    }

    @Override
    public ModelImageResponse textToImage(ModelImageRequest prompt) {
        throw new UnsupportedCapabilityException("DeepSeek does not support image generation");
    }

    @Override
    public StreamingResponse<String> streamTextToText(ModelTextRequest prompt) {
        return new StreamingResponse<>() {
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

    // ---- Core processing ----

    private ModelTextResponse processPrompt(ModelTextRequest prompt) {
        DeepSeekChatCompletionRequest request = buildRequest(prompt);

        try {
            DeepSeekChatCompletionResponse response = client.createChatCompletion(request);
            return mapToModelTextResponse(response);
        } catch (Exception e) {
            log.error("Error calling DeepSeek API", e);
            throw new RuntimeException("Failed to call DeepSeek API", e);
        }
    }

    private DeepSeekChatCompletionRequest buildRequest(ModelTextRequest prompt) {
        String model = Optional.ofNullable(prompt.getModel())
                .orElse(Optional.ofNullable(getModel()).orElse(DEEPSEEK_CHAT));
        Double temperature = Optional.ofNullable(prompt.getTemperature()).orElse(getTemperature());

        // Build messages, stripping reasoning_content from prior assistant turns
        List<DeepSeekChatCompletionRequest.Message> messages = new ArrayList<>();
        for (ModelContentMessage msg : prompt.getMessages()) {
            String role = msg.getRole().name().toLowerCase();
            String text = extractText(msg);
            messages.add(DeepSeekChatCompletionRequest.Message.of(role, text));
        }

        // Determine thinking mode
        boolean thinkingEnabled = isThinkingEnabled(prompt, model);

        DeepSeekChatCompletionRequest.DeepSeekChatCompletionRequestBuilder builder =
                DeepSeekChatCompletionRequest.builder()
                        .model(model)
                        .messages(messages)
                        .maxTokens(Optional.ofNullable(getMaxCompletionTokens())
                                .orElse(Optional.ofNullable(getMaxTokens())
                                        .orElse(Optional.ofNullable(config.getMaxTokens())
                                                .orElse(MAX_TOKENS))));

        if (thinkingEnabled) {
            builder.thinking(DeepSeekThinkingConfig.enabled());
            // temperature is ignored in thinking mode
        } else {
            builder.temperature(temperature);
        }

        // Tools
        List<Tool> tools = prompt.getToolMode() == ToolMode.none ? null : getTools();
        if (CollectionUtils.isEmpty(tools)) {
            tools = prompt.getTools();
        }
        if (CollectionUtils.isNotEmpty(tools) && prompt.getToolMode() != ToolMode.none) {
            builder.tools(tools);
            if (prompt.getToolMode() == ToolMode.auto) {
                builder.toolChoice("auto");
            }
        }

        // Response format — DeepSeek only supports json_object, NOT json_schema.
        // We map json_schema → json_object and inject schema into prompt.
        if (prompt.getResponseFormat() != null
                && prompt.getResponseFormat().getType() != null
                && prompt.getResponseFormat().getType() != ResponseType.TEXT) {
            builder.responseFormat(new DeepSeekChatCompletionRequest.ResponseFormat("json_object"));

            // DeepSeek requires "json" in prompt for json_object mode.
            // Generate schema description from ResponseFormat.JsonSchema and inject into prompt.
            String schemaPrompt = buildJsonSchemaPrompt(prompt.getResponseFormat().getJsonSchema());
            if (schemaPrompt != null && messages != null && !messages.isEmpty()) {
                var firstMsg = messages.get(0);
                if (firstMsg.getContent() instanceof String content) {
                    if (!content.toLowerCase().contains("json")) {
                        firstMsg.setContent(content + schemaPrompt);
                    }
                }
            }
        }

        if (getStop() != null) {
            builder.stop(getStop());
        }

        return builder.build();
    }

    /**
     * Build a prompt fragment describing the expected JSON schema.
     * DeepSeek doesn't support json_schema mode, so we inject the schema into the prompt
     * along with the word "json" (required by DeepSeek API for json_object mode).
     */
    private String buildJsonSchemaPrompt(ai.driftkit.common.domain.client.ResponseFormat.JsonSchema schema) {
        if (schema == null) {
            return "\n\nYou MUST respond with a valid JSON object.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\nYou MUST respond with a valid JSON object matching this schema:\n{");

        if (schema.getProperties() != null) {
            var props = schema.getProperties();
            int i = 0;
            for (var entry : props.entrySet()) {
                if (i > 0) sb.append(",");
                sb.append("\n  \"").append(entry.getKey()).append("\": ");
                var prop = entry.getValue();
                if (prop != null && prop.getType() != null) {
                    switch (prop.getType()) {
                        case "string" -> sb.append("\"<string>\"");
                        case "integer", "number" -> sb.append("<number>");
                        case "boolean" -> sb.append("<true|false>");
                        case "array" -> sb.append("[...]");
                        default -> sb.append("\"<").append(prop.getType()).append(">\"");
                    }
                    if (prop.getDescription() != null) {
                        sb.append("  // ").append(prop.getDescription());
                    }
                } else {
                    sb.append("\"<value>\"");
                }
                i++;
            }
        }
        sb.append("\n}");

        if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
            sb.append("\nRequired fields: ").append(String.join(", ", schema.getRequired()));
        }

        sb.append("\nReturn ONLY the JSON object, no other text.");
        return sb.toString();
    }

    private boolean isThinkingEnabled(ModelTextRequest prompt, String model) {
        // If model is explicitly deepseek-reasoner, thinking is always enabled
        if (DEEPSEEK_REASONER.equals(model)) {
            return true;
        }
        // For deepseek-chat: only enable thinking for explicit high/dynamic effort.
        // medium/low/minimal are treated as non-thinking since DeepSeek has no
        // budget control — thinking is all-or-nothing.
        ReasoningEffort effort = prompt.getReasoningEffort();
        return effort == ReasoningEffort.high || effort == ReasoningEffort.dynamic;
    }

    private String extractText(ModelContentMessage msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return "";
        }
        return msg.getContent().stream()
                .filter(e -> e.getType() == MessageType.text)
                .map(ModelContentElement::getText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    // ---- Response mapping ----

    private ModelTextResponse mapToModelTextResponse(DeepSeekChatCompletionResponse response) {
        if (response == null) return null;

        List<ResponseMessage> choices = null;
        if (response.getChoices() != null) {
            choices = response.getChoices().stream()
                    .map(this::mapChoice)
                    .filter(Objects::nonNull)
                    .toList();
        }

        Usage usage = mapUsage(response.getUsage());

        return ModelTextResponse.builder()
                .id(response.getId())
                .method("deepseek.chat.completions")
                .createdTime(response.getCreated())
                .model(response.getModel())
                .choices(choices)
                .usage(usage)
                .build();
    }

    private ResponseMessage mapChoice(DeepSeekChatCompletionResponse.Choice choice) {
        if (choice == null) return null;

        DeepSeekChatCompletionResponse.Message msg = choice.getMessage();
        String content = msg != null ? msg.getContent() : null;

        if (content != null && JsonUtils.isJSON(content) && !JsonUtils.isValidJSON(content)) {
            content = JsonUtils.fixIncompleteJSON(content);
        }

        // Map tool calls
        List<ToolCall> toolCalls = null;
        if (msg != null && msg.getToolCalls() != null) {
            toolCalls = msg.getToolCalls().stream()
                    .map(this::mapToolCall)
                    .filter(Objects::nonNull)
                    .toList();
        }

        ModelMessage modelMessage = ModelMessage.builder()
                .role(Role.assistant)
                .content(content)
                .reasoningContent(msg != null ? msg.getReasoningContent() : null)
                .toolCalls(toolCalls != null && !toolCalls.isEmpty() ? toolCalls : null)
                .build();

        return ResponseMessage.builder()
                .index(choice.getIndex())
                .message(modelMessage)
                .finishReason(choice.getFinishReason())
                .build();
    }

    private ToolCall mapToolCall(DeepSeekChatCompletionResponse.ToolCall tc) {
        if (tc == null) return null;

        ToolCall.FunctionCall function = null;
        if (tc.getFunction() != null) {
            Map<String, JsonNode> arguments = parseJsonStringToNodeMap(tc.getFunction().getArguments());
            function = ToolCall.FunctionCall.builder()
                    .name(tc.getFunction().getName())
                    .arguments(arguments)
                    .build();
        }

        return ToolCall.builder()
                .id(tc.getId())
                .type(tc.getType())
                .function(function)
                .build();
    }

    private Usage mapUsage(DeepSeekUsage du) {
        if (du == null) return null;

        CacheUsage cacheUsage = null;
        if (du.getPromptCacheHitTokens() != null || du.getPromptCacheMissTokens() != null) {
            cacheUsage = CacheUsage.builder()
                    .cacheHitTokens(du.getPromptCacheHitTokens())
                    .cacheMissTokens(du.getPromptCacheMissTokens())
                    .build();
        }

        Integer reasoningTokens = null;
        if (du.getCompletionTokensDetails() != null) {
            reasoningTokens = du.getCompletionTokensDetails().getReasoningTokens();
        }

        return Usage.builder()
                .promptTokens(du.getPromptTokens())
                .completionTokens(du.getCompletionTokens())
                .totalTokens(du.getTotalTokens())
                .cacheUsage(cacheUsage)
                .reasoningTokens(reasoningTokens)
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
            rootNode.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue()));
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse tool call arguments: " + e.getMessage(), e);
        }
    }

    // ---- Streaming ----

    private void processStreamingPrompt(ModelTextRequest prompt, StreamingCallback<String> callback,
                                        AtomicBoolean cancelled) throws Exception {
        DeepSeekChatCompletionRequest request = buildRequest(prompt);
        request.setStream(true);

        String apiKey = config.getApiKey();
        String baseUrl = Optional.ofNullable(config.getBaseUrl()).orElse(DEFAULT_BASE_URL);
        String requestBody = JsonUtils.toJson(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofMinutes(5))
                .build();

        SSESubscriber sseSubscriber = new SSESubscriber(callback, cancelled);
        HttpResponse<Void> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.fromLineSubscriber(sseSubscriber));

        if (response.statusCode() >= 400) {
            // Re-send to get the error body
            HttpRequest errorRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> errorResponse = httpClient.send(errorRequest, HttpResponse.BodyHandlers.ofString());
            log.error("DeepSeek streaming error response: {}", errorResponse.body());
            throw new RuntimeException("DeepSeek API error: HTTP " + response.statusCode() + " - " + errorResponse.body());
        }
    }

    /**
     * SSE Subscriber for DeepSeek streaming (OpenAI-compatible format).
     * Handles both reasoning_content and content deltas.
     */
    private static class SSESubscriber implements Flow.Subscriber<String> {
        private final StreamingCallback<String> callback;
        private final AtomicBoolean cancelled;
        private Flow.Subscription subscription;
        private boolean completed = false;

        SSESubscriber(StreamingCallback<String> callback, AtomicBoolean cancelled) {
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
                if (cancelled.get() && !completed) subscription.cancel();
                return;
            }
            try {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        completed = true;
                        callback.onComplete();
                        return;
                    }

                    JsonNode chunk = JsonUtils.fromJson(data, JsonNode.class);
                    if (chunk != null && chunk.has("choices") && !chunk.get("choices").isEmpty()) {
                        JsonNode delta = chunk.get("choices").get(0).path("delta");

                        // Emit reasoning_content first if present
                        String reasoning = delta.path("reasoning_content").asText(null);
                        if (reasoning != null) {
                            // Optionally could emit via separate callback, for now skip reasoning in stream
                        }

                        // Emit content
                        String content = delta.path("content").asText(null);
                        if (content != null) {
                            callback.onNext(content);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error processing DeepSeek SSE line: {}", line, e);
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
}
