package ai.driftkit.clients.claude.utils;

import ai.driftkit.clients.claude.domain.ClaudeContent;
import ai.driftkit.clients.claude.domain.ClaudeTool;
import ai.driftkit.common.domain.client.ModelClient;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@UtilityClass
public class ClaudeUtils {
    
    // Model constants - Claude 4 series (latest)
    public static final String CLAUDE_OPUS_4 = "claude-opus-4-20250514";
    public static final String CLAUDE_SONNET_4 = "claude-sonnet-4-20250514";
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
    
    public static List<ClaudeTool> convertToClaudeTools(List<ModelClient.Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        
        return tools.stream()
                .filter(tool -> tool.getType() == ModelClient.ResponseFormatType.function)
                .map(tool -> {
                    ModelClient.ToolFunction function = tool.getFunction();
                    return ClaudeTool.builder()
                            .name(function.getName())
                            .description(function.getDescription())
                            .inputSchema(convertToInputSchema(function.getParameters()))
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    private static ClaudeTool.InputSchema convertToInputSchema(ModelClient.ToolFunction.FunctionParameters params) {
        if (params == null) {
            return null;
        }
        
        Map<String, ClaudeTool.SchemaProperty> properties = new HashMap<>();
        if (params.getProperties() != null) {
            params.getProperties().forEach((key, value) -> {
                properties.put(key, convertToSchemaProperty(value));
            });
        }
        
        return ClaudeTool.InputSchema.builder()
                .type("object")
                .properties(properties)
                .required(params.getRequired() != null ? params.getRequired().toArray(new String[0]) : null)
                .build();
    }
    
    private static ClaudeTool.SchemaProperty convertToSchemaProperty(ModelClient.Property property) {
        if (property == null) {
            return null;
        }
        
        ClaudeTool.SchemaProperty.SchemaPropertyBuilder builder = ClaudeTool.SchemaProperty.builder()
                .type(mapPropertyType(property.getType()))
                .description(property.getDescription());
        
        if (property.getEnumValues() != null) {
            builder.enumValues(property.getEnumValues().toArray(new String[0]));
        }
        
        // Handle nested properties for objects
        if (property.getProperties() != null) {
            Map<String, ClaudeTool.SchemaProperty> nestedProps = new HashMap<>();
            property.getProperties().forEach((key, value) -> {
                nestedProps.put(key, convertToSchemaProperty(value));
            });
            builder.properties(nestedProps);
            if (property.getRequired() != null) {
                builder.required(property.getRequired().toArray(new String[0]));
            }
        }
        
        // Handle array items
        if (property.getItems() != null) {
            builder.items(convertToSchemaProperty(property.getItems()));
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