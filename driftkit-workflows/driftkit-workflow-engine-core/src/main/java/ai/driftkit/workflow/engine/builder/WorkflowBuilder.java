package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.InternalStepListener;
import ai.driftkit.workflow.engine.core.RetryExecutor;
import ai.driftkit.workflow.engine.async.TaskProgressReporter;
import ai.driftkit.workflow.engine.core.WorkflowAnalyzer;
import ai.driftkit.workflow.engine.core.WorkflowAnalyzer.AsyncStepMetadata;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.annotations.AsyncStep;
import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import ai.driftkit.workflow.engine.annotations.OnInvocationsLimit;
import ai.driftkit.workflow.engine.graph.Edge;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.utils.ReflectionUtils;
import ai.driftkit.workflow.engine.schema.SchemaUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
public class
WorkflowBuilder<T, R> {
    
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
        if (StringUtils.isBlank(id)) {
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
                                                  TriFunction<Map<String, Object>, WorkflowContext, TaskProgressReporter, StepResult<?>> asyncHandler) {
        if (StringUtils.isBlank(taskIdPattern)) {
            throw new IllegalArgumentException("Task ID pattern cannot be null or empty");
        }
        if (asyncHandler == null) {
            throw new IllegalArgumentException("Async handler cannot be null");
        }
        
        // Extract method info if possible
        String methodName = ReflectionUtils.extractLambdaMethodName(asyncHandler);
        
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
            TriFunction<Map<String, Object>, WorkflowContext, TaskProgressReporter, StepResult<?>> wrapper = 
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
        String stepId = ReflectionUtils.extractLambdaMethodName(step);
        
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
        String stepId = ReflectionUtils.extractLambdaMethodName(step);
        
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
        if (StringUtils.isBlank(id)) {
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
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("Step ID cannot be null or empty");
        }
        StepDefinition stepDef = StepDefinition.of(id, step);
        buildSteps.add(new SequentialStep(stepDef));
        lastStepDefinition = stepDef;
        return this;
    }
    
    /**
     * Adds a step with retry policy using Function.
     * 
     * @param id Step ID
     * @param step Function that takes input and returns StepResult
     * @param retryPolicy Retry policy for this step
     */
    public <I, O> WorkflowBuilder<T, R> thenWithRetry(String id, Function<I, StepResult<O>> step, 
                                                      RetryPolicy retryPolicy) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("Step ID cannot be null or empty");
        }
        
        StepDefinition stepDef = StepDefinition.of(id, step).withRetryPolicy(retryPolicy);
        buildSteps.add(new SequentialStep(stepDef));
        lastStepDefinition = stepDef;
        return this;
    }
    
    /**
     * Adds a step with retry policy using BiFunction with context.
     * 
     * @param id Step ID
     * @param step BiFunction that takes input and context and returns StepResult
     * @param retryPolicy Retry policy for this step
     */
    public <I, O> WorkflowBuilder<T, R> thenWithRetry(String id, BiFunction<I, WorkflowContext, StepResult<O>> step,
                                                      RetryPolicy retryPolicy) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("Step ID cannot be null or empty");
        }
        
        StepDefinition stepDef = StepDefinition.of(id, step).withRetryPolicy(retryPolicy);
        buildSteps.add(new SequentialStep(stepDef));
        lastStepDefinition = stepDef;
        return this;
    }
    
    /**
     * Applies a retry policy to the last added step.
     * Can be chained after then() methods.
     * 
     * @param retryPolicy Retry policy to apply
     */
    public WorkflowBuilder<T, R> withRetryPolicy(RetryPolicy retryPolicy) {
        if (lastStepDefinition == null) {
            throw new IllegalStateException("No step to apply retry policy to. Add a step first.");
        }
        
        lastStepDefinition.withRetryPolicy(retryPolicy);
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
        String stepId = ReflectionUtils.extractLambdaMethodName(step);
        
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
        String stepId = ReflectionUtils.extractLambdaMethodName(step);
        
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
        if (StringUtils.isBlank(id)) {
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
        String stepId = ReflectionUtils.extractLambdaMethodName(step);
        
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
        String stepId = ReflectionUtils.extractLambdaMethodName(step);
        
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
            String stepId = ReflectionUtils.extractLambdaMethodName(step);
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
            String stepId = ReflectionUtils.extractLambdaMethodName(step);
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
        
        // Register input type schema
        if (inputType != null && inputType != void.class && inputType != Void.class) {
            SchemaUtils.getSchemaFromClass(inputType);
            log.debug("Registered input type schema for workflow {}: {}", id, inputType.getName());
        }
        
        // Initialize graph components
        Map<String, StepNode> nodes = new HashMap<>();
        Map<String, List<Edge>> edges = new HashMap<>();
        
        // Build the graph from steps
        GraphBuildResult buildResult = buildGraphFromSteps(nodes, edges);
        String initialStepId = buildResult.initialStepId();
        
        // Validate the graph
        validateGraph(nodes, edges, initialStepId);
        
        // Log graph information
        logGraphInfo(nodes, edges);
        
        // Convert async handlers to metadata
        Map<String, AsyncStepMetadata> asyncStepMetadata = buildAsyncStepMetadata();
        
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
     * Builds the graph from the build steps.
     */
    private GraphBuildResult buildGraphFromSteps(Map<String, StepNode> nodes, 
                                                  Map<String, List<Edge>> edges) {
        GraphBuildContext context = new GraphBuildContext();
        String lastStepId = null;
        List<String> lastExitPoints = null;
        String initialStepId = null;
        
        for (int i = 0; i < buildSteps.size(); i++) {
            BuildStep buildStep = buildSteps.get(i);
            BuildStepResult result = buildStep.build(context, lastStepId);
            
            // Process the build step result
            processBuildStepResult(result, nodes, edges);
            
            // Set initial step
            if (i == 0 && !result.entryPoints.isEmpty()) {
                initialStepId = result.entryPoints.get(0);
            }
            
            // Connect to previous step(s)
            connectToPreviousSteps(result, edges, lastStepId, lastExitPoints);
            
            // Update last step tracking
            LastStepInfo lastStepInfo = updateLastStepTracking(result);
            lastStepId = lastStepInfo.lastStepId();
            lastExitPoints = lastStepInfo.lastExitPoints();
        }
        
        return new GraphBuildResult(initialStepId);
    }
    
    /**
     * Processes the result from a build step.
     */
    private void processBuildStepResult(BuildStepResult result, 
                                        Map<String, StepNode> nodes,
                                        Map<String, List<Edge>> edges) {
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
    }
    
    /**
     * Connects the current step to previous steps.
     */
    private void connectToPreviousSteps(BuildStepResult result,
                                        Map<String, List<Edge>> edges,
                                        String lastStepId,
                                        List<String> lastExitPoints) {
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
    }
    
    /**
     * Updates tracking of the last step based on exit points.
     */
    private LastStepInfo updateLastStepTracking(BuildStepResult result) {
        if (!result.exitPoints.isEmpty()) {
            if (result.exitPoints.size() == 1) {
                return new LastStepInfo(result.exitPoints.get(0), null);
            } else {
                // Multiple exit points (e.g., from branches)
                return new LastStepInfo(null, new ArrayList<>(result.exitPoints));
            }
        } else {
            return new LastStepInfo(null, null);
        }
    }
    
    /**
     * Logs information about the constructed graph.
     */
    private void logGraphInfo(Map<String, StepNode> nodes, Map<String, List<Edge>> edges) {
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
    }
    
    /**
     * Builds async step metadata from registered handlers.
     */
    private Map<String, AsyncStepMetadata> buildAsyncStepMetadata() {
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
        
        return asyncStepMetadata;
    }
    
    /**
     * Result of building the graph.
     */
    private record GraphBuildResult(String initialStepId) {}
    
    /**
     * Information about the last step in the build process.
     */
    private record LastStepInfo(String lastStepId, List<String> lastExitPoints) {}
    
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
     * Parallel step implementation - executes all steps in parallel and collects results.
     */
    private record ParallelStep(List<StepDefinition> parallelSteps) implements BuildStep {
        @Override
        public BuildStepResult build(GraphBuildContext context, String previousStepId) {
            BuildStepResult result = new BuildStepResult();
            
            // Determine output type from first step
            Class<?> outputType = parallelSteps.isEmpty() ? Object.class :
                parallelSteps.get(0).getOutputType() != null ? parallelSteps.get(0).getOutputType() : Object.class;
            
            // Create a single node that executes all steps in parallel
            String parallelNodeId = "parallel_" + context.nextId();
            StepNode parallelNode = StepNode.fromBiFunction(
                parallelNodeId,
                (Object input, WorkflowContext ctx) -> {
                    // Execute all steps in parallel using CompletableFuture
                    List<CompletableFuture<StepResult<?>>> futures = new ArrayList<>();
                    
                    for (StepDefinition stepDef : parallelSteps) {
                        CompletableFuture<StepResult<?>> future = CompletableFuture.supplyAsync(() -> {
                            try {
                                // Execute the step with the same input
                                return (StepResult<?>) stepDef.getExecutor().execute(input, ctx);
                            } catch (Exception e) {
                                log.error("Parallel step {} failed", stepDef.getId(), e);
                                return StepResult.fail(e);
                            }
                        });
                        futures.add(future);
                    }
                    
                    // Wait for all to complete
                    try {
                        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                        
                        // Collect all results with proper typing
                        List<Object> results = new ArrayList<>();
                        for (CompletableFuture<StepResult<?>> future : futures) {
                            StepResult<?> stepResult = future.get();
                            if (stepResult instanceof StepResult.Continue<?> cont) {
                                results.add(cont.data());
                            } else if (stepResult instanceof StepResult.Fail<?> fail) {
                                // If any step failed, fail the whole parallel execution
                                return StepResult.fail(fail.error());
                            }
                        }
                        
                        // Return the typed list of results
                        return StepResult.continueWith(results);
                    } catch (Exception e) {
                        log.error("Parallel execution failed", e);
                        return StepResult.fail(e);
                    }
                },
                Object.class,  // Input type
                List.class     // Output type is List
            ).withDescription("Parallel execution of " + parallelSteps.size() + " steps");
            
            result.nodes.add(parallelNode);
            result.entryPoints.add(parallelNodeId);
            result.exitPoints.add(parallelNodeId);
            
            return result;
        }
    }
    
    /**
     * Typed branch step implementation - executes branch as a single step.
     */
    private record TypedBranchStep<I>(Predicate<WorkflowContext> condition,
                                     WorkflowBuilder<I, ?> ifTrue,
                                     WorkflowBuilder<I, ?> ifFalse,
                                     Class<I> inputType) implements BuildStep {
        @Override
        public BuildStepResult build(GraphBuildContext context, String previousStepId) {
            BuildStepResult result = new BuildStepResult();
            
            // Collect all steps from both branches
            final List<StepDefinition> trueSteps = collectSteps(ifTrue);
            final List<StepDefinition> falseSteps = collectSteps(ifFalse);
            
            // Create a single branch node that executes the appropriate branch
            String branchNodeId = "branch_" + context.nextId();
            StepNode branchNode = StepNode.fromBiFunction(
                branchNodeId,
                (Object input, WorkflowContext ctx) -> {
                    boolean conditionResult = condition.test(ctx);
                    log.debug("Branch condition evaluated to: {}", conditionResult);
                    
                    // Select which steps to execute
                    List<StepDefinition> stepsToExecute = conditionResult ? trueSteps : falseSteps;
                    
                    // Execute the selected branch steps sequentially
                    Object currentInput = input;
                    Object lastResult = null;
                    
                    for (StepDefinition stepDef : stepsToExecute) {
                        try {
                            // Notify context about internal step execution for test tracking
                            ctx.notifyInternalStepExecution(stepDef.getId(), currentInput);
                            
                            log.debug("Processing internal step {} in branch, has retry policy: {}", 
                                stepDef.getId(), stepDef.getRetryPolicy() != null);
                            
                            // Check if listener wants to intercept this step
                            StepResult<?> stepResult;
                            InternalStepListener listener = ctx.getInternalStepListener();
                            if (listener != null) {
                                var intercepted = listener.interceptInternalStep(stepDef.getId(), currentInput, ctx);
                                if (intercepted.isPresent()) {
                                    stepResult = intercepted.get();
                                    // If the intercepted result is a failure and the step has a retry policy,
                                    // we need to handle it properly by letting executeStepWithRetry handle the retry
                                    if (stepResult instanceof StepResult.Fail && stepDef.getRetryPolicy() != null) {
                                        log.debug("Intercepted mock returned failure for step {} with retry policy, delegating to retry executor", stepDef.getId());
                                        // Wrap the mock's behavior in the step executor
                                        @SuppressWarnings("unchecked")
                                        BiFunction<Object, WorkflowContext, StepResult<Object>> wrappedExecutor = 
                                            (Object input2, WorkflowContext ctx2) -> {
                                                // Try to intercept again
                                                var intercepted2 = listener.interceptInternalStep(stepDef.getId(), input2, ctx2);
                                                if (intercepted2.isPresent()) {
                                                    return (StepResult<Object>) intercepted2.get();
                                                } else {
                                                    // Fall back to original executor
                                                    try {
                                                        return (StepResult<Object>) stepDef.getExecutor().execute(input2, ctx2);
                                                    } catch (Exception e) {
                                                        if (e instanceof RuntimeException) {
                                                            throw (RuntimeException) e;
                                                        }
                                                        throw new RuntimeException("Step execution failed", e);
                                                    }
                                                }
                                            };
                                        
                                        StepDefinition wrappedStep = StepDefinition.of(stepDef.getId(), wrappedExecutor)
                                            .withRetryPolicy(stepDef.getRetryPolicy())
                                            .withInvocationLimit(stepDef.getInvocationLimit())
                                            .withOnInvocationsLimit(stepDef.getOnInvocationsLimit());
                                        stepResult = executeStepWithRetry(wrappedStep, currentInput, ctx);
                                    }
                                } else {
                                    stepResult = executeStepWithRetry(stepDef, currentInput, ctx);
                                }
                            } else {
                                stepResult = executeStepWithRetry(stepDef, currentInput, ctx);
                            }
                            
                            // Notify listener about completion
                            if (listener != null) {
                                listener.afterInternalStep(stepDef.getId(), stepResult, ctx);
                            }
                            
                            if (stepResult instanceof StepResult.Continue<?> cont) {
                                lastResult = cont.data();
                                currentInput = lastResult; // Pass output to next step
                            } else if (stepResult instanceof StepResult.Fail<?> fail) {
                                return StepResult.fail(fail.error());
                            } else if (stepResult instanceof StepResult.Finish<?> finish) {
                                return StepResult.finish(finish.result());
                            } else if (stepResult instanceof StepResult.Suspend<?> suspend) {
                                return stepResult; // Return suspension as-is
                            } else if (stepResult instanceof StepResult.Async<?> async) {
                                return stepResult; // Return async as-is for the engine to handle
                            } else if (stepResult instanceof StepResult.Branch<?> branch) {
                                // Handle branch events
                                return stepResult;
                            }
                        } catch (Exception e) {
                            log.error("Branch step {} failed", stepDef.getId(), e);
                            // Notify listener about error
                            InternalStepListener listener = ctx.getInternalStepListener();
                            if (listener != null) {
                                listener.onInternalStepError(stepDef.getId(), e, ctx);
                            }
                            // Re-throw the exception so RetryExecutor can handle it
                            if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                            } else {
                                throw new RuntimeException("Step execution failed", e);
                            }
                        }
                    }
                    
                    // Return the result from the last step in the branch
                    return StepResult.continueWith(lastResult != null ? lastResult : input);
                },
                inputType,  // Input type
                Object.class  // Output type
            ).withDescription("Branch: " + (trueSteps.size() + falseSteps.size()) + " possible steps");
            
            result.nodes.add(branchNode);
            result.entryPoints.add(branchNodeId);
            result.exitPoints.add(branchNodeId);
            
            return result;
        }
        
        private List<StepDefinition> collectSteps(WorkflowBuilder<?, ?> workflow) {
            List<StepDefinition> steps = new ArrayList<>();
            for (BuildStep buildStep : workflow.buildSteps) {
                if (buildStep instanceof SequentialStep sequential) {
                    steps.add(sequential.stepDef());
                }
                // For nested branches/parallel, we'd need to handle them recursively
                // For now, keeping it simple
            }
            return steps;
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
     * Multi-branch step implementation - executes as a single step.
     */
    static class MultiBranchStep<V> implements BuildStep {
        private final Function<WorkflowContext, V> selector;
        private final Map<V, Consumer<WorkflowBuilder<?, ?>>> cases;
        private final Consumer<WorkflowBuilder<?, ?>> otherwiseCase;
        
        MultiBranchStep(Function<WorkflowContext, V> selector,
                       Map<V, Consumer<WorkflowBuilder<?, ?>>> cases,
                       Consumer<WorkflowBuilder<?, ?>> otherwiseCase) {
            this.selector = selector;
            this.cases = new LinkedHashMap<>(cases); // Preserve order
            this.otherwiseCase = otherwiseCase;
        }
        
        @Override
        public BuildStepResult build(GraphBuildContext context, String previousStepId) {
            BuildStepResult result = new BuildStepResult();
            
            // Collect all steps from all branches
            final Map<V, List<StepDefinition>> caseSteps = new LinkedHashMap<>();
            for (Map.Entry<V, Consumer<WorkflowBuilder<?, ?>>> entry : cases.entrySet()) {
                WorkflowBuilder<Object, Object> caseBuilder = new WorkflowBuilder<>(
                    "case-" + entry.getKey(), Object.class, Object.class
                );
                entry.getValue().accept(caseBuilder);
                caseSteps.put(entry.getKey(), collectSteps(caseBuilder));
            }
            
            final List<StepDefinition> otherwiseSteps;
            if (otherwiseCase != null) {
                WorkflowBuilder<Object, Object> otherwiseBuilder = new WorkflowBuilder<>(
                    "otherwise", Object.class, Object.class
                );
                otherwiseCase.accept(otherwiseBuilder);
                otherwiseSteps = collectSteps(otherwiseBuilder);
            } else {
                otherwiseSteps = null;
            }
            
            // Create a single multi-branch node
            String multiBranchNodeId = "multi_branch_" + context.nextId();
            StepNode multiBranchNode = StepNode.fromBiFunction(
                multiBranchNodeId,
                (Object input, WorkflowContext ctx) -> {
                    // Get the selector value
                    V value = selector.apply(ctx);
                    log.debug("Multi-branch selector returned: {} (type: {})", 
                        value, value != null ? value.getClass().getSimpleName() : "null");
                    
                    // Find the matching case
                    List<StepDefinition> stepsToExecute = caseSteps.get(value);
                    if (stepsToExecute == null && otherwiseSteps != null) {
                        stepsToExecute = otherwiseSteps;
                        log.debug("No case matched, using otherwise branch");
                    }
                    
                    if (stepsToExecute == null) {
                        throw new IllegalStateException("No branch matched for value: " + value);
                    }
                    
                    // Execute the selected branch steps sequentially
                    Object currentInput = input;
                    Object lastResult = null;
                    
                    for (StepDefinition stepDef : stepsToExecute) {
                        try {
                            log.debug("Executing branch step: {} with input type: {}", 
                                stepDef.getId(), currentInput != null ? currentInput.getClass().getSimpleName() : "null");
                            
                            // Notify context about internal step execution for test tracking
                            ctx.notifyInternalStepExecution(stepDef.getId(), currentInput);
                            
                            log.debug("Processing internal step {} in multi-branch, has retry policy: {}", 
                                stepDef.getId(), stepDef.getRetryPolicy() != null);
                            
                            // Check if listener wants to intercept this step
                            StepResult<?> stepResult;
                            InternalStepListener listener = ctx.getInternalStepListener();
                            if (listener != null) {
                                var intercepted = listener.interceptInternalStep(stepDef.getId(), currentInput, ctx);
                                if (intercepted.isPresent()) {
                                    stepResult = intercepted.get();
                                    // If the intercepted result is a failure and the step has a retry policy,
                                    // we need to handle it properly by letting executeStepWithRetry handle the retry
                                    if (stepResult instanceof StepResult.Fail && stepDef.getRetryPolicy() != null) {
                                        log.debug("Intercepted mock returned failure for step {} with retry policy, delegating to retry executor", stepDef.getId());
                                        // Wrap the mock's behavior in the step executor
                                        @SuppressWarnings("unchecked")
                                        BiFunction<Object, WorkflowContext, StepResult<Object>> wrappedExecutor = 
                                            (Object input2, WorkflowContext ctx2) -> {
                                                // Try to intercept again
                                                var intercepted2 = listener.interceptInternalStep(stepDef.getId(), input2, ctx2);
                                                if (intercepted2.isPresent()) {
                                                    return (StepResult<Object>) intercepted2.get();
                                                } else {
                                                    // Fall back to original executor
                                                    try {
                                                        return (StepResult<Object>) stepDef.getExecutor().execute(input2, ctx2);
                                                    } catch (Exception e) {
                                                        if (e instanceof RuntimeException) {
                                                            throw (RuntimeException) e;
                                                        }
                                                        throw new RuntimeException("Step execution failed", e);
                                                    }
                                                }
                                            };
                                        
                                        StepDefinition wrappedStep = StepDefinition.of(stepDef.getId(), wrappedExecutor)
                                            .withRetryPolicy(stepDef.getRetryPolicy())
                                            .withInvocationLimit(stepDef.getInvocationLimit())
                                            .withOnInvocationsLimit(stepDef.getOnInvocationsLimit());
                                        stepResult = executeStepWithRetry(wrappedStep, currentInput, ctx);
                                    }
                                } else {
                                    stepResult = executeStepWithRetry(stepDef, currentInput, ctx);
                                }
                            } else {
                                stepResult = executeStepWithRetry(stepDef, currentInput, ctx);
                            }
                            
                            // Notify listener about completion
                            if (listener != null) {
                                listener.afterInternalStep(stepDef.getId(), stepResult, ctx);
                            }
                            
                            if (stepResult instanceof StepResult.Continue<?> cont) {
                                lastResult = cont.data();
                                currentInput = lastResult; // Pass output to next step
                            } else if (stepResult instanceof StepResult.Fail<?> fail) {
                                return StepResult.fail(fail.error());
                            } else if (stepResult instanceof StepResult.Finish<?> finish) {
                                return StepResult.finish(finish.result());
                            } else if (stepResult instanceof StepResult.Suspend<?> suspend) {
                                return stepResult; // Return suspension as-is
                            } else if (stepResult instanceof StepResult.Async<?> async) {
                                return stepResult; // Return async as-is for the engine to handle
                            } else if (stepResult instanceof StepResult.Branch<?> branch) {
                                // Handle branch events
                                return stepResult;
                            }
                        } catch (Exception e) {
                            log.error("Multi-branch step {} failed", stepDef.getId(), e);
                            // Notify listener about error
                            InternalStepListener listener = ctx.getInternalStepListener();
                            if (listener != null) {
                                listener.onInternalStepError(stepDef.getId(), e, ctx);
                            }
                            // Re-throw the exception so RetryExecutor can handle it
                            if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                            } else {
                                throw new RuntimeException("Step execution failed", e);
                            }
                        }
                    }
                    
                    // Return the result from the last step in the branch
                    return StepResult.continueWith(lastResult != null ? lastResult : input);
                },
                Object.class,  // Input type
                Object.class   // Output type
            ).withDescription("Multi-branch with " + cases.size() + " cases");
            
            result.nodes.add(multiBranchNode);
            result.entryPoints.add(multiBranchNodeId);
            result.exitPoints.add(multiBranchNodeId);
            
            return result;
        }
        
        private List<StepDefinition> collectSteps(WorkflowBuilder<?, ?> workflow) {
            List<StepDefinition> steps = new ArrayList<>();
            for (BuildStep buildStep : workflow.buildSteps) {
                if (buildStep instanceof SequentialStep sequential) {
                    steps.add(sequential.stepDef());
                }
                // For nested branches/parallel, we'd need to handle them recursively
                // For now, keeping it simple
            }
            return steps;
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
        private final TriFunction<Map<String, Object>, WorkflowContext, TaskProgressReporter, StepResult<?>> handler;
        private final String methodName;
        private boolean fromAnnotation = false;
        private AsyncStep annotation;
        
        AsyncHandlerInfo(String pattern, 
                        TriFunction<Map<String, Object>, WorkflowContext, TaskProgressReporter, StepResult<?>> handler,
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
    
    /**
     * Executes a step with retry support if it has a retry policy.
     * This is used internally when executing steps within branches.
     */
    private static StepResult<?> executeStepWithRetry(StepDefinition stepDef, Object input, WorkflowContext ctx) throws Exception {
        if (stepDef.getRetryPolicy() != null) {
            log.debug("Executing step {} with retry policy: maxAttempts={}, delay={}ms", 
                stepDef.getId(), 
                stepDef.getRetryPolicy().maxAttempts(), 
                stepDef.getRetryPolicy().delay());
            
            // Create a minimal RetryExecutor for this specific step
            RetryExecutor retryExecutor = new RetryExecutor();
            
            // Create a fake WorkflowInstance and StepNode just for retry execution
            WorkflowInstance fakeInstance = WorkflowInstance.builder()
                .instanceId(ctx.getRunId())
                .context(ctx)
                .build();
                
            StepNode fakeStepNode = new StepNode(
                stepDef.getId(),
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
                        return true;
                    }
                },
                false,
                false,
                stepDef.getRetryPolicy(),
                stepDef.getInvocationLimit(),
                stepDef.getOnInvocationsLimit()
            );
            
            // Execute with retry
            return retryExecutor.executeWithRetry(fakeInstance, fakeStepNode, 
                (inst, stp) -> {
                    Object result = stepDef.getExecutor().execute(input, inst.getContext());
                    if (result instanceof StepResult<?>) {
                        return (StepResult<?>) result;
                    } else {
                        // The executor returns the raw result, wrap it in StepResult
                        return StepResult.continueWith(result);
                    }
                });
        } else {
            // No retry policy, execute directly
            Object result = stepDef.getExecutor().execute(input, ctx);
            if (result instanceof StepResult<?>) {
                return (StepResult<?>) result;
            } else {
                // The executor returns the raw result, wrap it in StepResult
                return StepResult.continueWith(result);
            }
        }
    }
}