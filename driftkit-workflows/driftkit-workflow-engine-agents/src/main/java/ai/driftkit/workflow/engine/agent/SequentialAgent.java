package ai.driftkit.workflow.engine.agent;

import ai.driftkit.common.domain.streaming.StreamingResponse;
import ai.driftkit.common.domain.streaming.BasicStreamingResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Agent that executes a sequence of agents one after another.
 * The output of each agent becomes the input for the next agent.
 */
@Slf4j
@Builder
@Getter
@AllArgsConstructor
public class SequentialAgent implements Agent {
    
    @Singular
    private final List<Agent> agents;
    
    @Builder.Default
    private final String name = "SequentialAgent";
    
    @Builder.Default
    private final String description = "Agent that executes multiple agents in sequence";
    
    @Override
    public String execute(String input) {
        return runSequence(input, null);
    }
    
    @Override
    public String execute(String text, byte[] imageData) {
        if (agents.isEmpty()) {
            return text;
        }
        
        // For multimodal input, only the first agent can handle images
        // Subsequent agents work with text output
        String result = agents.get(0).execute(text, imageData);
        
        for (int i = 1; i < agents.size(); i++) {
            Agent agent = agents.get(i);
            log.debug("SequentialAgent '{}' - executing step {}/{}: {}", 
                     getName(), i + 1, agents.size(), agent.getName());
            result = agent.execute(result);
        }
        
        return result;
    }
    
    @Override
    public String execute(String text, List<byte[]> imageDataList) {
        if (agents.isEmpty()) {
            return text;
        }
        
        // For multimodal input, only the first agent can handle images
        // Subsequent agents work with text output
        String result = agents.get(0).execute(text, imageDataList);
        
        for (int i = 1; i < agents.size(); i++) {
            Agent agent = agents.get(i);
            log.debug("SequentialAgent '{}' - executing step {}/{}: {}", 
                     getName(), i + 1, agents.size(), agent.getName());
            result = agent.execute(result);
        }
        
        return result;
    }
    
    @Override
    public String execute(String input, Map<String, Object> variables) {
        return runSequence(input, variables);
    }
    
    /**
     * Execute the sequence of agents.
     */
    private String runSequence(String input, Map<String, Object> variables) {
        if (agents.isEmpty()) {
            log.warn("SequentialAgent '{}' has no agents to execute", getName());
            return input;
        }
        
        String result = input;
        
        for (int i = 0; i < agents.size(); i++) {
            Agent agent = agents.get(i);
            log.debug("SequentialAgent '{}' - executing step {}/{}: {}", 
                     getName(), i + 1, agents.size(), agent.getName());
            
            try {
                if (variables != null) {
                    result = agent.execute(result, variables);
                } else {
                    result = agent.execute(result);
                }
                
                log.debug("SequentialAgent '{}' - step {} completed", getName(), i + 1);
                
            } catch (Exception e) {
                log.error("SequentialAgent '{}' - step {} failed: {}", 
                         getName(), i + 1, agent.getName(), e);
                throw new RuntimeException(
                    String.format("SequentialAgent step %d failed: %s", i + 1, agent.getName()), e);
            }
        }
        
        log.debug("SequentialAgent '{}' completed all {} steps", getName(), agents.size());
        return result;
    }
    
    @Override
    public StreamingResponse<String> executeStreaming(String input) {
        return executeStreaming(input, null);
    }
    
    @Override
    public StreamingResponse<String> executeStreaming(String input, Map<String, Object> variables) {
        // Sequential agents don't support true streaming since each agent needs the complete output of the previous one
        // Return a basic streaming response with the final result
        String result = runSequence(input, variables);
        return new BasicStreamingResponse<>(Collections.singletonList(result));
    }
}