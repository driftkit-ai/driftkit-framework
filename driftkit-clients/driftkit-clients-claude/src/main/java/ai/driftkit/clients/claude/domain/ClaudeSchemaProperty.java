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
 * Unified JSON Schema property definition for Claude API.
 * <p>
 * Used by both:
 * <ul>
 *   <li>Structured outputs ({@link ClaudeOutputFormat})</li>
 *   <li>Tool input schemas ({@link ClaudeTool})</li>
 * </ul>
 * </p>
 * <p>
 * Supports all JSON Schema features compatible with Claude's structured outputs:
 * basic types, enums, nested objects, arrays, and validation constraints.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeSchemaProperty {

    @JsonProperty("type")
    private String type;

    @JsonProperty("description")
    private String description;

    @JsonProperty("enum")
    private List<Object> enumValues;

    @JsonProperty("const")
    private Object constValue;

    @JsonProperty("properties")
    private Map<String, ClaudeSchemaProperty> properties;

    @JsonProperty("required")
    private List<String> required;

    /**
     * Must be set to false for Claude structured outputs and strict tool mode.
     */
    @JsonProperty("additionalProperties")
    private Boolean additionalProperties;

    @JsonProperty("items")
    private ClaudeSchemaProperty items;

    @JsonProperty("anyOf")
    private List<ClaudeSchemaProperty> anyOf;

    @JsonProperty("allOf")
    private List<ClaudeSchemaProperty> allOf;

    @JsonProperty("default")
    private Object defaultValue;

    @JsonProperty("format")
    private String format;

    @JsonProperty("minItems")
    private Integer minItems;

    @JsonProperty("pattern")
    private String pattern;

    /**
     * Creates a string type property.
     */
    public static ClaudeSchemaProperty string(String description) {
        return ClaudeSchemaProperty.builder()
                .type("string")
                .description(description)
                .build();
    }

    /**
     * Creates an integer type property.
     */
    public static ClaudeSchemaProperty integer(String description) {
        return ClaudeSchemaProperty.builder()
                .type("integer")
                .description(description)
                .build();
    }

    /**
     * Creates a number type property.
     */
    public static ClaudeSchemaProperty number(String description) {
        return ClaudeSchemaProperty.builder()
                .type("number")
                .description(description)
                .build();
    }

    /**
     * Creates a boolean type property.
     */
    public static ClaudeSchemaProperty bool(String description) {
        return ClaudeSchemaProperty.builder()
                .type("boolean")
                .description(description)
                .build();
    }

    /**
     * Creates an enum type property.
     */
    public static ClaudeSchemaProperty enumOf(String description, List<Object> values) {
        return ClaudeSchemaProperty.builder()
                .type("string")
                .description(description)
                .enumValues(values)
                .build();
    }

    /**
     * Creates an array type property.
     */
    public static ClaudeSchemaProperty array(String description, ClaudeSchemaProperty items) {
        return ClaudeSchemaProperty.builder()
                .type("array")
                .description(description)
                .items(items)
                .build();
    }

    /**
     * Creates an object type property with nested properties.
     */
    public static ClaudeSchemaProperty object(
            String description,
            Map<String, ClaudeSchemaProperty> properties,
            List<String> required) {
        return ClaudeSchemaProperty.builder()
                .type("object")
                .description(description)
                .properties(properties)
                .required(required)
                .additionalProperties(false)
                .build();
    }
}
