package ai.driftkit.clients.claude.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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
    
    @JsonProperty("system")
    private String system;
    
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
}