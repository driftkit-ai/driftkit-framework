package ai.driftkit.workflow.engine.agent;

import ai.driftkit.common.domain.streaming.StreamingResponse;
import ai.driftkit.common.domain.streaming.BasicStreamingResponse;
import ai.driftkit.workflow.engine.core.pipeline.PipelineDefinition;
import ai.driftkit.workflow.engine.core.pipeline.PipelineRegistry;
import ai.driftkit.workflow.engine.core.pipeline.PipelineStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
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

    /** True if user explicitly set .name() in builder — enables pipeline registration and trace context. */
    @Builder.Default
    private final boolean customName = false;

    @Builder.Default
    private final String description = "Agent that executes multiple agents in sequence";

    private transient volatile boolean registered = false;

    @Override
    public String execute(String input) {
        registerIfNamed();
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
        
        boolean isNamedPipeline = customName;

        for (int i = 0; i < agents.size(); i++) {
            Agent agent = agents.get(i);
            log.debug("SequentialAgent '{}' - executing step {}/{}: {}",
                     getName(), i + 1, agents.size(), agent.getName());

            // Inject hierarchical trace context for named pipelines
            if (isNamedPipeline && agent instanceof LLMAgent llmAgent) {
                llmAgent.setWorkflowContext(getName(), "step-" + i + "-" + agent.getName());
            }

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
    
    private void registerIfNamed() {
        if (registered || !customName) return;
        registered = true;
        try {
            List<PipelineStep> steps = new ArrayList<>();
            for (int i = 0; i < agents.size(); i++) {
                Agent agent = agents.get(i);
                steps.add(PipelineStep.builder()
                        .stepId(agent.getName())
                        .agentName(agent.getName())
                        .order(i)
                        .type(PipelineStep.StepType.LLM_CALL)
                        .build());
            }
            PipelineRegistry.getInstance().register(PipelineDefinition.builder()
                    .id(getName())
                    .name(getName())
                    .description(getDescription())
                    .type(PipelineDefinition.PipelineType.SEQUENTIAL_AGENT)
                    .steps(steps)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to register SequentialAgent '{}' in pipeline registry", getName(), e);
        }
    }
}