package ai.driftkit.workflows.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Class representing a combined event that includes both the result of the data processing
 * and the result of the condition evaluation.
 */
@Data
@AllArgsConstructor
public class CombinedEvent implements WorkflowEvent {
    private Object dataResult;          // Result from data processing
    private boolean conditionResult;    // Result of condition evaluation
}
