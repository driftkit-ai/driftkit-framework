package ai.driftkit.context.spring.testsuite.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for evaluations that require manual user inspection and verification
 * These evaluations will initially be marked as PENDING until a human reviewer
 * manually marks them as PASSED or FAILED
 */
@Slf4j
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("MANUAL_EVALUATION")
public class ManualEvalConfig extends EvaluationConfig {
    
    /**
     * Optional instructions for the human reviewer
     */
    private String reviewInstructions;
    
    @Override
    public EvaluationResult.EvaluationOutput evaluate(EvaluationContext context) {
        // Manual evaluations start in PENDING status and require human intervention
        EvaluationResult.EvaluationOutput output = EvaluationResult.EvaluationOutput.builder()
                .status(EvaluationResult.EvaluationStatus.PENDING)
                .message("Awaiting manual review")
                .details(new ManualEvalDetails(getReviewInstructions()))
                .build();
        
        // We don't apply negation to PENDING evaluations
        return output;
    }
    
    /**
     * Details of the manual evaluation
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManualEvalDetails {
        private String reviewInstructions;
    }
}