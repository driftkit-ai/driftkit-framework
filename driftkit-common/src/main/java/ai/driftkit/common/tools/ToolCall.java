package ai.driftkit.common.tools;

import ai.driftkit.common.utils.ModelUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a tool call from the language model.
 * Used in responses when the model decides to call a function.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("type")
    private String type = "function";
    
    @JsonProperty("function")
    private FunctionCall function;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("arguments")
        private Map<String, JsonNode> arguments; // Function arguments as JsonNode for flexible type handling
        
        /**
         * Parse argument value to the specified type
         */
        public <T> T getArgumentAs(String argName, Class<T> type) {
            if (arguments == null || !arguments.containsKey(argName)) {
                return null;
            }
            
            JsonNode node = arguments.get(argName);
            if (node == null || node.isNull()) {
                return null;
            }
            
            try {
                // Use ModelUtils ObjectMapper for consistent parsing
                return ModelUtils.OBJECT_MAPPER.treeToValue(node, type);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse argument '" + argName + "' to type " + type.getName(), e);
            }
        }
        
        /**
         * Get argument as a specific type with default value
         */
        public <T> T getArgumentAs(String argName, Class<T> type, T defaultValue) {
            T value = getArgumentAs(argName, type);
            return value != null ? value : defaultValue;
        }
        
        /**
         * Check if argument exists
         */
        public boolean hasArgument(String argName) {
            return arguments != null && arguments.containsKey(argName);
        }
    }
}