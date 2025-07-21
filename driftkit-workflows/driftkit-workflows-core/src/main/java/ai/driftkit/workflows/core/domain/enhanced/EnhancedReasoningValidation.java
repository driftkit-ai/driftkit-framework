package ai.driftkit.workflows.core.domain.enhanced;

import ai.driftkit.workflows.core.domain.enhanced.EnhancedReasoningPlan.ChecklistSeverity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents the result of validating a reasoning result against a checklist
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnhancedReasoningValidation {

    /**
     * Overall satisfaction level (0.0 to 1.0)
     */
    @JsonProperty("confidence")
    private double confidence;
    
    /**
     * Whether the result is satisfactory based on the checklist
     */
    @JsonProperty("is_satisfactory")
    private boolean isSatisfactory;
    
    /**
     * Improved result if the original result needed fixing
     */
    @JsonProperty("result")
    private JsonNode result;
    
    /**
     * Validation results for each checklist item
     */
    @JsonProperty("items")
    private List<ValidationItem> items;
    
    /**
     * Feedback for the result that failed validation
     */
    @JsonProperty("feedback")
    private String feedback;
    
    /**
     * Represents validation results for a single checklist item
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationItem {
        /**
         * Reference to the original checklist item description
         */
        @JsonProperty("description")
        private String description;
        
        /**
         * The severity level from the checklist item
         */
        @JsonProperty("severity")
        private EnhancedReasoningPlan.ChecklistSeverity severity;
        
        /**
         * Score for this validation item (0.0 to 1.0)
         */
        @JsonProperty("rating")
        private double rating;
        
        /**
         * Whether this item passed validation
         */
        @JsonProperty("passed")
        private boolean passed;
        
        /**
         * Specific feedback for this item
         */
        @JsonProperty("feedback")
        private String feedback;
    }
    
    /**
     * Calculate overall satisfaction rating based on severity weighted scores
     * 
     * @return true if the result meets the satisfaction threshold
     */
    public boolean calculateOverallSatisfaction(int iterations, double threshold) {
        if (items == null || items.isEmpty()) {
            return confidence >= threshold;
        }
        
        // Check if any critical items failed
        boolean anyCriticalFailed = items.stream()
                .anyMatch(item -> {
                    if (item.severity == EnhancedReasoningPlan.ChecklistSeverity.CRITICAL && !item.passed) {
                        return true;
                    }

                    if (iterations <= 1) {
                        return item.severity == ChecklistSeverity.HIGH && !item.passed;
                    }

                    return false;
                });
        
        if (anyCriticalFailed) {
            return false;
        }
        
        // Calculate weighted score
        double totalWeight = 0;
        double weightedSum = 0;
        
        for (ValidationItem item : items) {
            double weight = getWeightForSeverity(item.severity);
            totalWeight += weight;
            weightedSum += weight * item.rating;
        }
        
        confidence = totalWeight > 0 ? weightedSum / totalWeight : 0.5;
        return confidence >= threshold;
    }
    
    /**
     * Get weight value based on severity level
     */
    private double getWeightForSeverity(EnhancedReasoningPlan.ChecklistSeverity severity) {
        switch (severity) {
            case CRITICAL:
                return 4.0;
            case HIGH:
                return 2.0;
            case MEDIUM:
                return 1.0;
            case LOW:
                return 0.5;
            default:
                return 1.0;
        }
    }
}