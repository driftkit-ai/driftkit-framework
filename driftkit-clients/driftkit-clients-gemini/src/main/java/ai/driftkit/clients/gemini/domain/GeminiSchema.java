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
public class GeminiSchema {
    
    @JsonProperty("type")
    private String type; // OBJECT, ARRAY, STRING, NUMBER, BOOLEAN
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("properties")
    private Map<String, GeminiSchema> properties;
    
    @JsonProperty("items")
    private GeminiSchema items;
    
    @JsonProperty("enum")
    private List<String> enumValues;
    
    @JsonProperty("required")
    private List<String> required;
    
    @JsonProperty("propertyOrdering")
    private List<String> propertyOrdering;
    
    @JsonProperty("nullable")
    private Boolean nullable;
    
    @JsonProperty("format")
    private String format;
}