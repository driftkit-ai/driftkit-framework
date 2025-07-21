package ai.driftkit.workflows.core.domain.enhanced;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Standardized result format for EnhancedReasoningWorkflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnhancedReasoningResult {
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * The original user query
     */
    @JsonProperty("query")
    private String query;
    
    /**
     * The final result content
     */
    @JsonProperty("result")
    private JsonNode result;
    
    /**
     * Checklist items used for validation
     */
    @JsonProperty("checklist")
    private List<EnhancedReasoningPlan.ChecklistItem> checklist;
    
    /**
     * Validation results for each checklist item
     */
    @JsonProperty("validation")
    private List<EnhancedReasoningValidation.ValidationItem> validationItems;
    
    /**
     * Overall confidence score (0.0 to 1.0)
     */
    @JsonProperty("confidence")
    private double confidence;
    
    /**
     * Whether the result is satisfactory based on validation
     */
    @JsonProperty("is_satisfactory") 
    private boolean isSatisfactory;
    
    /**
     * Whether the fallback workflow was used
     */
    @JsonProperty("used_fallback")
    private boolean usedFallback;
    
    /**
     * History of all attempts (not serialized in JSON response)
     */
    @JsonIgnore
    private List<AttemptRecord> attemptHistory;
    
    /**
     * Create result object from normal execution path
     */
    @SneakyThrows
    public static EnhancedReasoningResult fromEnhancedReasoning(
            String query, 
            JsonNode result,
            List<EnhancedReasoningPlan.ChecklistItem> checklist,
            EnhancedReasoningValidation validation) {
        
        return EnhancedReasoningResult.builder()
                .query(query)
                .result(result)
                .checklist(checklist)
                .validationItems(validation != null ? validation.getItems() : Collections.emptyList())
                .confidence(validation != null ? validation.getConfidence() : 0.0)
                .isSatisfactory(validation != null ? validation.isSatisfactory() : false)
                .usedFallback(false)
                .attemptHistory(new ArrayList<>())
                .build();
    }
    
    /**
     * Create result object from fallback execution path
     */
    @SneakyThrows
    public static EnhancedReasoningResult fromFallback(String query, String result) {
        return EnhancedReasoningResult.builder()
                .query(query)
                .result(mapper.readTree(result))
                .checklist(Collections.emptyList())
                .validationItems(Collections.emptyList())
                .confidence(1.0) // We assume the fallback workflow produced correct results
                .isSatisfactory(true)
                .usedFallback(true)
                .attemptHistory(new ArrayList<>())
                .build();
    }
    
    /**
     * Class to store information about each attempt
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttemptRecord {
        private int attemptNumber;
        private EnhancedReasoningPlan plan;
        private EnhancedReasoningValidation validation;
        private double confidence;
        private boolean satisfactory;
    }
}