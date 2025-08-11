package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.graph.Edge;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Fluent API builder for creating workflow graphs.
 * Inspired by mastra.ai's workflow definition approach.
 */
@Slf4j
public class WorkflowBuilder<T, R> {
    
    private final String id;
    private final Class<T> inputType;
    private final Class<R> outputType;
    private final List<BuildStep> buildSteps = new ArrayList<>();
    private String version = "1.0";
    private String description;
    
    private WorkflowBuilder(String id, Class<T> inputType, Class<R> outputType) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("WorkflowBuilder ID cannot be null or empty");
        }
        if (inputType == null) {
            throw new IllegalArgumentException("Input type cannot be null");
        }
        if (outputType == null) {
            throw new IllegalArgumentException("Output type cannot be null");
        }
        
        this.id = id;
        this.inputType = inputType;
        this.outputType = outputType;
    }
    
    /**
     * Starts defining a new workflow.
     * 
     * @param id Unique workflow identifier
     * @param inputType Type of input data for the workflow
     * @param outputType Type of the final result
     */
    public static <T, R> WorkflowBuilder<T, R> define(String id, Class<T> inputType, Class<R> outputType) {
        return new WorkflowBuilder<>(id, inputType, outputType);
    }
    
    /**
     * Sets the version of this workflow.
     */
    public WorkflowBuilder<T, R> withVersion(String version) {
        this.version = version;
        return this;
    }
    
    /**
     * Sets the description of this workflow.
     */
    public WorkflowBuilder<T, R> withDescription(String description) {
        this.description = description;
        return this;
    }
    
    /**
     * Adds a sequential step to the workflow.
     * 
     * @param stepDef Step definition created via StepDefinition.of()
     */
    public WorkflowBuilder<T, R> then(StepDefinition stepDef) {
        buildSteps.add(new SequentialStep(stepDef));
        return this;
    }
    
    /**
     * Adds parallel steps to the workflow.
     * All steps in the list will be executed concurrently.
     * 
     * @param parallelSteps List of step definitions to execute in parallel
     */
    public WorkflowBuilder<T, R> parallel(List<StepDefinition> parallelSteps) {
        if (parallelSteps == null || parallelSteps.isEmpty()) {
            throw new IllegalArgumentException("Parallel steps list cannot be null or empty");
        }
        buildSteps.add(new ParallelStep(parallelSteps));
        return this;
    }
    
    /**
     * Adds a conditional branch to the workflow.
     * 
     * @param condition Predicate to evaluate on the workflow context
     * @param ifTrue WorkflowBuilder to execute if condition is true
     * @param ifFalse WorkflowBuilder to execute if condition is false
     */
    public WorkflowBuilder<T, R> branch(Predicate<WorkflowContext> condition, 
                                WorkflowBuilder<?, ?> ifTrue, 
                                WorkflowBuilder<?, ?> ifFalse) {
        buildSteps.add(new BranchStep(condition, ifTrue, ifFalse));
        return this;
    }
    
    /**
     * Adds a conditional branch with single step definitions.
     * 
     * @param condition Predicate to evaluate on the workflow context
     * @param ifTrue Step to execute if condition is true
     * @param ifFalse Step to execute if condition is false
     */
    public WorkflowBuilder<T, R> branch(Predicate<WorkflowContext> condition,
                                StepDefinition ifTrue,
                                StepDefinition ifFalse) {
        buildSteps.add(new BranchStep(
            condition,
            WorkflowBuilder.define("branch-true", Object.class, Object.class).then(ifTrue),
            WorkflowBuilder.define("branch-false", Object.class, Object.class).then(ifFalse)
        ));
        return this;
    }
    
    /**
     * Builds the workflow graph.
     * This validates the workflow structure and creates an immutable WorkflowBuilderGraph.
     */
    public WorkflowGraph<T, R> build() {
        if (buildSteps.isEmpty()) {
            throw new IllegalStateException("WorkflowBuilder must have at least one step");
        }
        
        Map<String, StepNode> nodes = new HashMap<>();
        Map<String, List<Edge>> edges = new HashMap<>();
        String initialStepId = null;
        
        // Build the graph
        GraphBuildContext context = new GraphBuildContext();
        String lastStepId = null;
        
        for (int i = 0; i < buildSteps.size(); i++) {
            BuildStep buildStep = buildSteps.get(i);
            BuildStepResult result = buildStep.build(context, lastStepId);
            
            // Add nodes
            result.nodes.forEach(node -> {
                if (nodes.containsKey(node.id())) {
                    throw new IllegalStateException("Duplicate step ID: " + node.id());
                }
                nodes.put(node.id(), node);
            });
            
            // Add edges
            result.edges.forEach((from, edgeList) -> {
                edges.computeIfAbsent(from, k -> new ArrayList<>()).addAll(edgeList);
            });
            
            // Set initial step
            if (i == 0 && !result.entryPoints.isEmpty()) {
                initialStepId = result.entryPoints.get(0);
            }
            
            // Connect to previous step
            if (lastStepId != null && !result.entryPoints.isEmpty()) {
                for (String entryPoint : result.entryPoints) {
                    edges.computeIfAbsent(lastStepId, k -> new ArrayList<>())
                        .add(Edge.sequential(lastStepId, entryPoint));
                }
            }
            
            lastStepId = result.exitPoints.isEmpty() ? null : result.exitPoints.get(0);
        }
        
        // Validate the graph
        validateGraph(nodes, edges, initialStepId);
        
        log.info("Built workflow graph: {} with {} nodes and {} edges", 
            id, nodes.size(), edges.values().stream().mapToInt(List::size).sum());
        
        return WorkflowGraph.<T, R>builder()
            .id(id)
            .version(version)
            .inputType(inputType)
            .outputType(outputType)
            .nodes(nodes)
            .edges(edges)
            .initialStepId(initialStepId)
            .build();
    }
    
    /**
     * Builds this workflow as a sub-workflow that can be embedded in another workflow.
     * Used internally for branch construction.
     */
    WorkflowBuilder<T, R> buildAsSubWorkflowBuilder() {
        // This is a marker method for the documentation example
        // In practice, branches are handled differently
        return this;
    }
    
    /**
     * Validates the constructed graph.
     */
    private void validateGraph(Map<String, StepNode> nodes, 
                              Map<String, List<Edge>> edges,
                              String initialStepId) {
        if (initialStepId == null) {
            throw new IllegalStateException("No initial step defined");
        }
        
        if (!nodes.containsKey(initialStepId)) {
            throw new IllegalStateException("Initial step not found: " + initialStepId);
        }
        
        // Check for orphaned nodes (except initial)
        Set<String> reachable = new HashSet<>();
        Queue<String> toVisit = new LinkedList<>();
        toVisit.offer(initialStepId);
        
        while (!toVisit.isEmpty()) {
            String current = toVisit.poll();
            if (reachable.contains(current)) {
                continue;
            }
            reachable.add(current);
            
            List<Edge> outgoing = edges.getOrDefault(current, Collections.emptyList());
            for (Edge edge : outgoing) {
                toVisit.offer(edge.toStepId());
            }
        }
        
        Set<String> unreachable = new HashSet<>(nodes.keySet());
        unreachable.removeAll(reachable);
        
        if (!unreachable.isEmpty()) {
            log.warn("Unreachable nodes in workflow {}: {}", id, unreachable);
        }
    }
    
    /**
     * Base interface for build steps.
     */
    private interface BuildStep {
        BuildStepResult build(GraphBuildContext context, String previousStepId);
    }
    
    /**
     * Sequential step implementation.
     */
    private record SequentialStep(StepDefinition stepDef) implements BuildStep {
        @Override
        public BuildStepResult build(GraphBuildContext context, String previousStepId) {
            StepNode node = createStepNode(stepDef, context);
            
            BuildStepResult result = new BuildStepResult();
            result.nodes.add(node);
            result.entryPoints.add(node.id());
            result.exitPoints.add(node.id());
            
            return result;
        }
    }
    
    /**
     * Parallel step implementation.
     */
    private record ParallelStep(List<StepDefinition> parallelSteps) implements BuildStep {
        @Override
        public BuildStepResult build(GraphBuildContext context, String previousStepId) {
            BuildStepResult result = new BuildStepResult();
            
            // Create a sync point after parallel execution
            String syncPointId = "sync_" + context.nextId();
            StepNode syncNode = StepNode.fromFunction(
                syncPointId,
                (Object input) -> new ai.driftkit.workflow.engine.core.StepResult.Continue<>(input)
            ).withDescription("Synchronization point");
            
            // Add all parallel steps
            for (StepDefinition stepDef : parallelSteps) {
                StepNode node = createStepNode(stepDef, context);
                result.nodes.add(node);
                result.entryPoints.add(node.id());
                
                // Connect each parallel step to the sync point
                result.edges.computeIfAbsent(node.id(), k -> new ArrayList<>())
                    .add(Edge.sequential(node.id(), syncPointId));
            }
            
            // Add sync node
            result.nodes.add(syncNode);
            result.exitPoints.add(syncPointId);
            
            return result;
        }
    }
    
    /**
     * Branch step implementation.
     */
    private record BranchStep(Predicate<WorkflowContext> condition,
                             WorkflowBuilder<?, ?> ifTrue,
                             WorkflowBuilder<?, ?> ifFalse) implements BuildStep {
        @Override
        public BuildStepResult build(GraphBuildContext context, String previousStepId) {
            BuildStepResult result = new BuildStepResult();
            
            // Create a decision node
            String decisionId = "decision_" + context.nextId();
            StepNode decisionNode = StepNode.fromBiFunction(
                decisionId,
                (Object input, WorkflowContext ctx) -> {
                    boolean conditionResult = condition.test(ctx);
                    return new ai.driftkit.workflow.engine.core.StepResult.Branch<>(
                        conditionResult ? new BranchTrue() : new BranchFalse()
                    );
                }
            ).withDescription("Branch decision");
            
            result.nodes.add(decisionNode);
            result.entryPoints.add(decisionId);
            
            // Build true branch
            String trueBranchPrefix = "true_" + context.nextId() + "_";
            GraphBuildContext trueBranchContext = context.withPrefix(trueBranchPrefix);
            BuildStepResult trueBranchResult = buildSubWorkflowBuilder(ifTrue, trueBranchContext);
            
            // Build false branch
            String falseBranchPrefix = "false_" + context.nextId() + "_";
            GraphBuildContext falseBranchContext = context.withPrefix(falseBranchPrefix);
            BuildStepResult falseBranchResult = buildSubWorkflowBuilder(ifFalse, falseBranchContext);
            
            // Add branch nodes and edges
            result.nodes.addAll(trueBranchResult.nodes);
            result.nodes.addAll(falseBranchResult.nodes);
            result.edges.putAll(trueBranchResult.edges);
            result.edges.putAll(falseBranchResult.edges);
            
            // Connect decision to branches
            if (!trueBranchResult.entryPoints.isEmpty()) {
                result.edges.computeIfAbsent(decisionId, k -> new ArrayList<>())
                    .add(Edge.branch(decisionId, trueBranchResult.entryPoints.get(0), BranchTrue.class));
            }
            
            if (!falseBranchResult.entryPoints.isEmpty()) {
                result.edges.computeIfAbsent(decisionId, k -> new ArrayList<>())
                    .add(Edge.branch(decisionId, falseBranchResult.entryPoints.get(0), BranchFalse.class));
            }
            
            // Merge exit points
            result.exitPoints.addAll(trueBranchResult.exitPoints);
            result.exitPoints.addAll(falseBranchResult.exitPoints);
            
            return result;
        }
        
        private BuildStepResult buildSubWorkflowBuilder(WorkflowBuilder<?, ?> workflow, GraphBuildContext context) {
            BuildStepResult result = new BuildStepResult();
            String lastStepId = null;
            
            for (BuildStep step : workflow.buildSteps) {
                BuildStepResult stepResult = step.build(context, lastStepId);
                
                result.nodes.addAll(stepResult.nodes);
                stepResult.edges.forEach((from, edges) -> 
                    result.edges.computeIfAbsent(from, k -> new ArrayList<>()).addAll(edges)
                );
                
                if (lastStepId != null && !stepResult.entryPoints.isEmpty()) {
                    for (String entryPoint : stepResult.entryPoints) {
                        result.edges.computeIfAbsent(lastStepId, k -> new ArrayList<>())
                            .add(Edge.sequential(lastStepId, entryPoint));
                    }
                }
                
                if (result.entryPoints.isEmpty()) {
                    result.entryPoints.addAll(stepResult.entryPoints);
                }
                
                lastStepId = stepResult.exitPoints.isEmpty() ? null : stepResult.exitPoints.get(0);
            }
            
            if (lastStepId != null) {
                result.exitPoints.add(lastStepId);
            }
            
            return result;
        }
    }
    
    /**
     * Helper to create StepNode from StepDefinition.
     */
    private static StepNode createStepNode(StepDefinition stepDef, GraphBuildContext context) {
        String nodeId = context.prefix + stepDef.getId();
        
        return new StepNode(
            nodeId,
            stepDef.getDescription(),
            new StepNode.StepExecutor() {
                @Override
                public Object execute(Object input, WorkflowContext context) throws Exception {
                    return stepDef.getExecutor().execute(input, context);
                }
                
                @Override
                public Class<?> getInputType() {
                    return stepDef.getInputType();
                }
                
                @Override
                public Class<?> getOutputType() {
                    return stepDef.getOutputType();
                }
                
                @Override
                public boolean requiresContext() {
                    return true; // Conservative default
                }
            },
            false,
            context.isFirst()
        );
    }
    
    /**
     * Context for building the graph.
     */
    private static class GraphBuildContext {
        private final AtomicInteger idCounter = new AtomicInteger();
        private String prefix = "";
        private boolean first = true;
        
        int nextId() {
            return idCounter.incrementAndGet();
        }
        
        boolean isFirst() {
            if (first) {
                first = false;
                return true;
            }
            return false;
        }
        
        GraphBuildContext withPrefix(String prefix) {
            GraphBuildContext newContext = new GraphBuildContext();
            newContext.prefix = prefix;
            newContext.idCounter.set(this.idCounter.get());
            return newContext;
        }
    }
    
    /**
     * Result of building a step or group of steps.
     */
    private static class BuildStepResult {
        final List<StepNode> nodes = new ArrayList<>();
        final Map<String, List<Edge>> edges = new HashMap<>();
        final List<String> entryPoints = new ArrayList<>();
        final List<String> exitPoints = new ArrayList<>();
    }
    
    /**
     * Marker types for branch decisions.
     */
    private record BranchTrue() {}
    private record BranchFalse() {}
}