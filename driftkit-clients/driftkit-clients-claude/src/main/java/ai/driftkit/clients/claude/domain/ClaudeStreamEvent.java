package ai.driftkit.clients.claude.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeStreamEvent {
    
    @JsonProperty("type")
    private String type; // "message_start", "content_block_start", "content_block_delta", "content_block_stop", "message_delta", "message_stop", "error"
    
    @JsonProperty("message")
    private ClaudeMessageResponse message; // For message_start
    
    @JsonProperty("index")
    private Integer index; // For content_block events
    
    @JsonProperty("content_block")
    private ClaudeContent contentBlock; // For content_block_start
    
    @JsonProperty("delta")
    private Delta delta; // For content_block_delta and message_delta
    
    @JsonProperty("usage")
    private ClaudeUsage usage; // For message_delta
    
    @JsonProperty("error")
    private Error error; // For error events
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Delta {
        @JsonProperty("type")
        private String type; // "text_delta", "input_json_delta"
        
        @JsonProperty("text")
        private String text;
        
        @JsonProperty("partial_json")
        private String partialJson;
        
        @JsonProperty("stop_reason")
        private String stopReason;
        
        @JsonProperty("stop_sequence")
        private String stopSequence;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Error {
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("message")
        private String message;
    }
}