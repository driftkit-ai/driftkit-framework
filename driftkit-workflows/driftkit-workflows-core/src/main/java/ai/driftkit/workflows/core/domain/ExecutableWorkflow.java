package ai.driftkit.workflows.core.domain;

import ai.driftkit.workflows.core.service.ExecutableWorkflowGraph;
import ai.driftkit.workflows.core.service.WorkflowAnalyzer;
import com.fasterxml.jackson.databind.JsonNode;

public class ExecutableWorkflow<I extends StartEvent, O> {

    ExecutableWorkflowGraph graph;

    public ExecutableWorkflow() {
        this.graph = WorkflowAnalyzer.buildExecutableWorkflowGraph(this);
    }

    public Class<I> getInputType() {
        return (Class<I>) StartEvent.class;
    }

    public Class<O> getOutputType() {
        return (Class<O>) JsonNode.class;
    }

    public StopEvent<O> execute(StartEvent startEvent, WorkflowContext workflowContext) throws Exception {
        return graph.execute(this, startEvent, workflowContext);
    }
}
