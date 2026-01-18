package ai.driftkit.clients.claude.utils;

import ai.driftkit.clients.claude.domain.ClaudeContent;
import ai.driftkit.clients.claude.domain.ClaudeSchemaProperty;
import ai.driftkit.clients.claude.domain.ClaudeTool;
import ai.driftkit.common.domain.client.ModelClient;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@UtilityClass
public class ClaudeUtils {

    // Model constants - Claude 4 series (latest)
    public static final String CLAUDE_OPUS_4 = "claude-opus-4-5-20251101";
    public static final String CLAUDE_SONNET_4 = "claude-sonnet-4-5-20250929";
    public static final String CLAUDE_HAIKU_3_5 = "claude-3-5-haiku-20241022";

    // Older models (for compatibility)
    public static final String CLAUDE_SONNET_3_5 = "claude-3-5-sonnet-20241022";

    public static final String CLAUDE_PREFIX = "claude";

    public static String bytesToBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] base64ToBytes(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    public static ClaudeContent.ImageSource createImageSource(byte[] imageData, String mimeType) {
        return ClaudeContent.ImageSource.builder()
                .type("base64")
                .mediaType(mimeType)
                .data(bytesToBase64(imageData))
                .build();
    }

    /**
     * Converts DriftKit tools to Claude tools without strict mode.
     *
     * @param tools DriftKit tool definitions
     * @return List of ClaudeTool or null if empty
     */
    public static List<ClaudeTool> convertToClaudeTools(List<ModelClient.Tool> tools) {
        return convertToClaudeTools(tools, false);
    }

    /**
     * Converts DriftKit tools to Claude tools with optional strict mode.
     * <p>
     * When strict mode is enabled:
     * <ul>
     *   <li>Sets strict: true on each tool</li>
     *   <li>Sets additionalProperties: false on all schemas</li>
     * </ul>
     * </p>
     *
     * @param tools DriftKit tool definitions
     * @param strictMode Whether to enable strict validation
     * @return List of ClaudeTool or null if empty
     */
    public static List<ClaudeTool> convertToClaudeTools(List<ModelClient.Tool> tools, boolean strictMode) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }

        return tools.stream()
                .filter(tool -> tool.getType() == ModelClient.ResponseFormatType.function)
                .map(tool -> {
                    ModelClient.ToolFunction function = tool.getFunction();
                    ClaudeTool.ClaudeToolBuilder builder = ClaudeTool.builder()
                            .name(function.getName())
                            .description(function.getDescription())
                            .inputSchema(convertToInputSchema(function.getParameters(), strictMode));

                    // Set strict mode if enabled
                    if (strictMode) {
                        builder.strict(true);
                    }

                    return builder.build();
                })
                .collect(Collectors.toList());
    }

    private static ClaudeTool.InputSchema convertToInputSchema(
            ModelClient.ToolFunction.FunctionParameters params,
            boolean strictMode) {
        if (params == null) {
            return null;
        }

        Map<String, ClaudeSchemaProperty> properties = new HashMap<>();
        if (params.getProperties() != null) {
            params.getProperties().forEach((key, value) -> {
                properties.put(key, convertToSchemaProperty(value, strictMode));
            });
        }

        ClaudeTool.InputSchema.InputSchemaBuilder builder = ClaudeTool.InputSchema.builder()
                .type("object")
                .properties(properties)
                .required(params.getRequired());

        // Strict mode requires additionalProperties: false
        if (strictMode) {
            builder.additionalProperties(false);
        }

        return builder.build();
    }

    private static ClaudeSchemaProperty convertToSchemaProperty(
            ModelClient.Property property,
            boolean strictMode) {
        if (property == null) {
            return null;
        }

        ClaudeSchemaProperty.ClaudeSchemaPropertyBuilder builder = ClaudeSchemaProperty.builder()
                .type(mapPropertyType(property.getType()))
                .description(property.getDescription());

        if (property.getEnumValues() != null) {
            builder.enumValues(new ArrayList<>(property.getEnumValues()));
        }

        // Handle nested properties for objects
        if (property.getProperties() != null) {
            Map<String, ClaudeSchemaProperty> nestedProps = new HashMap<>();
            property.getProperties().forEach((key, value) -> {
                nestedProps.put(key, convertToSchemaProperty(value, strictMode));
            });
            builder.properties(nestedProps);
            builder.required(property.getRequired());
            // Strict mode requires additionalProperties: false on nested objects
            if (strictMode) {
                builder.additionalProperties(false);
            }
        }

        // Handle array items
        if (property.getItems() != null) {
            builder.items(convertToSchemaProperty(property.getItems(), strictMode));
        }

        return builder.build();
    }

    private static String mapPropertyType(ModelClient.ResponseFormatType type) {
        if (type == null) {
            return "string";
        }

        switch (type) {
            case String:
            case Enum:
                return "string";
            case Number:
                return "number";
            case Integer:
                return "integer";
            case Boolean:
                return "boolean";
            case Array:
                return "array";
            case Object:
                return "object";
            default:
                return "string";
        }
    }

    public static String determineBestModel(String requestedModel) {
        if (requestedModel != null && !requestedModel.isEmpty()) {
            return requestedModel;
        }
        return CLAUDE_SONNET_4; // Default to Sonnet 4
    }
}
