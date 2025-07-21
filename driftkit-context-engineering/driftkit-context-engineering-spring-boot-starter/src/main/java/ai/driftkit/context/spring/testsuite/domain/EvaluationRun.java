package ai.driftkit.context.spring.testsuite.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/**
 * Represents a test run for evaluations, optionally with an alternative prompt
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "evaluation_runs")
public class EvaluationRun {
    @Id
    private String id;
    private String testSetId;
    private String name;
    private String description;
    
    // If specified, this prompt will be used instead of the original one
    private String alternativePromptId;
    private String alternativePromptTemplate;
    
    // Either modelId OR workflow should be specified, not both
    private String modelId;      // Direct model ID (like "gpt-4-turbo")
    private String workflow;     // Workflow ID for more advanced processing
    private Double temperature;
    
    // Run status
    private RunStatus status;
    private Long startedAt;
    private Long completedAt;
    
    // Image Test Settings
    @Builder.Default
    private boolean regenerateImages = true;  // If true, will regenerate images instead of using existing ones
    
    // Statistics
    private Map<String, Integer> statusCounts;  // Map of status -> count
    
    /**
     * Status of the evaluation run
     */
    public enum RunStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        PENDING  // Used when manual evaluations are waiting for human review
    }
}