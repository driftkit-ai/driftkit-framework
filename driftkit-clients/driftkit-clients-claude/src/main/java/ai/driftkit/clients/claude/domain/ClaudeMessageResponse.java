package ai.driftkit.clients.claude.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeMessageResponse {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("type")
    private String type; // "message"
    
    @JsonProperty("role")
    private String role; // "assistant"
    
    @JsonProperty("content")
    private List<ClaudeContent> content;
    
    @JsonProperty("model")
    private String model;
    
    @JsonProperty("stop_reason")
    private String stopReason; // "end_turn", "max_tokens", "stop_sequence", "tool_use"
    
    @JsonProperty("stop_sequence")
    private String stopSequence;
    
    @JsonProperty("usage")
    private ClaudeUsage usage;
}