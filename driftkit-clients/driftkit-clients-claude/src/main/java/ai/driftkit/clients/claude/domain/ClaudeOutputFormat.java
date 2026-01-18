package ai.driftkit.clients.claude.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents Claude's output_format parameter for structured outputs.
 * <p>
 * Structured outputs constrain Claude's responses to follow a specific JSON schema,
 * ensuring valid, parseable output for downstream processing.
 * </p>
 * <p>
 * Requires beta header: {@code anthropic-beta: structured-outputs-2025-11-13}
 * </p>
 *
 * @see <a href="https://platform.claude.com/docs/en/build-with-claude/structured-outputs">
 *      Claude Structured Outputs Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeOutputFormat {

    /**
     * The type of output format.
     * Currently only "json_schema" is supported for structured outputs.
     */
    @JsonProperty("type")
    private String type;

    /**
     * The JSON Schema that the response must conform to.
     * Uses standard JSON Schema format with some limitations.
     */
    @JsonProperty("schema")
    private JsonSchemaDefinition schema;

    /**
     * Creates a JSON schema output format from a schema definition.
     *
     * @param schema The JSON schema definition
     * @return ClaudeOutputFormat configured for JSON schema mode
     */
    public static ClaudeOutputFormat jsonSchema(JsonSchemaDefinition schema) {
        return ClaudeOutputFormat.builder()
                .type("json_schema")
                .schema(schema)
                .build();
    }

    /**
     * JSON Schema definition compatible with Claude's structured outputs.
     * <p>
     * Supported features:
     * <ul>
     *   <li>Basic types: object, array, string, integer, number, boolean, null</li>
     *   <li>enum (strings, numbers, bools, nulls only)</li>
     *   <li>const</li>
     *   <li>anyOf, allOf (with limitations)</li>
     *   <li>$ref, $defs, definitions (local only)</li>
     *   <li>default property</li>
     *   <li>required and additionalProperties (must be false)</li>
     *   <li>String formats: date-time, time, date, duration, email, hostname, uri, ipv4, ipv6, uuid</li>
     *   <li>minItems (only 0 or 1)</li>
     * </ul>
     * </p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonSchemaDefinition {

        @JsonProperty("type")
        private String type;

        @JsonProperty("description")
        private String description;

        @JsonProperty("properties")
        private Map<String, ClaudeSchemaProperty> properties;

        @JsonProperty("required")
        private List<String> required;

        /**
         * Must be set to false for Claude structured outputs.
         */
        @JsonProperty("additionalProperties")
        private Boolean additionalProperties;

        @JsonProperty("items")
        private ClaudeSchemaProperty items;

        @JsonProperty("enum")
        private List<Object> enumValues;

        @JsonProperty("const")
        private Object constValue;

        @JsonProperty("anyOf")
        private List<JsonSchemaDefinition> anyOf;

        @JsonProperty("allOf")
        private List<JsonSchemaDefinition> allOf;

        @JsonProperty("$ref")
        private String ref;

        @JsonProperty("$defs")
        private Map<String, JsonSchemaDefinition> defs;

        @JsonProperty("definitions")
        private Map<String, JsonSchemaDefinition> definitions;

        @JsonProperty("default")
        private Object defaultValue;

        @JsonProperty("format")
        private String format;

        @JsonProperty("minItems")
        private Integer minItems;

        @JsonProperty("pattern")
        private String pattern;
    }
}
