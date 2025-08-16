package ai.driftkit.workflow.engine.agent.tool;

/**
 * Interface for tools that can be used by agents.
 * Tools provide functionality that agents can call to perform specific tasks.
 */
public interface Tool<T extends ToolArguments> {
    
    /**
     * Get the name of the tool.
     * 
     * @return The tool's name
     */
    String getName();
    
    /**
     * Get the description of what the tool does.
     * 
     * @return The tool's description
     */
    String getDescription();
    
    /**
     * Get the parameter schema for the tool.
     * 
     * @return The parameters schema as POJO
     */
    ToolParameterSchema getParametersSchema();
    
    /**
     * Get the argument type class for this tool.
     * Used by the framework to parse JSON arguments into typed objects.
     * 
     * @return The argument type class
     */
    Class<T> getArgumentType();
    
    /**
     * Execute the tool with the given arguments.
     * 
     * @param arguments The parsed arguments as a typed object
     * @return The result of the tool execution
     * @throws Exception if tool execution fails
     */
    String execute(T arguments) throws Exception;
}