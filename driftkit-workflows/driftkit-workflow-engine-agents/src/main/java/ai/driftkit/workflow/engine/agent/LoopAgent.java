package ai.driftkit.workflow.engine.agent;

import ai.driftkit.common.domain.client.ResponseFormat;
import ai.driftkit.common.domain.streaming.StreamingResponse;
import ai.driftkit.common.domain.streaming.BasicStreamingResponse;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.context.core.util.DefaultPromptLoader;
import ai.driftkit.workflow.engine.core.pipeline.PipelineDefinition;
import ai.driftkit.workflow.engine.core.pipeline.PipelineRegistry;
import ai.driftkit.workflow.engine.core.pipeline.PipelineStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent that executes a worker agent in a loop until a stop condition is met.
 * The evaluator agent determines whether to continue or stop the loop.
 */
@Slf4j
@Builder
@Getter
@AllArgsConstructor
public class LoopAgent implements Agent {
    
    private final Agent worker;
    private final Agent evaluator;
    private final LoopStatus stopCondition;
    
    @Builder.Default
    private final String name = "LoopAgent";

    /** True if user explicitly set .name() in builder — enables pipeline registration and trace context. */
    @Builder.Default
    private final boolean customName = false;

    @Builder.Default
    private final String description = "Agent that executes work in a loop until condition is met";
    
    @Builder.Default
    private final int maxIterations = 10;

    private transient volatile boolean registered = false;

    @Override
    public String execute(String input) {
        registerIfNamed();
        return runLoop(input, null);
    }
    
    @Override
    public String execute(String text, byte[] imageData) {
        return worker.execute(text, imageData);
    }
    
    @Override
    public String execute(String text, List<byte[]> imageDataList) {
        return worker.execute(text, imageDataList);
    }
    
    @Override
    public String execute(String input, Map<String, Object> variables) {
        return runLoop(input, variables);
    }
    
    /**
     * Execute the loop with worker and evaluator agents.
     */
    private String runLoop(String input, Map<String, Object> variables) {
        String currentResult = input;
        int iteration = 0;
        
        boolean isNamedPipeline = customName;

        while (iteration < maxIterations) {
            iteration++;
            log.debug("LoopAgent '{}' - iteration {}/{}", getName(), iteration, maxIterations);

            // Inject hierarchical trace context for named pipelines
            if (isNamedPipeline) {
                if (worker instanceof LLMAgent llmWorker) {
                    llmWorker.setWorkflowContext(getName(), "worker-iter-" + iteration);
                }
                if (evaluator instanceof LLMAgent llmEval) {
                    llmEval.setWorkflowContext(getName(), "evaluator-iter-" + iteration);
                }
            }

            try {
                // Execute worker agent
                String workerResult;
                if (variables != null) {
                    workerResult = worker.execute(currentResult, variables);
                } else {
                    workerResult = worker.execute(currentResult);
                }

                // Evaluate the result
                EvaluationResult evaluationResult;

                // If evaluator is an LLMAgent, use structured output
                if (evaluator instanceof LLMAgent) {
                    LLMAgent llmEvaluator = (LLMAgent) evaluator;
                    String evaluationInput = buildStructuredEvaluationInput(currentResult, workerResult);
                    AgentResponse<EvaluationResult> response = llmEvaluator.executeStructured(
                        evaluationInput, 
                        EvaluationResult.class
                    );
                    evaluationResult = response.getStructuredData();
                } else {
                    // Fallback to traditional JSON parsing approach
                    String evaluationInput = buildEvaluationInput(currentResult, workerResult);
                    String evaluationResponse;
                    if (variables != null) {
                        evaluationResponse = evaluator.execute(evaluationInput, variables);
                    } else {
                        evaluationResponse = evaluator.execute(evaluationInput);
                    }
                    evaluationResult = parseEvaluationResult(evaluationResponse);
                }
                
                log.debug("LoopAgent '{}' - evaluation status: {}", getName(), evaluationResult.getStatus());
                
                // Check stop condition
                if (evaluationResult.getStatus() == stopCondition) {
                    log.debug("LoopAgent '{}' - stop condition met after {} iterations", getName(), iteration);
                    return workerResult;
                }
                
                // Handle different statuses
                switch (evaluationResult.getStatus()) {
                    case REVISE:
                        currentResult = buildRevisionInput(workerResult, evaluationResult.getFeedback());
                        break;
                    case RETRY:
                        // Keep the same input for retry
                        break;
                    case FAILED:
                        throw new RuntimeException("Evaluator indicated failure: " + evaluationResult.getReason());
                    case CONTINUE:
                    default:
                        currentResult = workerResult;
                        break;
                }
                
            } catch (Exception e) {
                log.error("Error in LoopAgent '{}' iteration {}", getName(), iteration, e);
                throw new RuntimeException("LoopAgent execution failed at iteration " + iteration, e);
            }
        }
        
        log.warn("LoopAgent '{}' reached maximum iterations ({})", getName(), maxIterations);
        return currentResult;
    }
    
    /**
     * Build input for the evaluator agent with structured output.
     * Uses DefaultPromptLoader to get prompt from resources or PromptService.
     */
    private String buildStructuredEvaluationInput(String originalInput, String workerResult) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("originalRequest", originalInput);
        variables.put("generatedResult", workerResult);
        
        return DefaultPromptLoader.loadPrompt("loop.agent.structured.evaluation", variables);
    }
    
    /**
     * Build input for the evaluator agent (legacy JSON format).
     * Uses DefaultPromptLoader to get prompt from resources or PromptService.
     */
    private String buildEvaluationInput(String originalInput, String workerResult) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("originalRequest", originalInput);
        variables.put("generatedResult", workerResult);
        
        return DefaultPromptLoader.loadPrompt("loop.agent.json.evaluation", variables);
    }
    
    /**
     * Build input for revision based on evaluator feedback.
     * Uses DefaultPromptLoader to get prompt from resources or PromptService.
     */
    private String buildRevisionInput(String workerResult, String feedback) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("previousResult", workerResult);
        
        if (StringUtils.isNotBlank(feedback)) {
            variables.put("feedback", feedback);
            return DefaultPromptLoader.loadPrompt("loop.agent.revision", variables);
        } else {
            return DefaultPromptLoader.loadPrompt("loop.agent.revision.no_feedback", variables);
        }
    }
    
    /**
     * Parse the evaluation response as JSON to get typed result.
     */
    private EvaluationResult parseEvaluationResult(String evaluationResponse) {
        try {
            // Try to parse as JSON first
            EvaluationResult result = JsonUtils.safeParse(evaluationResponse, EvaluationResult.class);
            if (result != null && result.getStatus() != null) {
                return result;
            }
        } catch (Exception e) {
            log.debug("Failed to parse evaluation response as JSON, falling back to enum analysis", e);
        }
        
        // Fallback to enum name analysis if JSON parsing fails
        return fallbackEnumAnalysis(evaluationResponse);
    }
    
    /**
     * Fallback method to analyze text response using enum names if JSON parsing fails.
     */
    private EvaluationResult fallbackEnumAnalysis(String response) {
        if (StringUtils.isBlank(response)) {
            return EvaluationResult.builder()
                .status(LoopStatus.CONTINUE)
                .build();
        }
        
        String upperResponse = response.toUpperCase();
        
        // Check for each enum value by name
        for (LoopStatus status : LoopStatus.values()) {
            if (upperResponse.contains(status.name())) {
                return EvaluationResult.builder()
                    .status(status)
                    .feedback(response)
                    .build();
            }
        }
        
        // Default to CONTINUE if no enum match found
        return EvaluationResult.builder()
            .status(LoopStatus.CONTINUE)
            .feedback(response)
            .build();
    }
    
    private void registerIfNamed() {
        if (registered || !customName) return;
        registered = true;
        try {
            PipelineRegistry.getInstance().register(PipelineDefinition.builder()
                    .id(getName())
                    .name(getName())
                    .description(getDescription())
                    .type(PipelineDefinition.PipelineType.LOOP_AGENT)
                    .steps(java.util.List.of(
                            PipelineStep.builder().stepId("worker").agentName(worker.getName()).order(0).type(PipelineStep.StepType.LOOP_WORKER).build(),
                            PipelineStep.builder().stepId("evaluator").agentName(evaluator.getName()).order(1).type(PipelineStep.StepType.LOOP_EVALUATOR).build()
                    ))
                    .config(java.util.Map.of("maxIterations", maxIterations, "stopCondition", stopCondition.name()))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to register LoopAgent '{}' in pipeline registry", getName(), e);
        }
    }
}