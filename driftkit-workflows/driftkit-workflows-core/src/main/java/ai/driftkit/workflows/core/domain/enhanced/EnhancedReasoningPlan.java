package ai.driftkit.workflows.core.domain.enhanced;

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
 * Represents a planning and execution plan with a checklist for validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnhancedReasoningPlan {

    /**
     * The original user query
     */
    private String query;
    
    /**
     * The result of processing the query
     */
    @JsonProperty("result")
    private JsonNode result;
    
    /**
     * List of checklist items to validate the result
     */
    @JsonProperty("checklist")
    private List<ChecklistItem> checklist;
    
    /**
     * Represents a single checklist item for validation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChecklistItem {
        /**
         * Description of the validation criterion
         */
        @JsonProperty("description")
        private String description;
        
        /**
         * The importance level of this criterion
         * (critical, high, medium, low)
         */
        @JsonProperty("severity")
        private ChecklistSeverity severity;
    }
    
    /**
     * Enum representing severity levels for checklist items
     */
    public enum ChecklistSeverity {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW;
        
        public boolean isHigherThan(ChecklistSeverity other) {
            return this.ordinal() < other.ordinal();
        }
    }
}