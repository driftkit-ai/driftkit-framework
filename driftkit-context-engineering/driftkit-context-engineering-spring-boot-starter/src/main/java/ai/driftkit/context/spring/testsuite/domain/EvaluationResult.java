package ai.driftkit.context.spring.testsuite.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Results of evaluation runs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "evaluation_results")
public class EvaluationResult {
    @Id
    private String id;
    private String evaluationId;
    private String testSetItemId;
    private String runId;  // To group results from the same run
    private EvaluationStatus status;
    private String message;
    private Object details;  // Additional details specific to evaluation type
    private Long createdAt;
    
    // Enhanced data for better debugging and analysis
    private String originalPrompt;     // The prompt used for the request
    private String modelResult;        // The actual model response
    private Object promptVariables;    // Variables used in the prompt
    private Long processingTimeMs;     // Time taken to process this evaluation
    private String errorDetails;       // Detailed error information if available
    
    /**
     * Status of the evaluation result
     */
    public enum EvaluationStatus {
        PASSED,
        FAILED,
        ERROR,
        SKIPPED,
        PENDING   // Used for manual evaluations awaiting human review
    }
    
    /**
     * Output of an evaluation, to be stored in the result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EvaluationOutput {
        private EvaluationStatus status;
        private String message;
        private Object details;
        private String originalPrompt;
        private String modelResult;
        private Object promptVariables;
        private Long processingTimeMs;
        private String errorDetails;
    }
}