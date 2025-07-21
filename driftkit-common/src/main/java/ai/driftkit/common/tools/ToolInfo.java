package ai.driftkit.common.tools;

import ai.driftkit.common.domain.client.ModelClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Information about a tool function including method signature analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInfo {
    private String functionName;          // Function name
    private String description;           // Function description
    private Method method;                // The actual method to invoke
    private Object instance;              // Instance to invoke method on (null for static methods)
    private List<String> parameterNames; // Parameter names
    private List<Type> parameterTypes;    // Parameter types
    private Type returnType;              // Return type
    private boolean isStatic;             // Whether this is a static method
    private ModelClient.Tool toolDefinition; // Generated tool definition for LLM
}