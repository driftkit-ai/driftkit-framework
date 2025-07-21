package ai.driftkit.workflows.core.agent.tool;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.tools.ToolInfo;
import ai.driftkit.workflows.core.agent.Agent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper that allows any Agent to be used as a Tool.
 * This enables composition of agents where one agent can call another agent as a tool.
 */
@Slf4j
@RequiredArgsConstructor
public class AgentAsTool implements Tool<SimpleToolArguments> {
    
    private final Agent agent;
    private final String name;
    private final String description;
    
    /**
     * Create a new AgentAsTool wrapper as ToolInfo.
     * 
     * @param name The name of the tool
     * @param description The description of what the tool does
     * @param agent The agent to wrap as a tool
     * @return A new ToolInfo instance representing the agent as a tool
     */
    public static ToolInfo create(String name, String description, Agent agent) {
        AgentAsTool agentAsTool = new AgentAsTool(agent, name, description);
        
        // Create ToolInfo from the AgentAsTool instance
        return ToolInfo.builder()
            .functionName(name)
            .description(description)
            .parameterNames(Arrays.asList("arguments"))
            .parameterTypes(Arrays.asList(SimpleToolArguments.class))
            .returnType(String.class)
            .method(null) // No method for Tool<?> objects
            .instance(agentAsTool) // Store the AgentAsTool instance
            .isStatic(false)
            .toolDefinition(createToolDefinition(agentAsTool))
            .build();
    }
    
    /**
     * Creates a ModelClient.Tool definition from an AgentAsTool instance
     */
    private static ModelClient.Tool createToolDefinition(AgentAsTool agentAsTool) {
        return ModelClient.Tool.builder()
            .type(ModelClient.ResponseFormatType.function)
            .function(ModelClient.ToolFunction.builder()
                .name(agentAsTool.getName())
                .description(agentAsTool.getDescription())
                .parameters(convertToFunctionParameters(agentAsTool.getParametersSchema()))
                .build())
            .build();
    }
    
    /**
     * Converts ToolParameterSchema to FunctionParameters
     */
    private static ModelClient.ToolFunction.FunctionParameters convertToFunctionParameters(ToolParameterSchema schema) {
        Map<String, ModelClient.Property> properties = new HashMap<>();
        
        // Convert each property in the schema
        if (schema.getProperties() != null) {
            for (Map.Entry<String, ToolParameterSchema.PropertySchema> entry : schema.getProperties().entrySet()) {
                ToolParameterSchema.PropertySchema propSchema = entry.getValue();
                ModelClient.Property property = new ModelClient.Property();
                property.setType(ModelClient.ResponseFormatType.fromType(propSchema.getType()));
                property.setDescription(propSchema.getDescription());
                properties.put(entry.getKey(), property);
            }
        }
        
        ModelClient.ToolFunction.FunctionParameters params = new ModelClient.ToolFunction.FunctionParameters();
        params.setType(ModelClient.ResponseFormatType.Object);
        params.setProperties(properties);
        params.setRequired(schema.getRequired());
        
        return params;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public ToolParameterSchema getParametersSchema() {
        // Create schema POJO
        ToolParameterSchema.PropertySchema inputProperty = ToolParameterSchema.PropertySchema.builder()
            .type("string")
            .description("The input text to process")
            .build();
        
        return ToolParameterSchema.builder()
            .type("object")
            .properties(Map.of("input", inputProperty))
            .required(List.of("input"))
            .build();
    }
    
    @Override
    public Class<SimpleToolArguments> getArgumentType() {
        return SimpleToolArguments.class;
    }
    
    @Override
    public String execute(SimpleToolArguments arguments) throws Exception {
        // Execute the wrapped agent
        String result = agent.execute(arguments.getInput());
        
        log.debug("AgentAsTool '{}' executed successfully", name);
        return result;
    }
}