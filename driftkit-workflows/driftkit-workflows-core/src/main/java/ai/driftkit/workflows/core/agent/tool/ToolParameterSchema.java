package ai.driftkit.workflows.core.agent.tool;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * POJO for tool parameter schema definition.
 * This class represents the JSON schema for tool parameters.
 */
@Data
@Builder
public class ToolParameterSchema {
    
    private String type;
    private Map<String, PropertySchema> properties;
    private List<String> required;
    
    @Data
    @Builder
    public static class PropertySchema {
        private String type;
        private String description;
        private List<String> enumValues;
        private PropertySchema items; // For array types
        private Map<String, PropertySchema> properties; // For object types
    }
}