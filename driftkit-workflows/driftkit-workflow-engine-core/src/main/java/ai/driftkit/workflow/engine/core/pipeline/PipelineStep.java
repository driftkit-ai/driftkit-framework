package ai.driftkit.workflow.engine.core.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * A single step within a pipeline definition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineStep {

    private String stepId;
    private String promptMethod;
    private int order;
    private StepType type;
    private String agentName;
    private String inputType;
    private String outputType;
    private Map<String, Object> config;

    public enum StepType {
        LLM_CALL,
        TOOL_CALL,
        BRANCH,
        MERGE,
        HUMAN_INPUT,
        LOOP_WORKER,
        LOOP_EVALUATOR,
        ASYNC,
        UNKNOWN
    }
}
