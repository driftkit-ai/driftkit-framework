package ai.driftkit.workflow.engine.core.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a registered pipeline — either from WorkflowEngine (@Workflow) or from agent composition
 * (SequentialAgent, LoopAgent). Used for introspection, visualization, and pipeline testing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineDefinition {

    private String id;
    private String name;
    private String description;
    private PipelineType type;
    private String version;
    private List<PipelineStep> steps;
    private Map<String, Object> config;

    public enum PipelineType {
        WORKFLOW,
        SEQUENTIAL_AGENT,
        LOOP_AGENT,
        CUSTOM
    }
}
