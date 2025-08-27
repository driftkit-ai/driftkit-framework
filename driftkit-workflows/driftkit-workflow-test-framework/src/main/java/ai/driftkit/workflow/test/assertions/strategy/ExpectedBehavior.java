package ai.driftkit.workflow.test.assertions.strategy;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Describes expected workflow behavior for assertions.
 */
@Data
@Builder
public class ExpectedBehavior {
    
    /**
     * Expected step execution order (strict).
     */
    private List<String> stepOrder;
    
    /**
     * Expected steps to execute (any order).
     */
    private List<String> expectedSteps;
    
    /**
     * Steps that should NOT execute.
     */
    private List<String> unexpectedSteps;
    
    /**
     * Expected execution counts per step.
     */
    private Map<String, Integer> executionCounts;
    
    /**
     * Whether workflow should complete successfully.
     */
    private boolean shouldComplete;
    
    /**
     * Whether workflow should fail.
     */
    private boolean shouldFail;
    
    /**
     * Expected failure message pattern (regex).
     */
    private String failurePattern;
    
    /**
     * Maximum allowed execution time in milliseconds.
     */
    private Long maxExecutionTime;
    
    /**
     * Minimum required execution time in milliseconds.
     */
    private Long minExecutionTime;
}