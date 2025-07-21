package ai.driftkit.workflows.core.agent.tool;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Simple tool arguments with a single input field.
 * Used for AgentAsTool and other simple tools.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SimpleToolArguments extends ToolArguments {
    
    private String input;
}