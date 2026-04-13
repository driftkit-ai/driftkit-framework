package ai.driftkit.clients.deepseek.domain;

import ai.driftkit.common.domain.client.ModelClient.Tool;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek-specific chat completion request.
 * OpenAI-compatible with additional {@code thinking} parameter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeepSeekChatCompletionRequest {

    @JsonProperty("model")
    private String model;

    @JsonProperty("messages")
    private List<Message> messages;

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("stream")
    private Boolean stream;

    @JsonProperty("stop")
    private List<String> stop;

    @JsonProperty("tools")
    private List<Tool> tools;

    @JsonProperty("tool_choice")
    private String toolChoice;

    @JsonProperty("response_format")
    private ResponseFormat responseFormat;

    /**
     * DeepSeek-specific: enable/disable thinking mode.
     */
    @JsonProperty("thinking")
    private DeepSeekThinkingConfig thinking;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        private String role;
        private Object content; // String or List<ContentElement>

        public static Message of(String role, String content) {
            return new Message(role, content);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContentElement {
        private String type;
        private String text;
        @JsonProperty("image_url")
        private Map<String, String> imageUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseFormat {
        private String type;
    }
}
