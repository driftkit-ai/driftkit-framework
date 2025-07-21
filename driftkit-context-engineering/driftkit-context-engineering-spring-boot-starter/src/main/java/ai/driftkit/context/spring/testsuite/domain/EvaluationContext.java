package ai.driftkit.context.spring.testsuite.domain;

import ai.driftkit.context.spring.testsuite.domain.TestSetItem;
import ai.driftkit.workflows.spring.service.AIService;
import lombok.Builder;
import lombok.Data;

/**
 * Context for evaluation execution
 */
@Data
@Builder
public class EvaluationContext {
    /**
     * The test set item being evaluated
     */
    private TestSetItem testSetItem;
    
    /**
     * The original result from the test set item
     */
    private String originalResult;
    
    /**
     * The actual result to evaluate (may be different if using alternative prompt)
     */
    private String actualResult;
    
    /**
     * The AIService instance (for LLM evaluations)
     */
    private AIService aiService;
    
    /**
     * Additional context data for evaluation (if needed)
     */
    private Object additionalContext;
}