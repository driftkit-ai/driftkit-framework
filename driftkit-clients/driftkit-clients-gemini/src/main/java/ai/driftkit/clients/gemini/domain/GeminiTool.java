package ai.driftkit.clients.gemini.domain;

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
public class GeminiTool {
    
    @JsonProperty("functionDeclarations")
    private List<FunctionDeclaration> functionDeclarations;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionDeclaration {
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("parameters")
        private FunctionParameters parameters;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionParameters {
        @JsonProperty("type")
        private String type; // Always "object"
        
        @JsonProperty("properties")
        private Map<String, PropertyDefinition> properties;
        
        @JsonProperty("required")
        private List<String> required;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PropertyDefinition {
        @JsonProperty("type")
        private String type; // string, number, boolean, array, object
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("enum")
        private List<String> enumValues;
        
        @JsonProperty("items")
        private PropertyDefinition items; // For array type
        
        @JsonProperty("properties")
        private Map<String, PropertyDefinition> properties; // For object type
        
        @JsonProperty("required")
        private List<String> required; // For object type
    }
}