package ai.driftkit.clients.claude.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeContent {
    
    @JsonProperty("type")
    private String type; // "text", "image", "tool_use", "tool_result"
    
    @JsonProperty("text")
    private String text;
    
    @JsonProperty("source")
    private ImageSource source;
    
    @JsonProperty("id")
    private String id; // For tool_use and tool_result
    
    @JsonProperty("name")
    private String name; // For tool_use
    
    @JsonProperty("input")
    private Map<String, Object> input; // For tool_use
    
    @JsonProperty("tool_use_id")
    private String toolUseId; // For tool_result
    
    @JsonProperty("content")
    private String content; // For tool_result
    
    @JsonProperty("is_error")
    private Boolean isError; // For tool_result
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageSource {
        @JsonProperty("type")
        private String type; // "base64"
        
        @JsonProperty("media_type")
        private String mediaType; // "image/jpeg", "image/png", "image/gif", "image/webp"
        
        @JsonProperty("data")
        private String data; // Base64 encoded image data
    }
    
    // Helper methods for creating content blocks
    public static ClaudeContent text(String text) {
        return ClaudeContent.builder()
                .type("text")
                .text(text)
                .build();
    }
    
    public static ClaudeContent image(String base64Data, String mediaType) {
        return ClaudeContent.builder()
                .type("image")
                .source(ImageSource.builder()
                        .type("base64")
                        .mediaType(mediaType)
                        .data(base64Data)
                        .build())
                .build();
    }
    
    public static ClaudeContent toolUse(String id, String name, Map<String, Object> input) {
        return ClaudeContent.builder()
                .type("tool_use")
                .id(id)
                .name(name)
                .input(input)
                .build();
    }
    
    public static ClaudeContent toolResult(String toolUseId, String content) {
        return ClaudeContent.builder()
                .type("tool_result")
                .toolUseId(toolUseId)
                .content(content)
                .build();
    }
    
    public static ClaudeContent toolError(String toolUseId, String error) {
        return ClaudeContent.builder()
                .type("tool_result")
                .toolUseId(toolUseId)
                .content(error)
                .isError(true)
                .build();
    }
}