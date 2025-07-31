package ai.driftkit.clients.gemini.utils;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.clients.gemini.domain.GeminiContent;
import ai.driftkit.clients.gemini.domain.GeminiSchema;
import ai.driftkit.clients.gemini.domain.GeminiTool;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@UtilityClass
public class GeminiUtils {
    
    // Stable models (2.5 series - latest release)
    public static final String GEMINI_PRO_2_5 = "gemini-2.5-pro";
    public static final String GEMINI_FLASH_2_5 = "gemini-2.5-flash";
    public static final String GEMINI_FLASH_LITE_2_5 = "gemini-2.5-flash-lite";

    // Experimental models
    public static final String GEMINI_IMAGE_MODEL = "gemini-2.0-flash-preview-image-generation";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageData {
        private byte[] image;
        private String mimeType;
    }
    
    public static ImageData base64toBytes(String mimeType, String base64Data) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            return new ImageData(imageBytes, mimeType);
        } catch (Exception e) {
            log.error("Error decoding base64 image", e);
            throw new RuntimeException("Failed to decode base64 image", e);
        }
    }
    
    public static String bytesToBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
    
    public static GeminiContent.Part.InlineData createInlineData(byte[] data, String mimeType) {
        return GeminiContent.Part.InlineData.builder()
                .data(bytesToBase64(data))
                .mimeType(mimeType)
                .build();
    }
    
    public static GeminiTool convertToGeminiTool(List<ModelClient.Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        
        List<GeminiTool.FunctionDeclaration> declarations = tools.stream()
                .filter(tool -> tool.getType() == ModelClient.ResponseFormatType.function)
                .map(tool -> {
                    ModelClient.ToolFunction function = tool.getFunction();
                    return GeminiTool.FunctionDeclaration.builder()
                            .name(function.getName())
                            .description(function.getDescription())
                            .parameters(convertFunctionParameters(function.getParameters()))
                            .build();
                })
                .collect(Collectors.toList());
        
        return GeminiTool.builder()
                .functionDeclarations(declarations)
                .build();
    }
    
    private static GeminiTool.FunctionParameters convertFunctionParameters(ModelClient.ToolFunction.FunctionParameters params) {
        if (params == null) {
            return null;
        }
        
        Map<String, GeminiTool.PropertyDefinition> properties = new HashMap<>();
        if (params.getProperties() != null) {
            params.getProperties().forEach((key, value) -> {
                properties.put(key, convertPropertyDefinition(value));
            });
        }
        
        return GeminiTool.FunctionParameters.builder()
                .type("object")
                .properties(properties)
                .required(params.getRequired())
                .build();
    }
    
    private static GeminiTool.PropertyDefinition convertPropertyDefinition(ModelClient.Property property) {
        if (property == null) {
            return null;
        }
        
        GeminiTool.PropertyDefinition.PropertyDefinitionBuilder builder = GeminiTool.PropertyDefinition.builder()
                .type(mapPropertyType(property.getType()))
                .description(property.getDescription())
                .enumValues(property.getEnumValues());
        
        // Handle nested properties for objects
        if (property.getProperties() != null) {
            Map<String, GeminiTool.PropertyDefinition> nestedProps = new HashMap<>();
            property.getProperties().forEach((key, value) -> {
                nestedProps.put(key, convertPropertyDefinition(value));
            });
            builder.properties(nestedProps);
            builder.required(property.getRequired());
        }
        
        // Handle array items
        if (property.getItems() != null) {
            builder.items(convertPropertyDefinition(property.getItems()));
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
    
    public static GeminiSchema convertToGeminiSchema(ai.driftkit.common.domain.client.ResponseFormat.JsonSchema schema) {
        if (schema == null) {
            return null;
        }
        
        GeminiSchema.GeminiSchemaBuilder builder = GeminiSchema.builder()
                .type(mapSchemaType(schema.getType()))
                .description(schema.getTitle()) // Use title as description
                .required(schema.getRequired());
        
        // Convert properties
        if (schema.getProperties() != null) {
            Map<String, GeminiSchema> properties = new HashMap<>();
            schema.getProperties().forEach((key, value) -> {
                properties.put(key, convertSchemaProperty(value));
            });
            builder.properties(properties);
        }
        
        // Handle additional properties
        if (schema.getAdditionalProperties() != null) {
            // Gemini doesn't support additionalProperties directly
            // We'll need to handle this in the generation config
        }
        
        return builder.build();
    }
    
    private static GeminiSchema convertSchemaProperty(ai.driftkit.common.domain.client.ResponseFormat.SchemaProperty property) {
        if (property == null) {
            return null;
        }
        
        GeminiSchema.GeminiSchemaBuilder builder = GeminiSchema.builder()
                .type(mapSchemaType(property.getType()))
                .description(property.getDescription())
                .enumValues(property.getEnumValues())
                .required(property.getRequired());
        
        // Handle nested properties
        if (property.getProperties() != null) {
            Map<String, GeminiSchema> properties = new HashMap<>();
            property.getProperties().forEach((key, value) -> {
                properties.put(key, convertSchemaProperty(value));
            });
            builder.properties(properties);
        }
        
        // Handle array items
        if (property.getItems() != null) {
            builder.items(convertSchemaProperty(property.getItems()));
        }
        
        return builder.build();
    }
    
    private static String mapSchemaType(String type) {
        if (type == null) {
            return "STRING";
        }
        
        switch (type.toLowerCase()) {
            case "string":
                return "STRING";
            case "number":
            case "integer":
                return "NUMBER";
            case "boolean":
                return "BOOLEAN";
            case "array":
                return "ARRAY";
            case "object":
                return "OBJECT";
            default:
                return "STRING";
        }
    }
}