package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.AsyncProgressReporter;
import ai.driftkit.workflow.engine.core.WorkflowAnalyzer;
import ai.driftkit.workflow.engine.core.WorkflowAnalyzer.AsyncStepMetadata;
import ai.driftkit.workflow.engine.annotations.AsyncStep;
import ai.driftkit.workflow.engine.graph.Edge;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.BiFunction;

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
    private StepDefinition lastStepDefinition = null;
    private final Set<Object> asyncHandlers = new HashSet<>(); // Collect all async handlers
    private final Map<String, AsyncHandlerInfo> registeredAsyncHandlers = new HashMap<>();
    
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
     * Registers an async handler for processing async tasks.
     * The handler will be called when a step returns StepResult.Async with matching taskId pattern.
     * 
     * @param taskIdPattern Pattern to match task IDs (supports wildcards like "*")
     * @param asyncHandler Trifunction that processes async tasks
     * @return this builder for chaining
     */
    public WorkflowBuilder<T, R> withAsyncHandler(String taskIdPattern, 
                                                  TriFunction<Map<String, Object>, WorkflowContext, AsyncProgressReporter, StepResult<?>> asyncHandler) {
        if (taskIdPattern == null || taskIdPattern.isBlank()) {
            throw new IllegalArgumentException("Task ID pattern cannot be null or empty");
        }
        if (asyncHandler == null) {
            throw new IllegalArgumentException("Async handler cannot be null");
        }
        
        // Extract method info if possible
        String methodName = extractTriFunctionMethodName(asyncHandler);
        
        AsyncHandlerInfo handlerInfo = new AsyncHandlerInfo(taskIdPattern, asyncHandler, methodName);
        registeredAsyncHandlers.put(taskIdPattern, handlerInfo);
        
        log.debug("Registered async handler for pattern '{}' (method: {})", taskIdPattern, methodName);
        return this;
    }
    
    /**
     * Registers an object containing @AsyncStep annotated methods.
     * This scans the object for methods with @AsyncStep and registers them automatically.
     * 
     * @param asyncHandlerObject Object containing @AsyncStep methods
     * @return this builder for chaining
     */
    public WorkflowBuilder<T, R> withAsyncHandler(Object asyncHandlerObject) {
        if (asyncHandlerObject == null) {
            throw new IllegalArgumentException("Async handler object cannot be null");
        }
        
        // Find all @AsyncStep methods in the object
        Map<String, AsyncStepMetadata> asyncSteps = WorkflowAnalyzer.findAsyncSteps(asyncHandlerObject);
        
        log.debug("Found {} @AsyncStep methods in {}", asyncSteps.size(), asyncHandlerObject.getClass().getSimpleName());
        
        // Register each async step
        for (Map.Entry<String, AsyncStepMetadata> entry : asyncSteps.entrySet()) {
            String pattern = entry.getKey();
            AsyncStepMetadata metadata = entry.getValue();
            
            // Create a wrapper that calls the method via reflection
            TriFunction<Map<String, Object>, WorkflowContext, AsyncProgressReporter, StepResult<?>> wrapper = 
                (taskArgs, context, progress) -> {
                    try {
                        Method method = metadata.getMethod();
                        return (StepResult<?>) method.invoke(asyncHandlerObject, taskArgs, context, progress);
                    } catch (Exception e) {
                        log.error("Error invoking async handler method", e);
                        return StepResult.fail(e);
                    }
                };
            
            AsyncHandlerInfo handlerInfo = new AsyncHandlerInfo(pattern, wrapper, metadata.getMethod().getName());
            handlerInfo.setFromAnnotation(true);
            handlerInfo.setAnnotation(metadata.getAnnotation());
            registeredAsyncHandlers.put(pattern, handlerInfo);
        }
        
        log.debug("Registered {} async handlers from object {}", asyncSteps.size(), asyncHandlerObject.getClass().getSimpleName());
        asyncHandlers.add(asyncHandlerObject);
        return this;
    }
    
    /**
     * Adds a sequential step to the workflow.
     * 
     * @param stepDef Step definition created via StepDefinition.of()
     */
    public WorkflowBuilder<T, R> then(StepDefinition stepDef) {
        buildSteps.add(new SequentialStep(stepDef));
        lastStepDefinition = stepDef; // Track for type flow
        return this;
    }
    
    /**
     * Adds a sequential step to the workflow using a function.
     * Automatically extracts the method name if it's a method reference.
     * 
     * @param step Function that returns StepResult
     */
    public <I, O> WorkflowBuilder<T, R> then(Function<I, StepResult<O>> step) {
        // Extract step ID from method reference or generate one
        String stepId = extractLambdaMethodName(step);
        
        // Create step definition
        StepDefinition stepDef = StepDefinition.of(stepId, step);
        buildSteps.add(new SequentialStep(stepDef));
        lastStepDefinition = stepDef; // Track for type flow
        
        return this;
    }
    
    /**
     * Adds a sequential step to the workflow using a BiFunction with context.
     * Automatically extracts the method name if it's a method reference.
     * 
     * @param step BiFunction that takes input and context and returns StepResult
     */
    public <I, O> WorkflowBuilder<T, R> then(BiFunction<I, WorkflowContext, StepResult<O>> step) {
        // Extract step ID from method reference or generate one
        String stepId = extractLambdaMethodName(step);
        
        // Create step definition
        StepDefinition stepDef = StepDefinition.of(stepId, step);
        buildSteps.add(new SequentialStep(stepDef));
        lastStepDefinition = stepDef; // Track for type flow
        
        return this;
    }
    
    /**
     * Adds a sequential step with explicit ID using a lambda.
     * Use this when you need to specify a custom ID for a lambda expression.
     * 
     * @param id Explicit step ID
     * @param step Function that returns StepResult
     */
    public <I, O> WorkflowBuilder<T, R> then(String id, Function<I, StepResult<O>> step, Class<I> inputType, Class<O> outputType) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Step ID cannot be null or empty");
        }
        if (inputType == null || outputType == null) {
            throw new IllegalArgumentException("Input and output types must be specified for lambda expressions");
        }
        StepDefinition stepDef = StepDefinition.of(id, step).withTypes(inputType, outputType);
        buildSteps.add(new SequentialStep(stepDef));
        lastStepDefinition = stepDef; // Track for type flow
        return this;
    }
    
    /**
     * Adds a sequential step with explicit ID using a BiFunction.
     * 
     * @param id Explicit step ID
     * @param step BiFunction that takes input and context and returns StepResult
     */
    public <I, O> WorkflowBuilder<T, R> then(String id, BiFunction<I, WorkflowContext, StepResult<O>> step) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Step ID cannot be null or empty");
        }
        StepDefinition stepDef = StepDefinition.of(id, step);
        buildSteps.add(new SequentialStep(stepDef));
        return this;
    }
    
    /**
     * Adds a step that returns a plain value (not StepResult).
     * The value will be automatically wrapped in StepResult.continueWith().
     * Method name is automatically extracted from method reference.
     * 
     * @param step Function that returns a plain object
     */
    public <I, O> WorkflowBuilder<T, R> thenValue(Function<I, O> step) {
        String stepId = extractLambdaMethodName(step);
        
        // Wrap the function to return StepResult
        Function<I, StepResult<O>> wrappedStep = input -> {
            O result = step.apply(input);
            return StepResult.continueWith(result);
        };
        
        StepDefinition stepDef = StepDefinition.of(stepId, wrappedStep);
        buildSteps.add(new SequentialStep(stepDef));
        lastStepDefinition = stepDef;
        
        return this;
    }
    
    /**
     * Adds a step that returns a plain value with context access.
     * The value will be automatically wrapped in StepResult.continueWith().
     * 
     * @param step BiFunction that returns a plain object
     */
    public <I, O> WorkflowBuilder<T, R> thenValue(BiFunction<I, WorkflowContext, O> step) {
        String stepId = extractLambdaMethodName(step);
        
        // Wrap the function to return StepResult
        BiFunction<I, WorkflowContext, StepResult<O>> wrappedStep = (input, ctx) -> {
            O result = step.apply(input, ctx);
            return StepResult.continueWith(result);
        };
        
        StepDefinition stepDef = StepDefinition.of(stepId, wrappedStep);
        buildSteps.add(new SequentialStep(stepDef));
        lastStepDefinition = stepDef;
        
        return this;
    }
    
    /**
     * Adds a step with explicit ID that returns a plain value.
     * 
     * @param id Explicit step ID
     * @param step Function that returns a plain object
     */
    public <I, O> WorkflowBuilder<T, R> thenValue(String id, Function<I, O> step, Class<I> inputType, Class<O> outputType) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Step ID cannot be null or empty");
        }
        if (inputType == null || outputType == null) {
            throw new IllegalArgumentException("Input and output types must be specified");
        }
        
        // Wrap the function to return StepResult
        Function<I, StepResult<O>> wrappedStep = input -> {
            O result = step.apply(input);
            return StepResult.continueWith(result);
        };
        
        StepDefinition stepDef = StepDefinition.of(id, wrappedStep).withTypes(inputType, outputType);
        buildSteps.add(new SequentialStep(stepDef));
        lastStepDefinition = stepDef;
        return this;
    }
    
    /**
     * Adds a final step that returns a plain value (automatically wrapped in StepResult.finish).
     */
    public <I, O> WorkflowBuilder<T, R> finishWithValue(Function<I, O> step) {
        String stepId = extractLambdaMethodName(step);
        
        // Wrap the function to return StepResult.finish
        Function<I, StepResult<O>> wrappedStep = input -> {
            O result = step.apply(input);
            return StepResult.finish(result);
        };
        
        StepDefinition stepDef = StepDefinition.of(stepId, wrappedStep);
        buildSteps.add(new SequentialStep(stepDef));
        lastStepDefinition = stepDef;
        
        return this;
    }
    
    /**
     * Adds a final step with context that returns a plain value (automatically wrapped in StepResult.finish).
     */
    public <I, O> WorkflowBuilder<T, R> finishWithValue(BiFunction<I, WorkflowContext, O> step) {
        String stepId = extractLambdaMethodName(step);
        
        // Wrap the function to return StepResult.finish
        BiFunction<I, WorkflowContext, StepResult<O>> wrappedStep = (input, ctx) -> {
            O result = step.apply(input, ctx);
            return StepResult.finish(result);
        };
        
        StepDefinition stepDef = StepDefinition.of(stepId, wrappedStep);
        buildSteps.add(new SequentialStep(stepDef));
        lastStepDefinition = stepDef;
        
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
     * Adds parallel steps to the workflow using varargs of functions.
     * All steps will be executed concurrently.
     * 
     * @param steps Variable number of functions to execute in parallel
     */
    @SafeVarargs
    public final <I, O> WorkflowBuilder<T, R> parallel(Function<I, StepResult<O>>... steps) {
        if (steps == null || steps.length == 0) {
            throw new IllegalArgumentException("Parallel steps cannot be null or empty");
        }
        
        List<StepDefinition> stepDefs = new ArrayList<>();
        for (Function<I, StepResult<O>> step : steps) {
            String stepId = extractLambdaMethodName(step);
            stepDefs.add(StepDefinition.of(stepId, step));
        }
        
        buildSteps.add(new ParallelStep(stepDefs));
        return this;
    }
    
    /**
     * Adds parallel steps to the workflow using varargs of BiFunctions.
     * All steps will be executed concurrently.
     * 
     * @param steps Variable number of BiFunctions to execute in parallel
     */
    @SafeVarargs
    public final <I, O> WorkflowBuilder<T, R> parallel(BiFunction<I, WorkflowContext, StepResult<O>>... steps) {
        if (steps == null || steps.length == 0) {
            throw new IllegalArgumentException("Parallel steps cannot be null or empty");
        }
        
        List<StepDefinition> stepDefs = new ArrayList<>();
        for (BiFunction<I, WorkflowContext, StepResult<O>> step : steps) {
            String stepId = extractLambdaMethodName(step);
            stepDefs.add(StepDefinition.of(stepId, step));
        }
        
        buildSteps.add(new ParallelStep(stepDefs));
        return this;
    }
    
    // Note: Legacy branch methods that use Object types have been removed.
    // Use the typed branch method below that properly tracks input types.
    
    /**
     * Creates a branch with automatic type inference from the previous step.
     * The branch steps will receive the output type of the previous step as their input.
     */
    /**
     * Creates a branch with automatic type inference from the previous step.
     * The branch steps will receive the output type of the previous step as their input.
     */
    public WorkflowBuilder<T, R> branch(Predicate<WorkflowContext> condition,
                                Consumer<WorkflowBuilder<?, ?>> ifTrue,
                                Consumer<WorkflowBuilder<?, ?>> ifFalse) {
        if (condition == null) {
            throw new IllegalArgumentException("Branch condition cannot be null");
        }
        if (ifTrue == null) {
            throw new IllegalArgumentException("True branch cannot be null");
        }
        if (ifFalse == null) {
            throw new IllegalArgumentException("False branch cannot be null");
        }
        
        // Determine the input type for branches from the last step's output
        Class<?> branchInputType = lastStepDefinition != null ? 
            lastStepDefinition.getOutputType() : inputType;
            
        if (branchInputType == null) {
            throw new IllegalStateException(
                "Cannot determine input type for branch. Previous step must have explicit output type. " +
                "Use method references or provide explicit types for lambda steps."
            );
        }
        
        // Create branches with the proper input type
        // The output type will be determined by the last step in each branch
        WorkflowBuilder<?, ?> trueBranch = new WorkflowBuilder<>("branch-true", branchInputType, branchInputType);
        ifTrue.accept(trueBranch);
        
        WorkflowBuilder<?, ?> falseBranch = new WorkflowBuilder<>("branch-false", branchInputType, branchInputType);
        ifFalse.accept(falseBranch);
        
        buildSteps.add(new TypedBranchStep(condition, trueBranch, falseBranch, branchInputType));
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
        List<String> lastExitPoints = null;
        
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
            
            // Connect to previous step(s)
            if (lastStepId != null && !result.entryPoints.isEmpty()) {
                // Single previous step to multiple entry points
                for (String entryPoint : result.entryPoints) {
                    edges.computeIfAbsent(lastStepId, k -> new ArrayList<>())
                        .add(Edge.sequential(lastStepId, entryPoint));
                }
            } else if (lastExitPoints != null && !lastExitPoints.isEmpty() && !result.entryPoints.isEmpty()) {
                // Multiple previous exit points (from branch) to entry points
                for (String exitPoint : lastExitPoints) {
                    for (String entryPoint : result.entryPoints) {
                        edges.computeIfAbsent(exitPoint, k -> new ArrayList<>())
                            .add(Edge.sequential(exitPoint, entryPoint));
                    }
                }
            }
            
            // Update last step tracking
            if (!result.exitPoints.isEmpty()) {
                if (result.exitPoints.size() == 1) {
                    lastStepId = result.exitPoints.get(0);
                    lastExitPoints = null;
                } else {
                    // Multiple exit points (e.g., from branches)
                    lastStepId = null;
                    lastExitPoints = new ArrayList<>(result.exitPoints);
                }
            } else {
                lastStepId = null;
                lastExitPoints = null;
            }
        }
        
        // Validate the graph
        validateGraph(nodes, edges, initialStepId);
        
        int totalEdges = edges.values().stream().mapToInt(List::size).sum();
        log.info("Built workflow graph: {} with {} nodes and {} edges", 
            id, nodes.size(), totalEdges);
        
        // Debug: print graph structure
        if (log.isDebugEnabled()) {
            log.debug("Graph structure for {}:", id);
            nodes.forEach((nodeId, node) -> {
                log.debug("  Node: {} ({})", nodeId, node.description());
                List<Edge> outgoing = edges.getOrDefault(nodeId, List.of());
                outgoing.forEach(edge -> {
                    log.debug("    -> {} ({})", edge.toStepId(), edge.type());
                });
            });
        }
        
        // Convert registered async handlers to AsyncStepMetadata format
        // WITHOUT workflowInstance - we'll use the TriFunction directly
        Map<String, AsyncStepMetadata> asyncStepMetadata = new HashMap<>();
        
        for (Map.Entry<String, AsyncHandlerInfo> entry : registeredAsyncHandlers.entrySet()) {
            String pattern = entry.getKey();
            AsyncHandlerInfo info = entry.getValue();
            
            // Create a wrapper method that delegates to the TriFunction
            Method proxyMethod = createProxyMethodForTriFunction();
            
            // Create AsyncStepMetadata with null instance - the handler is in the metadata itself
            AsyncStepMetadata metadata = new FluentApiAsyncStepMetadata(
                proxyMethod,
                info.annotation != null ? info.annotation : createSyntheticAsyncAnnotation(pattern),
                info.handler // Store the actual handler
            );
            
            asyncStepMetadata.put(pattern, metadata);
        }
        
        return WorkflowGraph.<T, R>builder()
            .id(id)
            .version(version)
            .inputType(inputType)
            .outputType(outputType)
            .nodes(nodes)
            .edges(edges)
            .initialStepId(initialStepId)
            .workflowInstance(null) // No instance for FluentAPI
            .asyncStepMetadata(asyncStepMetadata)
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
     * Multi-way branching based on a selector function.
     * 
     * @param selector Function that extracts the value to switch on
     * @return OnBuilder for specifying cases
     */
    public <V> OnBuilder<T, R, V> on(Function<WorkflowContext, V> selector) {
        return new OnBuilder<>(this, selector);
    }
    
    /**
     * Try-catch style error handling for a step.
     * 
     * @param stepDef The step that might throw an error
     * @return TryBuilder for specifying error handlers
     */
    public TryBuilder<T, R> tryStep(StepDefinition stepDef) {
        return new TryBuilder<>(this, stepDef);
    }
    
    
    /**
     * Package-private method to add build steps from other builders.
     */
    void addBuildStep(BuildStep step) {
        buildSteps.add(step);
    }
    
    /**
     * Package-private method to get build steps.
     */
    List<BuildStep> getBuildSteps() {
        return buildSteps;
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
    static interface BuildStep {
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
                (Object input) -> StepResult.continueWith(input)
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
     * Typed branch step implementation that preserves input type information.
     */
    private record TypedBranchStep<I>(Predicate<WorkflowContext> condition,
                                     WorkflowBuilder<I, ?> ifTrue,
                                     WorkflowBuilder<I, ?> ifFalse,
                                     Class<I> inputType) implements BuildStep {
        @Override
        public BuildStepResult build(GraphBuildContext context, String previousStepId) {
            BuildStepResult result = new BuildStepResult();
            
            // Create a decision node with proper type information
            String decisionId = "decision_" + context.nextId();
            StepNode decisionNode = StepNode.fromBiFunction(
                decisionId,
                (Object input, WorkflowContext ctx) -> {
                    boolean conditionResult = condition.test(ctx);
                    return StepResult.branch(
                        conditionResult ? new BranchTrue() : new BranchFalse()
                    );
                },
                inputType,  // Specify the input type
                Object.class  // Output type for branch results
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
            context.isFirst(),
            stepDef.getRetryPolicy(),
            stepDef.getInvocationLimit(),
            stepDef.getOnInvocationsLimit()
        );
    }
    
    /**
     * Context for building the graph.
     */
    static class GraphBuildContext {
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
            newContext.first = false; // Branch contexts should never have initial steps
            return newContext;
        }
    }
    
    /**
     * Result of building a step or group of steps.
     */
    static class BuildStepResult {
        final List<StepNode> nodes = new ArrayList<>();
        final Map<String, List<Edge>> edges = new HashMap<>();
        final List<String> entryPoints = new ArrayList<>();
        final List<String> exitPoints = new ArrayList<>();
    }
    
    /**
     * Multi-branch step implementation for when/is/otherwise pattern.
     */
    static class MultiBranchStep<V> implements BuildStep {
        private final Function<WorkflowContext, V> selector;
        private final Map<V, Consumer<WorkflowBuilder<?, ?>>> cases;
        private final Consumer<WorkflowBuilder<?, ?>> otherwiseCase;
        
        MultiBranchStep(Function<WorkflowContext, V> selector,
                       Map<V, Consumer<WorkflowBuilder<?, ?>>> cases,
                       Consumer<WorkflowBuilder<?, ?>> otherwiseCase) {
            this.selector = selector;
            this.cases = new HashMap<>(cases);
            this.otherwiseCase = otherwiseCase;
        }
        
        @Override
        public BuildStepResult build(GraphBuildContext context, String previousStepId) {
            BuildStepResult result = new BuildStepResult();
            
            // Create decision node
            String decisionId = "multi_branch_" + context.nextId();
            StepNode decisionNode = StepNode.fromBiFunction(
                decisionId,
                (Object input, WorkflowContext ctx) -> {
                    V value = selector.apply(ctx);
                    return StepResult.branch(
                        new BranchValue<>(value)
                    );
                }
            ).withDescription("Multi-way branch decision");
            
            result.nodes.add(decisionNode);
            result.entryPoints.add(decisionId);
            
            // Build branches for each case
            for (Map.Entry<V, Consumer<WorkflowBuilder<?, ?>>> entry : cases.entrySet()) {
                V caseValue = entry.getKey();
                Consumer<WorkflowBuilder<?, ?>> caseFlow = entry.getValue();
                
                String branchPrefix = "case_" + caseValue + "_" + context.nextId() + "_";
                GraphBuildContext branchContext = context.withPrefix(branchPrefix);
                
                WorkflowBuilder<Object, Object> caseBuilder = 
                    WorkflowBuilder.define("case-" + caseValue, Object.class, Object.class);
                caseFlow.accept(caseBuilder);
                
                BuildStepResult caseResult = buildSubWorkflow(caseBuilder, branchContext);
                
                // Add nodes and edges
                result.nodes.addAll(caseResult.nodes);
                result.edges.putAll(caseResult.edges);
                
                // Connect decision to case
                if (!caseResult.entryPoints.isEmpty()) {
                    result.edges.computeIfAbsent(decisionId, k -> new ArrayList<>())
                        .add(Edge.branchWithValue(decisionId, caseResult.entryPoints.get(0), 
                            BranchValue.class, caseValue));
                }
                
                result.exitPoints.addAll(caseResult.exitPoints);
            }
            
            // Build otherwise branch
            if (otherwiseCase != null) {
                String otherwisePrefix = "otherwise_" + context.nextId() + "_";
                GraphBuildContext otherwiseContext = context.withPrefix(otherwisePrefix);
                
                WorkflowBuilder<Object, Object> otherwiseBuilder = 
                    WorkflowBuilder.define("otherwise", Object.class, Object.class);
                otherwiseCase.accept(otherwiseBuilder);
                
                BuildStepResult otherwiseResult = buildSubWorkflow(otherwiseBuilder, otherwiseContext);
                
                // Add nodes and edges
                result.nodes.addAll(otherwiseResult.nodes);
                result.edges.putAll(otherwiseResult.edges);
                
                // Connect decision to otherwise
                if (!otherwiseResult.entryPoints.isEmpty()) {
                    result.edges.computeIfAbsent(decisionId, k -> new ArrayList<>())
                        .add(Edge.branch(decisionId, otherwiseResult.entryPoints.get(0), 
                            BranchOtherwise.class));
                }
                
                result.exitPoints.addAll(otherwiseResult.exitPoints);
            }
            
            return result;
        }
        
        private BuildStepResult buildSubWorkflow(WorkflowBuilder<?, ?> workflow, GraphBuildContext context) {
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
     * Try-catch step implementation.
     */
    static class TryCatchStep implements BuildStep {
        private final StepDefinition tryStep;
        private final Map<Class<? extends Throwable>, TryBuilder.ErrorHandler> errorHandlers;
        private final Runnable finallyBlock;
        
        TryCatchStep(StepDefinition tryStep,
                    Map<Class<? extends Throwable>, TryBuilder.ErrorHandler> errorHandlers,
                    Runnable finallyBlock) {
            this.tryStep = tryStep;
            this.errorHandlers = new HashMap<>(errorHandlers);
            this.finallyBlock = finallyBlock;
        }
        
        @Override
        public BuildStepResult build(GraphBuildContext context, String previousStepId) {
            BuildStepResult result = new BuildStepResult();
            
            // Create try step with error handling wrapper
            String tryStepId = context.prefix + tryStep.getId();
            StepNode tryNode = new StepNode(
                tryStepId,
                tryStep.getDescription() + " (with error handling)",
                new StepNode.StepExecutor() {
                    @Override
                    public Object execute(Object input, WorkflowContext ctx) throws Exception {
                        try {
                            Object result = tryStep.getExecutor().execute(input, ctx);
                            
                            // Execute finally block if present
                            if (finallyBlock != null) {
                                finallyBlock.run();
                            }
                            
                            return result;
                        } catch (Throwable t) {
                            // Find matching error handler
                            for (Map.Entry<Class<? extends Throwable>, TryBuilder.ErrorHandler> entry : 
                                 errorHandlers.entrySet()) {
                                if (entry.getKey().isAssignableFrom(t.getClass())) {
                                    StepResult<?> handled = 
                                        entry.getValue().handle(t, ctx);
                                    
                                    // Execute finally block
                                    if (finallyBlock != null) {
                                        finallyBlock.run();
                                    }
                                    
                                    if (handled instanceof StepResult.Continue) {
                                        return ((StepResult.Continue<?>) handled).data();
                                    } else if (handled instanceof StepResult.Finish) {
                                        return ((StepResult.Finish<?>) handled).result();
                                    } else if (handled instanceof StepResult.Fail) {
                                        throw new RuntimeException("Error handler failed", 
                                            ((StepResult.Fail<?>) handled).error());
                                    }
                                }
                            }
                            
                            // No handler found, execute finally and rethrow
                            if (finallyBlock != null) {
                                finallyBlock.run();
                            }
                            throw t;
                        }
                    }
                    
                    @Override
                    public Class<?> getInputType() {
                        return tryStep.getInputType();
                    }
                    
                    @Override
                    public Class<?> getOutputType() {
                        return tryStep.getOutputType();
                    }
                    
                    @Override
                    public boolean requiresContext() {
                        return true;
                    }
                },
                false,
                context.isFirst(),
                tryStep.getRetryPolicy(),
                tryStep.getInvocationLimit(),
                tryStep.getOnInvocationsLimit()
            );
            
            result.nodes.add(tryNode);
            result.entryPoints.add(tryStepId);
            result.exitPoints.add(tryStepId);
            
            return result;
        }
    }
    
    /**
     * Extract method name from a TriFunction lambda or method reference.
     * 
     * @param triFunction The trifunction lambda or method reference
     * @return The extracted method name or "asyncHandler"
     */
    private String extractTriFunctionMethodName(TriFunction<?, ?, ?, ?> triFunction) {
        return extractLambdaMethodName(triFunction);
    }
    
    /**
     * Extract method name from a lambda or method reference.
     * For method references, tries to extract the actual method name.
     * For anonymous lambdas, generates a unique ID.
     * 
     * @param lambda The lambda or method reference
     * @return The extracted method name or generated ID
     */
    private String extractLambdaMethodName(Object lambda) {
        if (lambda == null) {
            throw new IllegalArgumentException("Lambda cannot be null");
        }
        
        // Try SerializedLambda approach first (works when lambda is Serializable)
        if (lambda instanceof Serializable) {
            try {
                Method writeReplace = lambda.getClass().getDeclaredMethod("writeReplace");
                writeReplace.setAccessible(true);
                SerializedLambda serializedLambda = (SerializedLambda) writeReplace.invoke(lambda);
                
                String implMethodName = serializedLambda.getImplMethodName();
                
                // Check if it's a synthetic lambda (starts with "lambda$")
                if (implMethodName.startsWith("lambda$")) {
                    // Generate a more meaningful ID
                    return generateStepId();
                }
                
                // It's a method reference, return the method name
                log.debug("Extracted method name from SerializedLambda: {}", implMethodName);
                return implMethodName;
                
            } catch (Exception e) {
                log.debug("Could not extract method name from SerializedLambda: {}", e.getMessage());
            }
        }
        
        // Fallback: try to extract from class name
        String className = lambda.getClass().getName();
        
        if (className.contains("$$Lambda$")) {
            // Extract the base class name as a hint
            int lambdaIndex = className.indexOf("$$Lambda$");
            String baseClass = className.substring(0, lambdaIndex);
            int lastDot = baseClass.lastIndexOf('.');
            if (lastDot >= 0) {
                baseClass = baseClass.substring(lastDot + 1);
            }
            return baseClass.toLowerCase() + "_" + generateStepId().substring(5); // Remove "step_" prefix
        }
        
        // Ultimate fallback
        return generateStepId();
    }
    
    /**
     * Generate a unique step ID.
     * 
     * @return A unique step ID
     */
    private String generateStepId() {
        return "step_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Marker types for branch decisions.
     */
    private record BranchTrue() implements InternalRoutingMarker {}
    private record BranchFalse() implements InternalRoutingMarker {}
    public record BranchValue<V>(V value) implements InternalRoutingMarker {}
    private record BranchOtherwise() implements InternalRoutingMarker {}
    
    /**
     * Creates a proxy method for TriFunction to satisfy AsyncStepMetadata requirements.
     */
    private Method createProxyMethodForTriFunction() {
        try {
            // Get a method with the right signature from TriFunction interface
            return TriFunction.class.getMethod("apply", Object.class, Object.class, Object.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to create proxy method", e);
        }
    }
    
    /**
     * Creates a synthetic AsyncStep annotation.
     */
    private AsyncStep createSyntheticAsyncAnnotation(String value) {
        return new AsyncStep() {
            @Override
            public String value() {
                return value;
            }
            
            @Override
            public String description() {
                return "Async handler for pattern: " + value;
            }
            
            @Override
            public Class<?> inputClass() {
                return Map.class;
            }
            
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return AsyncStep.class;
            }
        };
    }
    
    
    /**
     * Information about a registered async handler.
     */
    private static class AsyncHandlerInfo {
        private final String pattern;
        private final TriFunction<Map<String, Object>, WorkflowContext, AsyncProgressReporter, StepResult<?>> handler;
        private final String methodName;
        private boolean fromAnnotation = false;
        private AsyncStep annotation;
        
        AsyncHandlerInfo(String pattern, 
                        TriFunction<Map<String, Object>, WorkflowContext, AsyncProgressReporter, StepResult<?>> handler,
                        String methodName) {
            this.pattern = pattern;
            this.handler = handler;
            this.methodName = methodName != null ? methodName : "asyncHandler";
        }
        
        void setFromAnnotation(boolean fromAnnotation) {
            this.fromAnnotation = fromAnnotation;
        }
        
        void setAnnotation(AsyncStep annotation) {
            this.annotation = annotation;
        }
    }
    
    /**
     * Functional interface for async handlers.
     */
    @FunctionalInterface
    public interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }
}