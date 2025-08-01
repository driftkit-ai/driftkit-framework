package ai.driftkit.workflows.core.agent;

import ai.driftkit.common.utils.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

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
    
    @Builder.Default
    private final String description = "Agent that executes work in a loop until condition is met";
    
    @Builder.Default
    private final int maxIterations = 10;
    
    @Override
    public String execute(String input) {
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
        
        while (iteration < maxIterations) {
            iteration++;
            log.debug("LoopAgent '{}' - iteration {}/{}", getName(), iteration, maxIterations);
            
            try {
                // Execute worker agent
                String workerResult;
                if (variables != null) {
                    workerResult = worker.execute(currentResult, variables);
                } else {
                    workerResult = worker.execute(currentResult);
                }
                
                // Evaluate the result
                String evaluationInput = buildEvaluationInput(currentResult, workerResult);
                String evaluationResponse;
                if (variables != null) {
                    evaluationResponse = evaluator.execute(evaluationInput, variables);
                } else {
                    evaluationResponse = evaluator.execute(evaluationInput);
                }
                
                // Parse evaluation result as JSON
                EvaluationResult evaluationResult = parseEvaluationResult(evaluationResponse);
                
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
     * Build input for the evaluator agent.
     */
    private String buildEvaluationInput(String originalInput, String workerResult) {
        String statusOptions = String.join("|", 
            LoopStatus.COMPLETE.name(),
            LoopStatus.REVISE.name(), 
            LoopStatus.RETRY.name(),
            LoopStatus.FAILED.name(),
            LoopStatus.CONTINUE.name()
        );
        
        return String.format("Original request: %s\n\nGenerated result: %s\n\nPlease respond with JSON in format: {\"status\": \"%s\", \"feedback\": \"optional feedback\", \"reason\": \"optional reason\"}", 
                            originalInput, workerResult, statusOptions);
    }
    
    /**
     * Build input for revision based on evaluator feedback.
     */
    private String buildRevisionInput(String workerResult, String feedback) {
        if (StringUtils.isNotBlank(feedback)) {
            return String.format("Previous result: %s\n\nFeedback for improvement: %s", workerResult, feedback);
        } else {
            return String.format("Previous result needs revision: %s", workerResult);
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
}