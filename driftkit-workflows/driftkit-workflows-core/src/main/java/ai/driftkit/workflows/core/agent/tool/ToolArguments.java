package ai.driftkit.workflows.core.agent.tool;

import lombok.Data;

import java.util.Map;

/**
 * Base class for tool arguments.
 * Specific tools should extend this class or use the generic version.
 */
@Data
public class ToolArguments {
    
    /**
     * Raw arguments as a map for flexible tools.
     */
    private Map<String, Object> arguments;
    
    /**
     * Get a specific argument value.
     * 
     * @param key The argument key
     * @return The argument value
     */
    public Object get(String key) {
        return arguments != null ? arguments.get(key) : null;
    }
    
    /**
     * Get a specific argument value as string.
     * 
     * @param key The argument key
     * @return The argument value as string
     */
    public String getString(String key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }
}