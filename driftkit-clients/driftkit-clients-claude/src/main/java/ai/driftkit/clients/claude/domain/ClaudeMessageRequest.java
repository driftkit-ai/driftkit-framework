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

    @JsonProperty("output_format")
    private OutputFormat outputFormat;

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OutputFormat {
        @JsonProperty("type")
        private String type; // "json_schema"

        @JsonProperty("schema")
        private SchemaDefinition schema;

        public OutputFormat(String type) {
            this.type = type;
        }

        public OutputFormat(String type, SchemaDefinition schema) {
            this.type = type;
            this.schema = schema;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SchemaDefinition {
        @JsonProperty("type")
        private String type;

        @JsonProperty("properties")
        private Map<String, Property> properties;

        @JsonProperty("required")
        private List<String> required;

        @JsonProperty("additionalProperties")
        private Boolean additionalProperties;

        public SchemaDefinition(String type, Map<String, Property> properties, List<String> required) {
            this.type = type;
            this.properties = properties;
            this.required = required;
            this.additionalProperties = false; // Always false for strict mode
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Property {
        @JsonProperty("type")
        private String type;

        @JsonProperty("description")
        private String description;

        @JsonProperty("enum")
        private List<String> enumValues;

        @JsonProperty("properties")
        private Map<String, Property> properties;

        @JsonProperty("required")
        private List<String> required;

        @JsonProperty("items")
        private Property items;

        @JsonProperty("additionalProperties")
        private Object additionalProperties;

        public Property(String type, String description, List<String> enumValues) {
            this.type = type;
            this.description = description;
            this.enumValues = enumValues;
        }
    }
}