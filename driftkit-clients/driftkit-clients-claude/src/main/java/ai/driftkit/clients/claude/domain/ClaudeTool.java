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
 * Represents a tool definition for Claude API.
 * <p>
 * Tools allow Claude to call external functions and interact with the world.
 * When strict mode is enabled, Claude guarantees that tool inputs will match
 * the input_schema exactly.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeTool {

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("input_schema")
    private InputSchema inputSchema;

    /**
     * Enable strict mode for tool inputs.
     * When true, Claude guarantees that tool inputs will match the input_schema exactly.
     * Requires beta header: anthropic-beta: structured-outputs-2025-11-13
     */
    @JsonProperty("strict")
    private Boolean strict;

    /**
     * Input schema definition for tool parameters.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InputSchema {
        @JsonProperty("type")
        private String type; // Usually "object"

        @JsonProperty("properties")
        private Map<String, ClaudeSchemaProperty> properties;

        @JsonProperty("required")
        private List<String> required;

        /**
         * Must be set to false for strict mode.
         */
        @JsonProperty("additionalProperties")
        private Boolean additionalProperties;
    }
}
