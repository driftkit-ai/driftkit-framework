package ai.driftkit.clients.claude.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

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
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InputSchema {
        @JsonProperty("type")
        private String type; // Usually "object"
        
        @JsonProperty("properties")
        private Map<String, SchemaProperty> properties;
        
        @JsonProperty("required")
        private String[] required;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SchemaProperty {
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("enum")
        private String[] enumValues;
        
        @JsonProperty("items")
        private SchemaProperty items; // For array types
        
        @JsonProperty("properties")
        private Map<String, SchemaProperty> properties; // For object types
        
        @JsonProperty("required")
        private String[] required; // For object types
    }
}