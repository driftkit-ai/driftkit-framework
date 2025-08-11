package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.annotations.AsyncStep;
import ai.driftkit.workflow.engine.annotations.InitialStep;
import ai.driftkit.workflow.engine.annotations.Step;
import ai.driftkit.workflow.engine.annotations.Workflow;
import ai.driftkit.workflow.engine.analyzer.*;
import ai.driftkit.workflow.engine.builder.WorkflowBuilder;
import ai.driftkit.workflow.engine.graph.Edge;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.*;
import java.util.Queue;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Analyzes workflow classes annotated with @Workflow and builds a WorkflowGraph
 * by examining method annotations and return types.
 *
 * <p>This analyzer implements the automatic graph construction strategy described
 * in the technical specification, including:</p>
 * <ul>
 *   <li>Discovery of step methods via annotations</li>
 *   <li>Automatic edge creation based on return types</li>
 *   <li>Support for sealed interface branching</li>
 *   <li>Type-based step matching</li>
 * </ul>
 */
@Slf4j
@UtilityClass
public class WorkflowAnalyzer {
    

    /**
     * Analyzes a workflow instance and builds a WorkflowGraph.
     *
     * @param workflowInstance An instance of a class annotated with @Workflow
     * @param <T> The workflow input type
     * @param <R> The workflow output type
     * @return The constructed WorkflowGraph
     * @throws IllegalArgumentException if the workflow is invalid
     * @throws NullPointerException if workflowInstance is null
     */
    @SuppressWarnings("unchecked")
    public static <T, R> WorkflowGraph<T, R> analyze(Object workflowInstance) {
        if (workflowInstance == null) {
            throw new NullPointerException("Workflow instance cannot be null");
        }

        Class<?> workflowClass = workflowInstance.getClass();

        // Verify @Workflow annotation
        Workflow workflowAnnotation = workflowClass.getAnnotation(Workflow.class);
        if (workflowAnnotation == null) {
            throw new IllegalArgumentException(
                    "Class must be annotated with @Workflow: " + workflowClass.getName()
            );
        }

        log.debug("Analyzing workflow: {} ({})", workflowAnnotation.id(), workflowClass.getName());

        try {
            // Discover all step methods
            Map<String, StepInfo> stepInfos = discoverSteps(workflowClass, workflowInstance);

            // Find initial step
            String initialStepId = findInitialStep(stepInfos);

            // Build nodes
            Map<String, StepNode> nodes = buildNodes(stepInfos);

            // Build edges by analyzing return types
            Map<String, List<Edge>> edges = buildEdges(stepInfos);

            // Determine input and output types
            StepInfo initialStep = stepInfos.get(initialStepId);
            Class<T> inputType = (Class<T>) initialStep.getInputType();
            Class<R> outputType = (Class<R>) determineOutputType(stepInfos);

            // Find and analyze async steps
            Map<String, AsyncStepMetadata> asyncSteps = findAsyncSteps(workflowInstance);

            // Validate async steps reference valid parent steps
            validateAsyncSteps(stepInfos, asyncSteps);

            // Log analysis results
            log.info("Workflow analysis complete: {} - {} nodes, {} edges, {} async handlers",
                    workflowAnnotation.id(), nodes.size(),
                    edges.values().stream().mapToInt(List::size).sum(),
                    asyncSteps.size());

            // DEBUG: Workflow edges logged at debug level
            if (log.isDebugEnabled()) {
                edges.forEach((from, edgeList) -> {
                    edgeList.forEach(edge -> {
                        log.debug("  {} -> {} (type: {}, event: {})",
                                from, edge.toStepId(), edge.type(), edge.eventType());
                    });
                });
            }

            return WorkflowGraph.<T, R>builder()
                    .id(workflowAnnotation.id())
                    .version(workflowAnnotation.version())
                    .inputType(inputType)
                    .outputType(outputType)
                    .nodes(nodes)
                    .edges(edges)
                    .initialStepId(initialStepId)
                    .workflowInstance(workflowInstance)
                    .asyncStepMetadata(asyncSteps)
                    .build();

        } catch (Exception e) {
            log.error("Failed to analyze workflow: {}", workflowClass.getName(), e);
            throw new IllegalArgumentException(
                    "Failed to analyze workflow " + workflowClass.getName() + ": " + e.getMessage(), e
            );
        }
    }

    /**
     * Analyzes a workflow created with the builder API.
     * 
     * @param builderWorkflow The workflow created using the builder API
     * @param <T> The workflow input type
     * @param <R> The workflow output type
     * @return The constructed and validated WorkflowGraph
     * @throws IllegalArgumentException if the workflow is invalid
     */
    public static <T, R> WorkflowGraph<T, R> analyzeBuilder(WorkflowBuilder<T, R> builderWorkflow) {
        if (builderWorkflow == null) {
            throw new IllegalArgumentException("Builder workflow cannot be null");
        }
        
        log.info("Analyzing builder workflow");
        
        // Build the graph directly
        WorkflowGraph<T, R> graph = builderWorkflow.build();
        
        // Validate the graph structure
        validateBuilderGraph(graph);
        
        // Log graph statistics
        log.info("Enhanced workflow graph '{}' with {} nodes and {} edges", 
            graph.id(),
            graph.nodes().size(),
            graph.edges().values().stream().mapToInt(List::size).sum()
        );
        
        return graph;
    }

    /**
     * Discovers all step methods in the workflow class.
     */
    private static Map<String, StepInfo> discoverSteps(Class<?> workflowClass, Object instance) {
        Map<String, StepInfo> steps = new HashMap<>();
        Set<String> methodNames = new HashSet<>();

        // Process all public methods
        for (Method method : workflowClass.getMethods()) {
            // Skip methods from Object class
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            StepInfo stepInfo = null;

            // Check for @InitialStep
            InitialStep initialAnnotation = method.getAnnotation(InitialStep.class);
            if (initialAnnotation != null) {
                if (method.getAnnotation(Step.class) != null ||
                        method.getAnnotation(AsyncStep.class) != null) {
                    throw new IllegalArgumentException(
                            "Method cannot have multiple step annotations: " + method.getName()
                    );
                }

                String id = method.getName();
                stepInfo = StepInfo.builder()
                        .id(id)
                        .method(method)
                        .instance(instance)
                        .isInitial(true)
                        .description(initialAnnotation.description())
                        .build();
            }

            // Check for @Step
            Step stepAnnotation = method.getAnnotation(Step.class);
            if (stepAnnotation != null) {
                if (initialAnnotation != null || method.getAnnotation(AsyncStep.class) != null) {
                    throw new IllegalArgumentException(
                            "Method cannot have multiple step annotations: " + method.getName()
                    );
                }

                // Determine step ID - priority: value, id, method name
                String id = StringUtils.isNotBlank(stepAnnotation.id()) ? stepAnnotation.id() : method.getName();

                stepInfo = StepInfo.builder()
                        .id(id)
                        .method(method)
                        .instance(instance)
                        .description(stepAnnotation.description())
                        .index(stepAnnotation.index())
                        .timeoutMs(stepAnnotation.timeoutMs())
                        .nextClasses(stepAnnotation.nextClasses())
                        .nextSteps(stepAnnotation.nextSteps())
                        .condition(stepAnnotation.condition())
                        .onTrue(stepAnnotation.onTrue())
                        .onFalse(stepAnnotation.onFalse())
                        .build();
            }

            // Check for @AsyncStep - skip these, they are handled separately
            AsyncStep asyncAnnotation = method.getAnnotation(AsyncStep.class);
            if (asyncAnnotation != null) {
                if (initialAnnotation != null || stepAnnotation != null) {
                    throw new IllegalArgumentException(
                            "Method cannot have multiple step annotations: " + method.getName()
                    );
                }
                // Skip adding @AsyncStep methods to the step graph
                // They are handled separately via asyncStepMetadata
                continue;
            }

            if (stepInfo != null) {
                // Make method accessible if needed
                if (!method.canAccess(instance)) {
                    method.setAccessible(true);
                }

                // Track method names to detect overloading
                if (methodNames.contains(method.getName())) {
                    log.warn("Method overloading detected for step: {}. " +
                            "Consider using explicit step IDs to avoid ambiguity.", method.getName());
                }
                methodNames.add(method.getName());

                // Validate method signature
                validateStepMethod(method);

                // Analyze method parameters
                MethodAnalyzer.analyzeMethodParameters(stepInfo);

                // Override input type if specified in annotation
                if (stepAnnotation != null && stepAnnotation.inputClass() != void.class) {
                    stepInfo.setInputType(stepAnnotation.inputClass());
                } else if (asyncAnnotation != null && asyncAnnotation.inputClass() != void.class) {
                    stepInfo.setInputType(asyncAnnotation.inputClass());
                }

                // Analyze return type
                MethodAnalyzer.analyzeReturnType(stepInfo);

                // Check for duplicate IDs
                if (steps.containsKey(stepInfo.getId())) {
                    throw new IllegalArgumentException(
                            "Duplicate step ID: " + stepInfo.getId() +
                                    " (methods: " + steps.get(stepInfo.getId()).getMethod().getName() +
                                    " and " + method.getName() + ")"
                    );
                }

                steps.put(stepInfo.getId(), stepInfo);
                log.debug("Discovered step: {} (method: {}, async: {}, initial: {})",
                        stepInfo.getId(), method.getName(), stepInfo.isInitial());
            }
        }

        if (steps.isEmpty()) {
            throw new IllegalArgumentException(
                    "No steps found in workflow: " + workflowClass.getName() +
                            ". Ensure methods are annotated with @InitialStep, @Step, or @AsyncStep"
            );
        }

        return steps;
    }





    /**
     * Finds the initial step ID.
     */
    private static String findInitialStep(Map<String, StepInfo> steps) {
        List<String> initialSteps = steps.values().stream()
                .filter(StepInfo::isInitial)
                .map(StepInfo::getId)
                .collect(Collectors.toList());

        if (initialSteps.isEmpty()) {
            throw new IllegalArgumentException(
                    "No step marked with @InitialStep. Every workflow must have exactly one initial step."
            );
        }

        if (initialSteps.size() > 1) {
            throw new IllegalArgumentException(
                    "Multiple steps marked with @InitialStep: " + initialSteps +
                            ". A workflow can have only one initial step."
            );
        }

        return initialSteps.get(0);
    }

    /**
     * Builds StepNode instances from StepInfo.
     */
    private static Map<String, StepNode> buildNodes(Map<String, StepInfo> stepInfos) {
        Map<String, StepNode> nodes = new HashMap<>();

        for (StepInfo info : stepInfos.values()) {
            try {
                String description = info.getDescription();
                if (StringUtils.isEmpty(description)) {
                    description = generateStepDescription(info);
                }

                StepNode node = StepNode.fromMethod(
                        info.getId(),
                        info.getMethod(),
                        info.getInstance()
                );

                if (info.isInitial()) {
                    node = node.asInitial();
                }

                if (!description.equals(node.description())) {
                    node = node.withDescription(description);
                }

                nodes.put(info.getId(), node);

            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to create StepNode for " + info.getId() + ": " + e.getMessage(), e
                );
            }
        }

        return nodes;
    }

    /**
     * Generates a descriptive name for a step based on its metadata.
     */
    private static String generateStepDescription(StepInfo info) {
        StringBuilder desc = new StringBuilder();

        if (info.isInitial()) {
            desc.append("Initial step: ");
        } else {
            desc.append("Step: ");
        }

        desc.append(info.getMethod().getName());

        if (info.getInputType() != null && info.getInputType() != Object.class) {
            desc.append(" (").append(info.getInputType().getSimpleName()).append(")");
        }

        return desc.toString();
    }

    /**
     * Builds edges by analyzing step return types and finding matching input types.
     */
    private static Map<String, List<Edge>> buildEdges(Map<String, StepInfo> stepInfos) {
        Map<String, List<Edge>> edges = new HashMap<>();

        for (StepInfo fromStep : stepInfos.values()) {
            List<Edge> stepEdges = new ArrayList<>();

            // First priority: type-based routing from nextClasses annotation
            if (fromStep.getNextClasses() != null && fromStep.getNextClasses().length > 0) {
                addNextClassEdges(fromStep, stepInfos, stepEdges);
            }

            // Second priority: explicit nextSteps from annotation
            if (fromStep.getNextSteps() != null && fromStep.getNextSteps().length > 0) {
                addExplicitEdges(fromStep, stepInfos, stepEdges);
            }

            // Third priority: condition-based branching (onTrue/onFalse)
            if (fromStep.getCondition() != null && !fromStep.getCondition().isEmpty()) {
                addConditionalEdges(fromStep, stepInfos, stepEdges);
            }

            // Fourth priority: automatic type-based routing (existing logic)
            if (stepEdges.isEmpty()) {
                log.debug("Step {} has no explicit edges, checking automatic routing", fromStep.getId());
                log.debug("  Continue type: {}", fromStep.getPossibleContinueType());
                log.debug("  Branch types: {}", fromStep.getPossibleBranchTypes());

                // Handle Continue<T> case - sequential flow
                if (fromStep.getPossibleContinueType() != null) {
                    addSequentialEdges(fromStep, stepInfos, stepEdges);
                }

                // Handle Branch with sealed interface - branching flow
                if (!fromStep.getPossibleBranchTypes().isEmpty()) {
                    addBranchEdges(fromStep, stepInfos, stepEdges);
                }
            }

            // Handle error edges for steps that might fail
            addErrorEdges(fromStep, stepInfos, stepEdges);

            // Sort edges by priority (sequential, branch, error)
            stepEdges.sort(Comparator.comparing(Edge::type));

            if (!stepEdges.isEmpty()) {
                edges.put(fromStep.getId(), stepEdges);
            }
        }

        // Validate graph connectivity
        // Validate graph structure
        Map<String, StepNode> nodeMap = new HashMap<>();
        for (StepInfo stepInfo : stepInfos.values()) {
            StepNode node = buildNodes(Map.of(stepInfo.getId(), stepInfo)).get(stepInfo.getId());
            nodeMap.put(node.id(), node);
        }
        String initialStep = findInitialStep(stepInfos);
        validateGraph(nodeMap, edges, initialStep);

        return edges;
    }

    /**
     * Adds sequential edges for Continue<T> return types.
     */
    private static void addSequentialEdges(StepInfo fromStep, Map<String, StepInfo> allSteps,
                                           List<Edge> edges) {
        Class<?> continueType = fromStep.getPossibleContinueType();
        List<StepInfo> compatibleSteps = new ArrayList<>();

        for (StepInfo toStep : allSteps.values()) {
            if (toStep != fromStep && TypeUtils.isTypeCompatible(continueType, toStep.getInputType())) {
                compatibleSteps.add(toStep);
            }
        }

        if (compatibleSteps.isEmpty() && continueType != Void.class) {
            log.warn("Step {} produces type {} but no step accepts this type",
                    fromStep.getId(), continueType.getSimpleName());
        }

        // Add edges to all compatible steps
        for (StepInfo toStep : compatibleSteps) {
            edges.add(Edge.sequential(fromStep.getId(), toStep.getId()));
            log.debug("Added sequential edge: {} -> {} (type: {})",
                    fromStep.getId(), toStep.getId(), continueType.getSimpleName());
        }
    }

    /**
     * Adds branch edges for sealed interface return types.
     */
    private static void addBranchEdges(StepInfo fromStep, Map<String, StepInfo> allSteps,
                                       List<Edge> edges) {
        log.debug("Adding branch edges for step {} with branch types: {}",
                fromStep.getId(), fromStep.getPossibleBranchTypes());

        for (Class<?> branchType : fromStep.getPossibleBranchTypes()) {
            List<StepInfo> compatibleSteps = new ArrayList<>();

            for (StepInfo toStep : allSteps.values()) {
                log.debug("Checking compatibility: {} (input: {}) for branch type {}",
                        toStep.getId(), toStep.getInputType(), branchType);

                if (toStep != fromStep && TypeUtils.isTypeCompatible(branchType, toStep.getInputType())) {
                    compatibleSteps.add(toStep);
                }
            }

            if (compatibleSteps.isEmpty()) {
                log.warn("Step {} can branch to type {} but no step accepts this type",
                        fromStep.getId(), branchType.getSimpleName());
            }

            // Add edges to all compatible steps for this branch type
            for (StepInfo toStep : compatibleSteps) {
                edges.add(Edge.branch(fromStep.getId(), toStep.getId(), branchType));
                log.debug("Added branch edge: {} -> {} (type: {})",
                        fromStep.getId(), toStep.getId(), branchType.getSimpleName());
            }
        }
    }

    /**
     * Adds edges based on nextClasses annotation field.
     */
    private static void addNextClassEdges(StepInfo fromStep, Map<String, StepInfo> allSteps,
                                          List<Edge> edges) {
        if (ArrayUtils.isEmpty(fromStep.getNextClasses())) {
            return;
        }

        log.debug("Adding nextClass edges for step {} with nextClasses: {}",
                fromStep.getId(), Arrays.toString(fromStep.getNextClasses()));

        for (Class<?> nextClass : fromStep.getNextClasses()) {
            for (StepInfo toStep : allSteps.values()) {
                if (toStep == fromStep) {
                    continue;
                }

                log.debug("  Checking: {} -> {} (input: {})",
                        nextClass.getSimpleName(), toStep.getId(), toStep.getInputType());

                if (TypeUtils.isTypeCompatible(nextClass, toStep.getInputType())) {
                    edges.add(Edge.sequential(fromStep.getId(), toStep.getId()));
                    log.debug("  ADDED nextClass edge: {} -> {} (type: {})",
                            fromStep.getId(), toStep.getId(), nextClass.getSimpleName());
                }
            }
        }
    }

    /**
     * Adds edges based on explicit nextSteps annotation field.
     */
    private static void addExplicitEdges(StepInfo fromStep, Map<String, StepInfo> allSteps,
                                         List<Edge> edges) {
        if (ArrayUtils.isEmpty(fromStep.getNextSteps())) {
            return;
        }

        for (String nextStepId : fromStep.getNextSteps()) {
            if (!allSteps.containsKey(nextStepId)) {
                log.warn("Step {} references non-existent next step: {}",
                        fromStep.getId(), nextStepId);
                continue;
            }

            edges.add(Edge.sequential(fromStep.getId(), nextStepId));
            log.debug("Added explicit edge: {} -> {}", fromStep.getId(), nextStepId);
        }
    }

    /**
     * Adds edges based on condition, onTrue, and onFalse annotation fields.
     */
    private static void addConditionalEdges(StepInfo fromStep, Map<String, StepInfo> allSteps,
                                            List<Edge> edges) {
        if (StringUtils.isEmpty(fromStep.getCondition())) {
            return;
        }

        String onTrue = fromStep.getOnTrue();
        String onFalse = fromStep.getOnFalse();

        if (StringUtils.isNotEmpty(onTrue)) {
            if (!allSteps.containsKey(onTrue)) {
                log.warn("Step {} references non-existent onTrue step: {}",
                        fromStep.getId(), onTrue);
            } else {
                // Create conditional edge with true predicate
                edges.add(Edge.conditional(
                        fromStep.getId(),
                        onTrue,
                        result -> evaluateCondition(fromStep.getCondition(), result, true),
                        "When " + fromStep.getCondition() + " is true"
                ));
                log.debug("Added conditional true edge: {} -> {}", fromStep.getId(), onTrue);
            }
        }

        if (StringUtils.isNotEmpty(onFalse)) {
            if (!allSteps.containsKey(onFalse)) {
                log.warn("Step {} references non-existent onFalse step: {}",
                        fromStep.getId(), onFalse);
            } else {
                // Create conditional edge with false predicate
                edges.add(Edge.conditional(
                        fromStep.getId(),
                        onFalse,
                        result -> evaluateCondition(fromStep.getCondition(), result, false),
                        "When " + fromStep.getCondition() + " is false"
                ));
                log.debug("Added conditional false edge: {} -> {}", fromStep.getId(), onFalse);
            }
        }
    }

    /**
     * Adds error handling edges.
     */
    private static void addErrorEdges(StepInfo fromStep, Map<String, StepInfo> allSteps,
                                      List<Edge> edges) {
        // Find steps that accept Throwable or Exception
        for (StepInfo toStep : allSteps.values()) {
            if (toStep == fromStep) {
                continue;
            }

            if (!Throwable.class.isAssignableFrom(toStep.getInputType()) &&
                    !Exception.class.isAssignableFrom(toStep.getInputType())) {
                continue;
            }

            edges.add(Edge.error(fromStep.getId(), toStep.getId()));
            log.debug("Added error edge: {} -> {}", fromStep.getId(), toStep.getId());
        }
    }





    /**
     * Determines the overall output type of the workflow.
     */
    private static Class<?> determineOutputType(Map<String, StepInfo> stepInfos) {
        // Look for steps that return Finish<R>
        Set<Class<?>> finishTypes = new HashSet<>();

        for (StepInfo step : stepInfos.values()) {
            StepInfo.ReturnTypeInfo returnInfo = step.getReturnTypeInfo();
            if (returnInfo != null && TypeUtils.isFinishType(returnInfo.rawType())) {
                // Extract R from Finish<R>
                if (returnInfo.innerType() instanceof Class<?> clazz &&
                        !clazz.equals(Void.class) && !clazz.equals(void.class)) {
                    finishTypes.add(clazz);
                }
            }
        }

        if (finishTypes.isEmpty()) {
            log.debug("No Finish<R> return types found, defaulting output type to Object");
            return Object.class;
        }

        if (finishTypes.size() == 1) {
            Class<?> outputType = finishTypes.iterator().next();
            log.debug("Determined workflow output type: {}", outputType.getSimpleName());
            return outputType;
        }

        // Multiple finish types - find common superclass
        Class<?> commonType = TypeMatcher.findCommonSuperclass(finishTypes);
        log.debug("Multiple finish types found: {}, using common type: {}",
                finishTypes, commonType.getSimpleName());
        return commonType;
    }



    /**
     * Validates the graph structure created from builder to ensure it's well-formed.
     * 
     * @param graph The graph to validate
     * @throws IllegalStateException if the graph is invalid
     */
    private static void validateBuilderGraph(WorkflowGraph<?, ?> graph) {
        if (graph.nodes() == null || graph.nodes().isEmpty()) {
            throw new IllegalStateException("Workflow graph must have at least one node");
        }
        
        if (graph.initialStepId() == null || graph.initialStepId().isBlank()) {
            throw new IllegalStateException("Workflow graph must have an initial step");
        }
        
        if (!graph.nodes().containsKey(graph.initialStepId())) {
            throw new IllegalStateException(
                "Initial step '" + graph.initialStepId() + "' not found in graph nodes"
            );
        }
        
        // Additional validation can be added here
        // For example: checking for unreachable nodes, cycles, etc.
    }
    
    // Now using StepInfo from analyzer package

    /**
     * Evaluates a condition expression.
     * This is a placeholder implementation - in a real system, you would use
     * Spring Expression Language (SpEL) or another expression evaluator.
     */
    private static boolean evaluateCondition(String condition, Object result, boolean expectedValue) {
        if (StringUtils.isEmpty(condition)) {
            return expectedValue;
        }

        // TODO: Implement proper SpEL evaluation
        // For now, just return the expected value
        log.debug("Condition evaluation not yet implemented for: {}", condition);
        return expectedValue;
    }

    /**
     * Validates a step method.
     * 
     * @param method The method to validate
     * @throws IllegalArgumentException if validation fails
     */
    private static void validateStepMethod(Method method) {
        // Check basic modifiers
        int modifiers = method.getModifiers();
        if (Modifier.isStatic(modifiers)) {
            throw new IllegalArgumentException(
                "Step method cannot be static: " + method.getName()
            );
        }
        if (Modifier.isAbstract(modifiers)) {
            throw new IllegalArgumentException(
                "Step method cannot be abstract: " + method.getName()
            );
        }
        
        // Delegate to MethodAnalyzer for signature validation
        MethodAnalyzer.validateStepMethod(method);
    }
    
    /**
     * Validates the workflow graph structure.
     * 
     * @param nodes The workflow nodes
     * @param edges The workflow edges
     * @param initialStepId The initial step ID
     * @throws IllegalStateException if validation fails
     */
    private static void validateGraph(Map<String, StepNode> nodes, 
                                    Map<String, List<Edge>> edges,
                                    String initialStepId) {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("Workflow has no steps");
        }
        
        if (initialStepId == null || !nodes.containsKey(initialStepId)) {
            throw new IllegalStateException(
                "Initial step not found: " + initialStepId
            );
        }
        
        // Check for unreachable steps
        Set<String> reachableSteps = findReachableSteps(nodes, edges, initialStepId);
        Set<String> unreachableSteps = new HashSet<>(nodes.keySet());
        unreachableSteps.removeAll(reachableSteps);
        
        if (!unreachableSteps.isEmpty()) {
            log.warn("Unreachable steps detected: {}. These steps will never be executed.", 
                    unreachableSteps);
        }
    }
    
    /**
     * Finds all reachable steps from the initial step.
     */
    private static Set<String> findReachableSteps(Map<String, StepNode> nodes,
                                                 Map<String, List<Edge>> edges,
                                                 String initialStepId) {
        Set<String> reachable = new HashSet<>();
        Queue<String> toVisit = new LinkedList<>();
        toVisit.add(initialStepId);
        
        while (!toVisit.isEmpty()) {
            String stepId = toVisit.poll();
            if (reachable.contains(stepId)) {
                continue;
            }
            
            reachable.add(stepId);
            
            // Add all target steps from edges
            List<Edge> stepEdges = edges.get(stepId);
            if (stepEdges != null) {
                for (Edge edge : stepEdges) {
                    if (!reachable.contains(edge.toStepId())) {
                        toVisit.add(edge.toStepId());
                    }
                }
            }
        }
        
        return reachable;
    }

    /**
     * Validates an async step method.
     * 
     * @param method The async method to validate
     * @param annotation The AsyncStep annotation
     * @throws IllegalArgumentException if validation fails
     */
    private static void validateAsyncStepMethod(Method method, AsyncStep annotation) {
        // Check that value is provided in annotation
        if (annotation.value().isEmpty()) {
            throw new IllegalArgumentException(
                "Async step must have a task ID value: " + method.getName()
            );
        }
        
        // Check basic modifiers
        int modifiers = method.getModifiers();
        if (Modifier.isStatic(modifiers)) {
            throw new IllegalArgumentException(
                "Async step method cannot be static: " + method.getName()
            );
        }
        if (Modifier.isAbstract(modifiers)) {
            throw new IllegalArgumentException(
                "Async step method cannot be abstract: " + method.getName()
            );
        }
        
        // Async methods can have up to 3 parameters (input, context, AsyncProgressReporter)
        // So we don't use the regular step validation here
    }

    /**
     * Finds all @AsyncStep annotated methods in a workflow instance.
     *
     * @param workflowInstance The workflow instance to analyze
     * @return Map of asyncStepId to AsyncStepMetadata
     */
    public static Map<String, AsyncStepMetadata> findAsyncSteps(Object workflowInstance) {
        Map<String, AsyncStepMetadata> asyncSteps = new HashMap<>();
        Class<?> clazz = workflowInstance.getClass();

        for (Method method : clazz.getDeclaredMethods()) {
            AsyncStep annotation = method.getAnnotation(AsyncStep.class);
            if (annotation != null) {
                // Validate the async step method
                validateAsyncStepMethod(method, annotation);
                
                String asyncStepId = annotation.value();

                // Make method accessible
                if (!method.canAccess(workflowInstance)) {
                    method.setAccessible(true);
                }

                AsyncStepMetadata metadata = new AsyncStepMetadata(
                        method,
                        workflowInstance,
                        annotation
                );

                // Check for duplicates
                if (asyncSteps.containsKey(asyncStepId)) {
                    throw new IllegalArgumentException(
                            "Multiple @AsyncStep methods with id " + asyncStepId + ": " +
                                    asyncSteps.get(asyncStepId).getMethod().getName() + " and " + method.getName()
                    );
                }

                asyncSteps.put(asyncStepId, metadata);
                log.debug("Found async step handler {} with id {}", method.getName(), asyncStepId);
            }
        }

        return asyncSteps;
    }

    /**
     * Validates that async steps have valid configurations.
     * Note: asyncStepId is not a reference to an existing step, but a task identifier
     * that will be used by StepResult.Async to trigger this handler.
     */
    private static void validateAsyncSteps(Map<String, StepInfo> steps,
                                           Map<String, AsyncStepMetadata> asyncSteps) {
        // Validate async steps have proper configuration
        for (Map.Entry<String, AsyncStepMetadata> entry : asyncSteps.entrySet()) {
            String asyncStepId = entry.getKey();
            AsyncStepMetadata metadata = entry.getValue();

            // Validate the async step has proper configuration
            if (asyncStepId == null || asyncStepId.isEmpty()) {
                throw new IllegalArgumentException(
                        "@AsyncStep " + metadata.getMethod().getName() +
                                " has empty value"
                );
            }

            // Log warning if using default input class
            if (metadata.getAnnotation().inputClass() == Map.class) {
                log.debug("@AsyncStep {} uses default Map.class as input",
                        metadata.getMethod().getName());
            }
        }
    }

    /**
     * Metadata for @AsyncStep annotated methods.
     */
    @Data
    public static class AsyncStepMetadata {
        private final Method method;
        private final Object instance;
        private final AsyncStep annotation;

        public AsyncStepMetadata(Method method, Object instance, AsyncStep annotation) {
            this.method = method;
            this.instance = instance;
            this.annotation = annotation;
        }

        public String getValue() {
            return annotation.value();
        }

        public Class<?> getInputClass() {
            return annotation.inputClass();
        }

        public String getDescription() {
            return annotation.description();
        }
    }
}