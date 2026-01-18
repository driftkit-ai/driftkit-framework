package ai.driftkit.clients.claude.utils;

import ai.driftkit.clients.claude.domain.ClaudeOutputFormat;
import ai.driftkit.clients.claude.domain.ClaudeOutputFormat.JsonSchemaDefinition;
import ai.driftkit.clients.claude.domain.ClaudeSchemaProperty;
import ai.driftkit.common.domain.client.ResponseFormat;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts DriftKit ResponseFormat to Claude's output_format.
 * <p>
 * Format Mapping:
 * <pre>
 * DriftKit:                          Claude API:
 * ResponseFormat                     output_format
 * ├─ type: JSON_SCHEMA        →     ├─ type: "json_schema"
 * └─ jsonSchema                      └─ schema
 *    ├─ title (ignored)                 ├─ type
 *    ├─ type                            ├─ properties
 *    ├─ properties                      ├─ required
 *    ├─ required                        └─ additionalProperties: false (forced)
 *    ├─ additionalProperties
 *    └─ strict (used for tools)
 * </pre>
 * </p>
 *
 * @see <a href="https://platform.claude.com/docs/en/build-with-claude/structured-outputs">
 *      Claude Structured Outputs Documentation</a>
 */
@Slf4j
@UtilityClass
public class ClaudeSchemaConverter {

    /**
     * Converts DriftKit ResponseFormat to Claude's ClaudeOutputFormat.
     *
     * @param responseFormat DriftKit response format configuration
     * @return ClaudeOutputFormat or null if not applicable
     */
    public static ClaudeOutputFormat convert(ResponseFormat responseFormat) {
        if (responseFormat == null || responseFormat.getType() == null) {
            return null;
        }

        switch (responseFormat.getType()) {
            case JSON_SCHEMA:
                return convertJsonSchema(responseFormat.getJsonSchema());
            case JSON_OBJECT:
                // Claude structured outputs don't support JSON_OBJECT mode directly.
                // Claude requires explicit schema. For JSON_OBJECT behavior,
                // users should use prompting approach instead.
                log.warn("JSON_OBJECT mode is not supported by Claude structured outputs. " +
                         "Use JSON_SCHEMA with explicit schema or rely on prompting.");
                return null;
            default:
                return null;
        }
    }

    /**
     * Converts DriftKit JsonSchema to Claude's output_format structure.
     * <p>
     * Key transformations:
     * <ul>
     *   <li>title field is dropped (Claude doesn't use it)</li>
     *   <li>additionalProperties is forced to false (Claude requirement)</li>
     *   <li>enumValues List&lt;String&gt; maps to Claude's List&lt;Object&gt; (compatible)</li>
     * </ul>
     * </p>
     *
     * @param schema DriftKit JSON schema
     * @return ClaudeOutputFormat or null if schema is null
     */
    private static ClaudeOutputFormat convertJsonSchema(ResponseFormat.JsonSchema schema) {
        if (schema == null) {
            return null;
        }

        // Build the schema definition for Claude
        // Note: DriftKit's 'title' field is intentionally NOT mapped - Claude doesn't use it
        JsonSchemaDefinition definition = JsonSchemaDefinition.builder()
                .type(schema.getType())
                .properties(convertProperties(schema.getProperties()))
                .required(schema.getRequired())
                // CRITICAL: Claude structured outputs REQUIRE additionalProperties: false
                // We force this regardless of DriftKit's setting
                .additionalProperties(false)
                .build();

        return ClaudeOutputFormat.jsonSchema(definition);
    }

    /**
     * Converts property map from DriftKit format to Claude format.
     *
     * @param properties DriftKit properties map
     * @return Claude properties map or null if input is null/empty
     */
    private static Map<String, ClaudeSchemaProperty> convertProperties(
            Map<String, ResponseFormat.SchemaProperty> properties) {
        if (properties == null || properties.isEmpty()) {
            return null;
        }

        Map<String, ClaudeSchemaProperty> result = new LinkedHashMap<>();
        for (Map.Entry<String, ResponseFormat.SchemaProperty> entry : properties.entrySet()) {
            result.put(entry.getKey(), convertProperty(entry.getValue()));
        }
        return result;
    }

    /**
     * Converts a single property from DriftKit format to Claude format.
     * <p>
     * Handles:
     * <ul>
     *   <li>Basic types (string, integer, number, boolean)</li>
     *   <li>Enums (List&lt;String&gt; → List&lt;Object&gt;, compatible conversion)</li>
     *   <li>Nested objects (recursive, with additionalProperties: false)</li>
     *   <li>Arrays with items schema</li>
     * </ul>
     * </p>
     *
     * @param property DriftKit schema property
     * @return Claude schema property or null if input is null
     */
    private static ClaudeSchemaProperty convertProperty(ResponseFormat.SchemaProperty property) {
        if (property == null) {
            return null;
        }

        ClaudeSchemaProperty.ClaudeSchemaPropertyBuilder builder = ClaudeSchemaProperty.builder()
                .type(property.getType())
                .description(property.getDescription());

        // Handle enum values
        // DriftKit uses List<String>, Claude accepts List<Object> (strings, numbers, bools, nulls)
        // String list is a valid subset, so direct conversion works
        if (property.getEnumValues() != null && !property.getEnumValues().isEmpty()) {
            // Convert List<String> to List<Object> for Claude compatibility
            builder.enumValues(new ArrayList<>(property.getEnumValues()));
        }

        // Handle nested object properties
        if (property.getProperties() != null && !property.getProperties().isEmpty()) {
            builder.properties(convertProperties(property.getProperties()));
            builder.required(property.getRequired());
            // CRITICAL: Nested objects also require additionalProperties: false
            builder.additionalProperties(false);
        }

        // Handle array items
        if (property.getItems() != null) {
            builder.items(convertProperty(property.getItems()));
        }

        return builder.build();
    }

    /**
     * Validates that a schema is compatible with Claude's structured outputs.
     * <p>
     * Checks for:
     * <ul>
     *   <li>Null schema</li>
     *   <li>additionalProperties != false on root and nested objects</li>
     * </ul>
     * </p>
     *
     * @param schema Schema to validate
     * @return List of validation errors, empty if valid
     */
    public static List<String> validateSchema(ResponseFormat.JsonSchema schema) {
        List<String> errors = new ArrayList<>();

        if (schema == null) {
            errors.add("Schema cannot be null");
            return errors;
        }

        // Check additionalProperties at root level (warning only, we force it anyway)
        if (schema.getAdditionalProperties() != null &&
            !Boolean.FALSE.equals(schema.getAdditionalProperties())) {
            log.debug("Root schema has additionalProperties != false, will be forced to false for Claude");
        }

        // Validate properties recursively
        validateSchemaRecursive(schema.getProperties(), "", errors);

        return errors;
    }

    /**
     * Recursively validates schema properties.
     */
    private static void validateSchemaRecursive(
            Map<String, ResponseFormat.SchemaProperty> properties,
            String path,
            List<String> errors) {
        if (properties == null) {
            return;
        }

        for (Map.Entry<String, ResponseFormat.SchemaProperty> entry : properties.entrySet()) {
            String propertyPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
            ResponseFormat.SchemaProperty prop = entry.getValue();

            // Recursively validate nested properties
            if (prop.getProperties() != null) {
                validateSchemaRecursive(prop.getProperties(), propertyPath, errors);
            }

            // Validate array items
            if (prop.getItems() != null && prop.getItems().getProperties() != null) {
                validateSchemaRecursive(prop.getItems().getProperties(), propertyPath + "[]", errors);
            }
        }
    }
}
