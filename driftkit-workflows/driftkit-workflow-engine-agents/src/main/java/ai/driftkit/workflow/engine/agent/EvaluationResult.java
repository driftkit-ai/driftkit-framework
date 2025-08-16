package ai.driftkit.workflow.engine.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POJO for evaluation result from evaluator agent.
 * This ensures type-safe parsing of evaluation responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResult {
    
    /**
     * The status of the evaluation.
     */
    private LoopStatus status;
    
    /**
     * Optional feedback message for revision.
     */
    private String feedback;
    
    /**
     * Optional reason for the evaluation result.
     */
    private String reason;
}