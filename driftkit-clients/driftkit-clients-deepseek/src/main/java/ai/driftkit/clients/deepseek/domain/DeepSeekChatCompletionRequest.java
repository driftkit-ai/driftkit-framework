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

        /** Echo of assistant tool calls in a follow-up request (OpenAI-compatible). */
        @JsonProperty("tool_calls")
        private List<RequestToolCall> toolCalls;

        /** Pairs a role=tool message with its originating call. */
        @JsonProperty("tool_call_id")
        private String toolCallId;

        public Message(String role, Object content) {
            this.role = role;
            this.content = content;
        }

        public static Message of(String role, String content) {
            return new Message(role, content);
        }
    }

    /**
     * Tool call echoed back to the API. Unlike the response DTO, {@code function.arguments}
     * must be a JSON-encoded string per the OpenAI-compatible wire format.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RequestToolCall {
        private String id;
        private String type;
        private RequestFunctionCall function;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class RequestFunctionCall {
            private String name;
            private String arguments; // JSON string
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseFormat {
        @JsonProperty("type")
        private String type;

        @JsonProperty("json_schema")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private JsonSchema jsonSchema;

        public ResponseFormat() {}

        public ResponseFormat(String type) {
            this.type = type;
        }

        public ResponseFormat(String type, JsonSchema jsonSchema) {
            this.type = type;
            this.jsonSchema = jsonSchema;
        }

        @Data
        public static class JsonSchema {
            @JsonProperty("name")
            private String name;

            @JsonProperty("strict")
            private Boolean strict;

            @JsonProperty("schema")
            private Map<String, Object> schema;

            public JsonSchema() {}

            public JsonSchema(String name, Map<String, Object> schema) {
                this.name = name;
                this.strict = true;
                this.schema = schema;
            }
        }
    }
}
