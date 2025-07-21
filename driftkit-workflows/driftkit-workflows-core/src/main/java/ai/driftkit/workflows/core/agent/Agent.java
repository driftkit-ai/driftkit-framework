package ai.driftkit.workflows.core.agent;

import java.util.List;
import java.util.Map;

/**
 * Base interface for all agents in the simplified DriftKit agent system.
 * Agents are simplified wrappers around complex DriftKit workflows that provide
 * easy-to-use interfaces for common AI operations.
 */
public interface Agent {
    
    /**
     * Execute the agent with a simple text input.
     * 
     * @param input The text input to process
     * @return The agent's response as a string
     */
    String execute(String input);
    
    /**
     * Execute the agent with text and image input.
     * 
     * @param text The text input to process
     * @param imageData Raw image data as byte array
     * @return The agent's response as a string
     */
    String execute(String text, byte[] imageData);
    
    /**
     * Execute the agent with text and multiple images.
     * 
     * @param text The text input to process
     * @param imageDataList List of raw image data as byte arrays
     * @return The agent's response as a string
     */
    String execute(String text, List<byte[]> imageDataList);
    
    /**
     * Execute the agent with input and context variables.
     * 
     * @param input The text input to process
     * @param variables Context variables for template processing
     * @return The agent's response as a string
     */
    String execute(String input, Map<String, Object> variables);
    
    /**
     * Get the agent's name/identifier.
     * 
     * @return The agent's name
     */
    String getName();
    
    /**
     * Get the agent's description.
     * 
     * @return The agent's description
     */
    String getDescription();
}