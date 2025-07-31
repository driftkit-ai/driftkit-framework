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
public class ClaudeMessage {
    
    @JsonProperty("role")
    private String role; // "user" or "assistant"
    
    @JsonProperty("content")
    private List<ClaudeContent> content;
    
    // Helper method to create a simple text message
    public static ClaudeMessage textMessage(String role, String text) {
        return ClaudeMessage.builder()
                .role(role)
                .content(List.of(ClaudeContent.text(text)))
                .build();
    }
    
    // Helper method to create a message with content blocks
    public static ClaudeMessage contentMessage(String role, List<ClaudeContent> contents) {
        return ClaudeMessage.builder()
                .role(role)
                .content(contents)
                .build();
    }
}