package ai.driftkit.workflow.engine.core.pipeline;

import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.graph.Edge;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Unified registry for all pipeline definitions — both WorkflowEngine workflows and agent compositions.
 * Provides introspection for pipeline visualization, testing, and observability.
 */
@Slf4j
public class PipelineRegistry {

    private static final PipelineRegistry INSTANCE = new PipelineRegistry();

    private final Map<String, PipelineDefinition> pipelines = new ConcurrentHashMap<>();

    public static PipelineRegistry getInstance() {
        return INSTANCE;
    }

    public void register(PipelineDefinition definition) {
        pipelines.put(definition.getId(), definition);
        log.info("Pipeline registered: {} ({})", definition.getId(), definition.getType());
    }

    public Optional<PipelineDefinition> get(String id) {
        return Optional.ofNullable(pipelines.get(id));
    }

    public List<PipelineDefinition> list() {
        return new ArrayList<>(pipelines.values());
    }

    public void remove(String id) {
        pipelines.remove(id);
    }

    /**
     * Auto-register a pipeline from a WorkflowGraph (from @Workflow annotation).
     */
    public void registerFromWorkflowGraph(WorkflowGraph<?, ?> graph) {
        List<String> orderedSteps = graph.topologicalSort();

        List<PipelineStep> steps = new ArrayList<>();
        for (int i = 0; i < orderedSteps.size(); i++) {
            String stepId = orderedSteps.get(i);
            Optional<StepNode> nodeOpt = graph.getNode(stepId);
            if (nodeOpt.isEmpty()) continue;

            StepNode node = nodeOpt.get();
            PipelineStep.StepType type = node.isAsync()
                    ? PipelineStep.StepType.ASYNC
                    : PipelineStep.StepType.LLM_CALL;

            // Check if step has branching edges
            List<Edge> outgoing = graph.getOutgoingEdges(stepId);
            if (outgoing != null && outgoing.size() > 1) {
                type = PipelineStep.StepType.BRANCH;
            }

            steps.add(PipelineStep.builder()
                    .stepId(stepId)
                    .order(i)
                    .type(type)
                    .inputType(node.executor() != null && node.executor().getInputType() != null
                            ? node.executor().getInputType().getSimpleName() : null)
                    .outputType(node.executor() != null && node.executor().getOutputType() != null
                            ? node.executor().getOutputType().getSimpleName() : null)
                    .build());
        }

        PipelineDefinition definition = PipelineDefinition.builder()
                .id(graph.id())
                .name(graph.id())
                .type(PipelineDefinition.PipelineType.WORKFLOW)
                .version(graph.version())
                .steps(steps)
                .build();

        register(definition);
    }
}
