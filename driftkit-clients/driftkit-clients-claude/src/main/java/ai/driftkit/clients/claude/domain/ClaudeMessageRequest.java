package ai.driftkit.clients.claude.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeMessageRequest {
    
    @JsonProperty("model")
    private String model;
    
    @JsonProperty("messages")
    private List<ClaudeMessage> messages;
    
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    @JsonProperty("stop_sequences")
    private List<String> stopSequences;
    
    @JsonProperty("stream")
    private Boolean stream;
    
    /**
     * System prompt. Can be a plain String or a List of ClaudeContent blocks
     * (required for cache_control support).
     */
    @JsonProperty("system")
    @JsonDeserialize(using = SystemDeserializer.class)
    private Object system;
    
    @JsonProperty("temperature")
    private Double temperature;
    
    @JsonProperty("tool_choice")
    private ToolChoice toolChoice;
    
    @JsonProperty("tools")
    private List<ClaudeTool> tools;
    
    @JsonProperty("top_k")
    private Integer topK;
    
    @JsonProperty("top_p")
    private Double topP;

    /**
     * Structured output format configuration.
     * Requires beta header: anthropic-beta: structured-outputs-2025-11-13
     *
     * @see ClaudeOutputFormat
     */
    @JsonProperty("output_format")
    private ClaudeOutputFormat outputFormat;

    /**
     * Set system prompt as a list of content blocks with cache_control on the last block.
     */
    public void setSystemWithCache(String systemPrompt) {
        Objects.requireNonNull(systemPrompt, "systemPrompt must not be null");
        this.system = List.of(ClaudeContent.textWithCache(systemPrompt));
    }

    /**
     * Get system prompt as plain string regardless of internal representation.
     */
    public String getSystemAsString() {
        if (system instanceof String s) {
            return s;
        }
        if (system instanceof List<?> list && !list.isEmpty()) {
            Object first = list.getFirst();
            if (first instanceof ClaudeContent cc) {
                return cc.getText();
            }
        }
        return null;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolChoice {
        @JsonProperty("type")
        private String type; // "auto", "any", "tool"

        @JsonProperty("name")
        private String name; // Only when type is "tool"

        @JsonProperty("disable_parallel_tool_use")
        private Boolean disableParallelToolUse;
    }

    /**
     * Deserializes the {@code system} field as either a plain String or a List of ClaudeContent blocks.
     */
    public static class SystemDeserializer extends JsonDeserializer<Object> {
        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node.isTextual()) {
                return node.asText();
            }
            if (node.isArray()) {
                List<ClaudeContent> contents = new ArrayList<>();
                for (JsonNode element : node) {
                    contents.add(p.getCodec().treeToValue(element, ClaudeContent.class));
                }
                return contents;
            }
            return null;
        }
    }
}