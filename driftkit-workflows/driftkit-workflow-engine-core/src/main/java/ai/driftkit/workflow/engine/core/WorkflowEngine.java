package ai.driftkit.workflow.engine.core;

import ai.driftkit.common.service.ChatStore;
import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.workflow.engine.async.InMemoryProgressTracker;
import ai.driftkit.workflow.engine.async.ProgressTracker;
import ai.driftkit.workflow.engine.builder.WorkflowBuilder;
import ai.driftkit.workflow.engine.domain.AsyncStepState;
import ai.driftkit.workflow.engine.domain.SuspensionData;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.domain.WorkflowEvent;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.AsyncStepStateRepository;
import ai.driftkit.workflow.engine.persistence.inmemory.InMemoryAsyncStepStateRepository;
import ai.driftkit.workflow.engine.persistence.inmemory.InMemorySuspensionDataRepository;
import ai.driftkit.workflow.engine.persistence.inmemory.InMemoryWorkflowStateRepository;
import ai.driftkit.workflow.engine.persistence.SuspensionDataRepository;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance.WorkflowStatus;
import ai.driftkit.workflow.engine.persistence.WorkflowStateRepository;
import ai.driftkit.workflow.engine.schema.SchemaUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Core workflow execution engine that orchestrates workflow runs.
 *
 * <p>This engine handles:</p>
 * <ul>
 *   <li>Workflow registration and management</li>
 *   <li>Step execution with proper error handling</li>
 *   <li>Suspension and resumption for Human-in-the-Loop</li>
 *   <li>Asynchronous step execution</li>
 *   <li>Integration with Spring DI (optional)</li>
 * </ul>
 */
@Slf4j
public class WorkflowEngine {

    private final Map<String, WorkflowGraph<?, ?>> registeredWorkflows = new ConcurrentHashMap<>();
    private final WorkflowStateRepository stateRepository;
    private final AsyncStepStateRepository asyncStepStateRepository;
    private final SuspensionDataRepository suspensionDataRepository;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    private final Map<String, WorkflowExecutionListener> listeners = new ConcurrentHashMap<>();
    private final ProgressTracker progressTracker;
    private final AsyncStepHandler asyncStepHandler;
    private final StepRouter stepRouter;
    private final WorkflowExecutor workflowExecutor;
    private final WorkflowOrchestrator orchestrator;
    private final WorkflowStateManager stateManager;
    private final AsyncTaskManager asyncTaskManager;
    @Getter
    private final ChatStore chatStore;

    /**
     * Creates a workflow engine with default configuration.
     */
    public WorkflowEngine() {
        this(WorkflowEngineConfig.defaultConfig());
    }

    /**
     * Creates a workflow engine with custom configuration.
     */
    public WorkflowEngine(WorkflowEngineConfig config) {
        // Initialize state repository
        this.stateRepository = config.getStateRepository() != null ?
                config.getStateRepository() : new InMemoryWorkflowStateRepository();

        
        // Initialize async step state repository
        this.asyncStepStateRepository = config.getAsyncStepStateRepository() != null ?
                config.getAsyncStepStateRepository() : new InMemoryAsyncStepStateRepository();
                
        // Initialize suspension data repository
        this.suspensionDataRepository = config.getSuspensionDataRepository() != null ?
                config.getSuspensionDataRepository() : new InMemorySuspensionDataRepository();

        // Initialize progress tracker
        this.progressTracker = config.getProgressTracker() != null ?
                config.getProgressTracker() : new InMemoryProgressTracker();
                
        // Initialize chat store (optional)
        this.chatStore = config.getChatStore();

        // Initialize async step handler
        this.asyncStepHandler = new AsyncStepHandler();
        
        // Initialize step router
        this.stepRouter = new DefaultStepRouter();
        
        // Initialize input preparer
        InputPreparer inputPreparer = new InputPreparer();
        
        // Initialize workflow executor
        this.workflowExecutor = new WorkflowExecutor(config, progressTracker, chatStore);
        
        // Add listener adapter as interceptor
        this.workflowExecutor.addInterceptor(new ListenerAdapterInterceptor());
        
        // Initialize state manager
        this.stateManager = new WorkflowStateManager(stateRepository);
        
        // Initialize orchestrator
        this.orchestrator = new WorkflowOrchestrator(
            stateManager,
            workflowExecutor,
            stepRouter,
            inputPreparer,
            suspensionDataRepository,
            chatStore
        );

        // Initialize thread pools
        this.executorService = createExecutorService(config);
        this.scheduledExecutor = Executors.newScheduledThreadPool(
                config.getScheduledThreads(),
                new NamedThreadFactory("workflow-scheduler")
        );
        
        // Initialize async task manager
        this.asyncTaskManager = new AsyncTaskManager(
            executorService,
            progressTracker,
            stateRepository,
            asyncStepHandler,
            asyncStepStateRepository
        );
        

        log.info("WorkflowEngine initialized with config: {}", config);
    }

    /**
     * Registers a workflow graph for execution.
     *
     * @param graph The workflow graph to register
     * @throws IllegalArgumentException if a workflow with the same ID already exists
     */
    public void register(WorkflowGraph<?, ?> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Workflow graph cannot be null");
        }

        String workflowId = graph.id();
        if (registeredWorkflows.containsKey(workflowId)) {
            throw new IllegalArgumentException(
                    "Workflow already registered: " + workflowId
            );
        }

        registeredWorkflows.put(workflowId, graph);

        // Register async steps if any
        asyncStepHandler.registerWorkflow(graph);

        log.info("Registered workflow: {} (version: {}, async steps: {})",
                workflowId, graph.version(),
                graph.asyncStepMetadata() != null ? graph.asyncStepMetadata().size() : 0);
    }

    /**
     * Registers a workflow instance by analyzing it.
     *
     * @param workflowInstance An instance of a class annotated with @Workflow
     */
    public void register(Object workflowInstance) {
        WorkflowGraph<?, ?> graph = WorkflowAnalyzer.analyze(workflowInstance);
        register(graph);
    }

    /**
     * Registers a workflow created with the builder API.
     * The builder workflow is converted to a WorkflowGraph and registered
     * with the same ID specified in the builder.
     * 
     * Example usage:
     * <pre>
     * Workflow<OrderRequest, OrderResult> orderWorkflow = Workflow
     *     .define("order-processing", OrderRequest.class, OrderResult.class)
     *     .then(StepDefinition.of(orderService::validateOrder))
     *     .then(StepDefinition.of(orderService::processPayment))
     *     .then(StepDefinition.of(orderService::shipOrder));
     * 
     * engine.register(orderWorkflow);
     * 
     * // Execute using the workflow ID from the builder
     * WorkflowExecution<OrderResult> execution = engine.execute("order-processing", orderRequest);
     * </pre>
     *
     * @param builderWorkflow The workflow created using the builder API
     */
    public void register(WorkflowBuilder<?, ?> builderWorkflow) {
        WorkflowGraph<?, ?> graph = WorkflowAnalyzer.analyzeBuilder(builderWorkflow);
        register(graph);
    }

    /**
     * Starts a new workflow execution.
     *
     * @param workflowId The ID of the workflow to execute
     * @param input The input data for the workflow
     * @return WorkflowExecution handle for tracking the execution
     */
    public <T, R> WorkflowExecution<R> execute(String workflowId, T input) {
        return execute(workflowId, input, UUID.randomUUID().toString());
    }
    
    /**
     * Starts a new workflow execution with chat context.
     *
     * @param workflowId The ID of the workflow to execute
     * @param input The input data for the workflow
     * @param instanceId The specific instance ID to use
     * @param chatId The chat ID to associate with this execution
     * @return WorkflowExecution handle for tracking the execution
     */
    public <T, R> WorkflowExecution<R> execute(String workflowId, T input, String instanceId, String chatId) {
        // Check if workflow instance already exists
        Optional<WorkflowInstance> existingInstance = stateRepository.load(instanceId);
        
        if (existingInstance.isPresent()) {
            WorkflowInstance instance = existingInstance.get();
            
            // If workflow is suspended, automatically resume it
            if (instance.getStatus() == WorkflowStatus.SUSPENDED) {
                log.info("Workflow instance {} is suspended, automatically resuming with provided input", instanceId);
                return resume(instanceId, input);
            }
            
            // If workflow is in any other state, throw exception
            throw new IllegalStateException(
                "Workflow instance already exists with ID: " + instanceId + 
                " in status: " + instance.getStatus() + 
                ". Cannot start new execution."
            );
        }
        
        // No existing instance, create new one with chatId
        WorkflowGraph<T, R> graph = getWorkflowGraph(workflowId);
        WorkflowInstance instance = WorkflowInstance.newInstance(graph, input, instanceId, chatId);

        stateRepository.save(instance);

        WorkflowExecution<R> execution = new WorkflowExecution<>(
                instance.getInstanceId(),
                workflowId,
                new CompletableFuture<>(),
                this
        );

        // Start execution asynchronously
        executorService.submit(() -> executeWorkflow(instance, graph, execution));

        return execution;
    }
    
    /**
     * Starts a new workflow execution with specific instance ID.
     * If a workflow with this instance ID already exists and is suspended,
     * automatically resumes it with the provided input.
     *
     * @param workflowId The ID of the workflow to execute
     * @param input The input data for the workflow
     * @param instanceId The specific instance ID to use
     * @return WorkflowExecution handle for tracking the execution
     */
    public <T, R> WorkflowExecution<R> execute(String workflowId, T input, String instanceId) {
        // Check if workflow instance already exists
        Optional<WorkflowInstance> existingInstance = stateRepository.load(instanceId);
        
        if (existingInstance.isPresent()) {
            WorkflowInstance instance = existingInstance.get();
            
            // If workflow is suspended, automatically resume it
            if (instance.getStatus() == WorkflowStatus.SUSPENDED) {
                log.info("Workflow instance {} is suspended, automatically resuming with provided input", instanceId);
                return resume(instanceId, input);
            }
            
            // If workflow is in any other state, throw exception
            throw new IllegalStateException(
                "Workflow instance already exists with ID: " + instanceId + 
                " in status: " + instance.getStatus() + 
                ". Cannot start new execution."
            );
        }
        
        // No existing instance, create new one
        WorkflowGraph<T, R> graph = getWorkflowGraph(workflowId);
        WorkflowInstance instance = WorkflowInstance.newInstance(graph, input, instanceId);

        stateRepository.save(instance);

        WorkflowExecution<R> execution = new WorkflowExecution<>(
                instance.getInstanceId(),
                workflowId,
                new CompletableFuture<>(),
                this
        );

        // Start execution asynchronously
        executorService.submit(() -> executeWorkflow(instance, graph, execution));

        return execution;
    }


    /**
     * Resumes a suspended workflow execution.
     *
     * @param runId The run ID of the suspended workflow
     * @param input The user input to resume with
     * @return WorkflowExecution handle for tracking the resumed execution
     */
    public <T, R> WorkflowExecution<R> resume(String runId, T input) {
        WorkflowInstance instance = stateRepository.load(runId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Workflow instance not found: " + runId
                ));

        if (instance.getStatus() != WorkflowStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "Workflow is not suspended: " + runId + " (status: " + instance.getStatus() + ")"
            );
        }

        WorkflowGraph<?, R> graph = getWorkflowGraph(instance.getWorkflowId());

        // Get the suspended step to find the original input type
        String suspendedStepId = instance.getCurrentStepId();

        // Get suspension data from repository
        SuspensionData suspensionData = suspensionDataRepository.findByInstanceId(runId).orElse(null);

        if (suspensionData != null && suspensionData.originalStepInput() != null) {
            // Store the original step input back in context for the step to use
            instance.updateContext(WorkflowContext.Keys.RESUMED_STEP_INPUT,
                    suspensionData.originalStepInput());
        }

        // Store the user input separately with its type information
        instance.updateContext(WorkflowContext.Keys.USER_INPUT, input);
        instance.updateContext(WorkflowContext.Keys.USER_INPUT_TYPE, input.getClass().getName());

        // Validate input type if specified in suspension data
        if (suspensionData != null && suspensionData.nextInputClass() != null) {
            Class<?> expectedInputType = suspensionData.nextInputClass();
            if (!expectedInputType.isInstance(input)) {
                throw new IllegalArgumentException(
                        "Resume input type mismatch: expected " + expectedInputType.getName() +
                                " but received " + input.getClass().getName()
                );
            }
            
            // When resuming, we need to find the next step after the suspended one
            // The suspended step already executed and returned Suspend result
            String nextStepId = stepRouter.findNextStep(graph, suspendedStepId, input);
            if (nextStepId != null) {
                instance.setCurrentStepId(nextStepId);
                log.debug("Resume: moving from suspended step {} to next step {} for input type {}",
                        suspendedStepId, nextStepId, input.getClass().getSimpleName());
            } else {
                // If no next step found by normal routing, try type-based routing
                nextStepId = stepRouter.findStepForInputType(graph, input.getClass(), suspendedStepId);
                if (nextStepId != null) {
                    instance.setCurrentStepId(nextStepId);
                    log.debug("Resume: found step {} that accepts input type {} using type-based routing",
                            nextStepId, input.getClass().getSimpleName());
                } else {
                    throw new IllegalStateException(
                            "No step found that can process resume input of type: " + 
                            input.getClass().getName()
                    );
                }
            }
        }

        instance.resume();
        
        // Auto-track user input in ChatStore if available
        if (chatStore != null && runId != null && input != null) {
            chatStore.add(runId, 
                SchemaUtils.extractProperties(input), 
                ChatMessage.MessageType.USER);
            log.debug("Auto-tracked user input to ChatStore for instance: {}", runId);
        }
        
        // Delete suspension data from repository
        suspensionDataRepository.deleteByInstanceId(runId);
        stateRepository.save(instance);

        WorkflowExecution<R> execution = new WorkflowExecution<>(
                instance.getInstanceId(),
                instance.getWorkflowId(),
                new CompletableFuture<>(),
                this
        );

        // Resume execution asynchronously
        executorService.submit(() -> executeWorkflow(instance, graph, execution));

        return execution;
    }

    /**
     * Main workflow execution loop.
     */
    @SuppressWarnings("unchecked")
    private <R> void executeWorkflow(WorkflowInstance instance,
                                     WorkflowGraph<?, R> graph,
                                     WorkflowExecution<R> execution) {
        notifyListeners(l -> l.onWorkflowStarted(instance));
        
        // Delegate to orchestrator
        orchestrator.orchestrateExecution(instance, graph, execution, this);
        
        // Handle notifications based on final state
        if (instance.getStatus() == WorkflowStatus.COMPLETED) {
            R finalResult = orchestrator.getFinalResult(instance, graph);
            notifyListeners(l -> l.onWorkflowCompleted(instance, finalResult));
        } else if (instance.getStatus() == WorkflowStatus.FAILED) {
            Throwable error = orchestrator.createErrorFromInfo(instance.getErrorInfo());
            notifyListeners(l -> l.onWorkflowFailed(instance, error));
        } else if (instance.getStatus() == WorkflowStatus.SUSPENDED) {
            log.debug("Workflow suspended: {} at step: {}",
                    instance.getInstanceId(), instance.getCurrentStepId());
            notifyListeners(l -> l.onWorkflowSuspended(instance));
        }
    }

    /**
     * Prepares input for a step execution.
     * Enhanced to prioritize most recent compatible input.
     * Note: This method is now only used internally and may be moved to InputPreparer in future.
     */
    private Object prepareStepInput(WorkflowInstance instance, StepNode step) {
        log.debug("Preparing input for step: {} (expected type: {})",
                step.id(),
                step.executor().getInputType() != null ? step.executor().getInputType().getSimpleName() : "any");

        // For initial step, use trigger data
        if (step.isInitial()) {
            return instance.getContext().getTriggerData();
        }

        WorkflowContext ctx = instance.getContext();
        Class<?> expectedInputType = step.executor().getInputType();

        // Priority 1: Check if we're resuming from suspension with user input
        if (ctx.hasStepResult(WorkflowContext.Keys.USER_INPUT)) {
            // Get the saved type information if available
            String userInputTypeName = ctx.getStepResultOrDefault(
                    WorkflowContext.Keys.USER_INPUT_TYPE, String.class, null);

            Object userInput = null;

            // Try to deserialize with the correct type if we have type information
            if (userInputTypeName != null && expectedInputType != null) {
                try {
                    Class<?> savedType = Class.forName(userInputTypeName);
                    // Only use the saved type if it's compatible with the expected type
                    if (expectedInputType.isAssignableFrom(savedType)) {
                        userInput = ctx.getStepResult(WorkflowContext.Keys.USER_INPUT, savedType);
                        log.debug("Deserialized user input with saved type {} for step {}",
                                savedType.getSimpleName(), step.id());
                    } else {
                        // Type mismatch - fall back to Object.class
                        userInput = ctx.getStepResult(WorkflowContext.Keys.USER_INPUT, Object.class);
                        log.warn("Saved type {} is not compatible with expected type {} for step {}",
                                savedType.getSimpleName(), expectedInputType.getSimpleName(), step.id());
                    }
                } catch (ClassNotFoundException e) {
                    log.warn("Could not load saved type class: {}, falling back to Object.class",
                            userInputTypeName);
                    userInput = ctx.getStepResult(WorkflowContext.Keys.USER_INPUT, Object.class);
                }
            } else {
                // No type information available - use Object.class as before
                userInput = ctx.getStepResult(WorkflowContext.Keys.USER_INPUT, Object.class);
            }

            if (userInput != null && step.canAcceptInput(userInput.getClass())) {
                log.debug("Using user input of type {} for step {}",
                        userInput.getClass().getSimpleName(), step.id());
                // Remove userInput and its type from context after use
                instance.updateContext(WorkflowContext.Keys.USER_INPUT, null);
                instance.updateContext(WorkflowContext.Keys.USER_INPUT_TYPE, null);
                return userInput;
            }
        }

        // Priority 2: Find the most recent compatible output from execution history
        List<WorkflowInstance.StepExecutionRecord> history = instance.getExecutionHistory();
        if (!history.isEmpty()) {
            // Traverse history from most recent to oldest
            for (int i = history.size() - 1; i >= 0; i--) {
                WorkflowInstance.StepExecutionRecord exec = history.get(i);

                // Skip if it's the current step
                if (exec.getStepId().equals(step.id())) {
                    continue;
                }

                // Skip if step output doesn't exist
                if (!ctx.hasStepResult(exec.getStepId())) {
                    continue;
                }

                // Get the step output with proper type information
                try {
                    StepOutput output = ctx.getStepOutputs().get(exec.getStepId());
                    if (output == null || !output.hasValue()) {
                        continue;
                    }
                    
                    // Check if this output matches the expected input type exactly
                    if (expectedInputType != null && expectedInputType != Object.class) {
                        if (output.getActualClass().equals(expectedInputType)) {
                            Object result = output.getValue();
                            log.debug("Using exact type match from step {} (type: {}) as input for step {}",
                                    exec.getStepId(), output.getActualClass().getSimpleName(), step.id());
                            return result;
                        }
                    }
                    
                    // Check type compatibility
                    if (step.canAcceptInput(output.getActualClass())) {
                        Object result = output.getValue();
                        log.debug("Using compatible output from step {} (type: {}) as input for step {}",
                                exec.getStepId(), output.getActualClass().getSimpleName(), step.id());
                        return result;
                    }
                } catch (Exception e) {
                    log.error("Error retrieving output from step {}", exec.getStepId(), e);
                    continue;
                }
            }
        }

        // Priority 3: Only check trigger data for initial steps
        // This prevents non-initial steps (like those in branches) from accidentally
        // getting the workflow's original input instead of the previous step's output
        if (step.isInitial()) {
            Object triggerData = ctx.getTriggerData();
            if (triggerData != null && step.canAcceptInput(triggerData.getClass())) {
                log.debug("Using trigger data of type {} for initial step {}",
                        triggerData.getClass().getSimpleName(), step.id());
                return triggerData;
            }
        }

        // No suitable input available
        log.error("No suitable input found for step {} (expected type: {})",
                step.id(),
                expectedInputType != null ? expectedInputType.getSimpleName() : "any");
        return null;
    }


    /**
     * Handles asynchronous step execution.
     * Modified to treat async as suspend-like with updateable results.
     * Package-private to allow WorkflowOrchestrator to call it.
     */
    <R> void handleAsyncStep(WorkflowInstance instance,
                             WorkflowGraph<?, R> graph,
                             StepNode currentStep,
                             StepResult.Async<?> async,
                             WorkflowExecution<R> execution) {
        String asyncTaskId = async.taskId();
        Object immediateData = async.immediateData();

        // Create structured async state and save to repository
        AsyncStepState asyncState = AsyncStepState.started(asyncTaskId, immediateData);
        asyncStepStateRepository.save(asyncState);
        
        // Store the messageId for this async step (not in context, just for tracking)
        final String messageId = asyncState.getMessageId();
        
        // Create suspension data with async state message ID
        SuspensionData suspensionData = SuspensionData.createWithMessageId(
            messageId,
            immediateData,  // The initial async data is the prompt to user
            Map.of("async", true, "taskId", asyncTaskId),
            async,  // Original async result
            currentStep.id(),
            null  // No next input class for async
        );

        // Suspend the workflow with suspension data
        instance.suspend();
        suspensionDataRepository.save(instance.getInstanceId(), suspensionData);
        stateRepository.save(instance);

        // Track the execution with initial event
        WorkflowEvent trackingEvent = WorkflowEvent.asyncStarted(asyncTaskId, "");
        progressTracker.trackExecution(asyncTaskId, trackingEvent);

        // Check if we should use AsyncTaskManager for CompletableFuture support
        if (async.taskArgs().containsKey(WorkflowContext.Keys.ASYNC_FUTURE)) {
            // Use AsyncTaskManager for CompletableFuture handling
            asyncTaskManager.handleAsyncStep(instance, graph, currentStep.id(), async)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Async task failed", error);
                        execution.future.completeExceptionally(error);
                    } else {
                        // Process the result
                        processAsyncResult(instance, graph, currentStep, result, execution);
                    }
                });
            return;
        }
        
        // Create async task supplier for traditional async handlers
        Supplier<Object> asyncTask = () -> {
            try {
                log.debug("Executing async task {} for step {}", asyncTaskId, currentStep.id());

                // Create progress reporter for async method
                AsyncProgressReporter progressReporter = new AsyncProgressReporter() {
                    @Override
                    public void updateProgress(int percentComplete, String message) {
                        // Update async state
                        // Update progress directly in repository
                        if (percentComplete >= 0) {
                            asyncStepStateRepository.updateProgress(messageId, percentComplete, message);
                        } else {
                            // Just update message - need to get current progress first
                            asyncStepStateRepository.findByMessageId(messageId).ifPresent(state -> {
                                asyncStepStateRepository.updateProgress(messageId, state.getPercentComplete(), message);
                            });
                        }
                        
                        // Update progress tracker
                        progressTracker.updateProgress(asyncTaskId, percentComplete >= 0 ? percentComplete : 0, message);
                    }

                    @Override
                    public boolean isCancelled() {
                        WorkflowInstance latestInstance = stateRepository.load(instance.getInstanceId())
                                .orElse(instance);

                        // Check if cancelled directly from repository
                        return asyncStepStateRepository.findByMessageId(messageId)
                                .map(state -> state.getStatus() == AsyncStepState.AsyncStatus.CANCELLED)
                                .orElse(false);
                    }
                };

                // Find the async step handler - try both taskId and stepId for compatibility
                StepResult<?> handlerResult = asyncStepHandler.handleAsyncResult(
                        graph,
                        asyncTaskId,  // First try task ID to match @AsyncStep value
                        currentStep.id(),  // Fall back to step ID if not found
                        async.taskArgs(),  // Pass taskArgs directly
                        instance.getContext(),
                        progressReporter
                );

                if (handlerResult != null) {
                    // Process the handler result in a new thread to avoid deadlock
                    try {
                        // Reload instance to get latest state
                        WorkflowInstance latestInstance = stateRepository.load(instance.getInstanceId())
                                .orElse(instance);

                        // Update async state with completion
                        asyncStepStateRepository.findByMessageId(messageId).ifPresent(state -> {
                            state.complete(handlerResult);
                            asyncStepStateRepository.save(state);
                            
                            WorkflowEvent completedEvent = WorkflowEvent.completed(Map.of(
                                "taskId", asyncTaskId,
                                "status", "completed"
                            ));
                            progressTracker.updateExecutionStatus(asyncTaskId, completedEvent);
                        });

                        // Process async handler result WITHOUT recursion
                        switch (handlerResult) {
                            case StepResult.Continue<?> cont -> {
                                // Store the async result
                                latestInstance.updateContext(currentStep.id(), cont.data());

                                // Resume workflow from suspended state
                                latestInstance.resume();

                                // Find next step
                                String nextStepId = stepRouter.findNextStep(graph, currentStep.id(), cont.data());
                                if (nextStepId != null) {
                                    latestInstance.setCurrentStepId(nextStepId);
                                    stateRepository.save(latestInstance);

                                    // Continue workflow execution
                                    if (!latestInstance.isTerminal()) {
                                        executeWorkflow(latestInstance, graph, execution);
                                    }
                                } else {
                                    log.warn("No next step found after async handler for: {}", currentStep.id());
                                    latestInstance.fail(new IllegalStateException("No next step after async " + currentStep.id()),
                                            currentStep.id());
                                    stateRepository.save(latestInstance);
                                    execution.future.completeExceptionally(
                                            new IllegalStateException("No next step after async " + currentStep.id()));
                                }
                            }

                            case StepResult.Finish<?> finish -> {
                                // Update final result and resume
                                latestInstance.updateContext(currentStep.id(), finish.result());
                                latestInstance.resume();

                                // Workflow completed
                                latestInstance.updateContext(WorkflowContext.Keys.FINAL_RESULT, finish.result());
                                latestInstance.updateStatus(WorkflowStatus.COMPLETED);
                                stateRepository.save(latestInstance);
                                // Get the typed result using the workflow's output type
                                R typedResult = latestInstance.getContext().getStepResult(
                                        WorkflowContext.Keys.FINAL_RESULT, graph.outputType());
                                execution.future.complete(typedResult);
                            }

                            case StepResult.Fail<?> fail -> {
                                // Resume to failed state
                                latestInstance.resume();

                                // Workflow failed
                                latestInstance.fail(fail.error(), currentStep.id());
                                stateRepository.save(latestInstance);
                                execution.future.completeExceptionally(fail.error());
                            }

                            case StepResult.Async<?> asyncResult -> {
                                // Async handler returned another async - this is not supported
                                throw new IllegalStateException(
                                        "Async handler cannot return another Async result: " + currentStep.id()
                                );
                            }

                            case StepResult.Suspend<?> susp -> {
                                // Suspend from async handler
                                SuspensionData asyncSuspensionData = SuspensionData.create(
                                        susp.promptToUser(),
                                        susp.metadata(),
                                        null,
                                        currentStep.id(),
                                        susp.nextInputClass()
                                );
                                latestInstance.suspend();
                                suspensionDataRepository.save(latestInstance.getInstanceId(), asyncSuspensionData);
                                stateRepository.save(latestInstance);
                            }

                            case StepResult.Branch<?> branch -> {
                                // Resume workflow
                                latestInstance.resume();
                                // Find branch target
                                String branchTarget = stepRouter.findBranchTarget(graph, currentStep.id(), branch.event());
                                if (branchTarget != null) {
                                    latestInstance.setCurrentStepId(branchTarget);
                                    latestInstance.updateContext(currentStep.id(), branch.event());
                                    stateRepository.save(latestInstance);

                                    // Continue execution
                                    if (!latestInstance.isTerminal()) {
                                        executeWorkflow(latestInstance, graph, execution);
                                    }
                                } else {
                                    throw new IllegalStateException(
                                            "No branch target found for event: " + branch.event().getClass()
                                    );
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error processing async handler result for step {}", currentStep.id(), e);
                        instance.fail(e, currentStep.id());
                        stateRepository.save(instance);
                        execution.future.completeExceptionally(e);
                    }
                }

                return handlerResult;
            } catch (Exception e) {
                log.error("Async task {} failed for step {}", asyncTaskId, currentStep.id(), e);
                throw new RuntimeException("Async execution failed", e);
            }
        };

        // Execute async task with progress tracking
        CompletableFuture<Object> future = progressTracker.executeAsync(
                asyncTaskId,
                trackingEvent,
                asyncTask
        );

        // Handle completion/error callbacks
        future.whenComplete((result, error) -> {
            // Reload instance to get latest state
            WorkflowInstance latestInstance = stateRepository.load(instance.getInstanceId())
                    .orElse(instance);

            if (error != null) {
                log.error("Async task {} completed with error", asyncTaskId, error);
                progressTracker.onError(asyncTaskId, error);

                // Update async state with error
                Optional<SuspensionData> errorSuspensionData = suspensionDataRepository.findByInstanceId(latestInstance.getInstanceId());
                String errorMessageId = errorSuspensionData.map(SuspensionData::messageId).orElse(null);
                if (errorMessageId != null) {
                    asyncStepStateRepository.findByMessageId(errorMessageId).ifPresent(state -> {
                        state.fail(error);
                        asyncStepStateRepository.save(state);
                    });
                }

                // Update instance state
                latestInstance.fail(error, currentStep.id());
                stateRepository.save(latestInstance);

                // Complete execution exceptionally
                execution.future.completeExceptionally(error);

                // Notify listeners
                notifyListeners(l -> l.onStepFailed(latestInstance, currentStep.id(), error));
            } else {
                log.debug("Async task {} completed successfully", asyncTaskId);
                progressTracker.onComplete(asyncTaskId, result);

                // Result is already handled in the async task itself
                // Just save the state
                stateRepository.save(latestInstance);
            }
        });

        // Notify listeners about async start (workflow is now suspended)
        notifyListeners(l -> l.onStepCompleted(instance, currentStep.id(), async));
        notifyListeners(l -> l.onWorkflowSuspended(instance));
    }

    /**
     * Process async result from CompletableFuture-based async operations.
     */
    private <R> void processAsyncResult(WorkflowInstance instance,
                                       WorkflowGraph<?, R> graph,
                                       StepNode currentStep,
                                       StepResult<?> result,
                                       WorkflowExecution<R> execution) {
        try {
            // Reload instance to get latest state
            WorkflowInstance latestInstance = stateRepository.load(instance.getInstanceId())
                    .orElse(instance);
            
            // Update async state with completion
            Optional<SuspensionData> completionSuspensionData = suspensionDataRepository.findByInstanceId(latestInstance.getInstanceId());
            String completionMessageId = completionSuspensionData.map(SuspensionData::messageId).orElse(null);
            if (completionMessageId != null) {
                asyncStepStateRepository.findByMessageId(completionMessageId).ifPresent(state -> {
                    state.complete(result);
                    asyncStepStateRepository.save(state);
                    
                    WorkflowEvent progressEvent = WorkflowEvent.withProgress(
                        state.getPercentComplete(),
                        state.getStatusMessage()
                    );
                    progressTracker.updateExecutionStatus(state.getTaskId(), progressEvent);
                });
            }
            
            // Process the result
            switch (result) {
                case StepResult.Continue<?> cont -> {
                    // Store the async result
                    latestInstance.updateContext(currentStep.id(), cont.data());
                    latestInstance.resume();
                    
                    // Find next step
                    String nextStepId = stepRouter.findNextStep(graph, currentStep.id(), cont.data());
                    if (nextStepId != null) {
                        latestInstance.setCurrentStepId(nextStepId);
                        stateRepository.save(latestInstance);
                        
                        // Continue execution
                        if (!latestInstance.isTerminal()) {
                            executeWorkflow(latestInstance, graph, execution);
                        }
                    } else {
                        throw new IllegalStateException("No next step after async " + currentStep.id());
                    }
                }
                case StepResult.Finish<?> fin -> {
                    // Update final result and mark as completed
                    latestInstance.updateContext(WorkflowContext.Keys.FINAL_RESULT, fin.result());
                    latestInstance.updateStatus(WorkflowInstance.WorkflowStatus.COMPLETED);
                    stateRepository.save(latestInstance);
                    execution.future.complete((R) fin.result());
                }
                case StepResult.Fail<?> fail -> {
                    latestInstance.fail(fail.error(), currentStep.id());
                    stateRepository.save(latestInstance);
                    execution.future.completeExceptionally(fail.error());
                }
                case StepResult.Suspend<?> susp -> {
                    // Store suspension data
                    latestInstance.updateContext(WorkflowContext.Keys.USER_INPUT, susp.promptToUser());
                    stateRepository.save(latestInstance);
                    notifyListeners(l -> l.onWorkflowSuspended(latestInstance));
                }
                case StepResult.Branch<?> branch -> {
                    String branchTarget = stepRouter.findBranchTarget(graph, currentStep.id(), branch.event());
                    if (branchTarget != null) {
                        latestInstance.setCurrentStepId(branchTarget);
                        latestInstance.updateContext(currentStep.id(), branch.event());
                        latestInstance.resume();
                        stateRepository.save(latestInstance);
                        
                        // Continue execution
                        if (!latestInstance.isTerminal()) {
                            executeWorkflow(latestInstance, graph, execution);
                        }
                    } else {
                        throw new IllegalStateException(
                                "No branch target found for event: " + branch.event().getClass()
                        );
                    }
                }
                default -> throw new IllegalStateException("Unknown step result type: " + result.getClass());
            }
        } catch (Exception e) {
            log.error("Error processing async result for step {}", currentStep.id(), e);
            instance.fail(e, currentStep.id());
            stateRepository.save(instance);
            execution.future.completeExceptionally(e);
        }
    }

    /**
     * Registers a workflow execution listener.
     */
    public void addListener(String listenerId, WorkflowExecutionListener listener) {
        listeners.put(listenerId, listener);
    }

    /**
     * Removes a workflow execution listener.
     */
    public void removeListener(String listenerId) {
        listeners.remove(listenerId);
    }

    /**
     * Notifies all registered listeners.
     */
    private void notifyListeners(Consumer<WorkflowExecutionListener> action) {
        listeners.values().forEach(listener -> {
            try {
                action.accept(listener);
            } catch (Exception e) {
                log.error("Error notifying listener", e);
            }
        });
    }

    /**
     * Shuts down the workflow engine.
     */
    public void shutdown() {
        log.info("Shutting down workflow engine...");

        executorService.shutdown();
        scheduledExecutor.shutdown();

        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("Workflow engine shut down");
    }

    /**
     * Creates the main executor service based on configuration.
     */
    private ExecutorService createExecutorService(WorkflowEngineConfig config) {
        return new ThreadPoolExecutor(
                config.getCoreThreads(),
                config.getMaxThreads(),
                300_000,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(config.getQueueCapacity()),
                new NamedThreadFactory("workflow-executor"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * Handle for tracking workflow execution.
     */
    @Getter
    public static class WorkflowExecution<R> {
        private final String runId;
        private final String workflowId;
        private final CompletableFuture<R> future;
        private final WorkflowEngine engine;

        WorkflowExecution(String runId, String workflowId, CompletableFuture<R> future, WorkflowEngine engine) {
            this.runId = runId;
            this.workflowId = workflowId;
            this.future = future;
            this.engine = engine;
        }

        public R get() throws InterruptedException, ExecutionException {
            return future.get();
        }

        public R get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return future.get(timeout, unit);
        }

        public R getResult() throws InterruptedException, ExecutionException {
            return future.get();
        }

        public boolean isDone() {
            return future.isDone();
        }

        public boolean isCancelled() {
            return future.isCancelled();
        }

        public boolean isAsync() {
            return !future.isDone();
        }
        
        /**
         * Check if the workflow is currently suspended.
         */
        public boolean isSuspended() {
            return engine.getWorkflowInstance(runId)
                    .map(instance -> instance.getStatus() == WorkflowInstance.WorkflowStatus.SUSPENDED)
                    .orElse(false);
        }
        
        /**
         * Check if the workflow has completed successfully.
         */
        public boolean isCompleted() {
            return engine.getWorkflowInstance(runId)
                    .map(instance -> instance.getStatus() == WorkflowInstance.WorkflowStatus.COMPLETED)
                    .orElse(false);
        }
        
        /**
         * Get the current workflow instance state.
         */
        public Optional<WorkflowInstance> getInstance() {
            return engine.getWorkflowInstance(runId);
        }
    }

    /**
     * Interface for workflow execution lifecycle events.
     */
    public interface WorkflowExecutionListener {
        default void onWorkflowStarted(WorkflowInstance instance) {}
        default void onWorkflowCompleted(WorkflowInstance instance, Object result) {}
        default void onWorkflowFailed(WorkflowInstance instance, Throwable error) {}
        default void onWorkflowSuspended(WorkflowInstance instance) {}
        default void onStepStarted(WorkflowInstance instance, String stepId) {}
        default void onStepCompleted(WorkflowInstance instance, String stepId, StepResult<?> result) {}
        default void onStepFailed(WorkflowInstance instance, String stepId, Throwable error) {}
    }

    /**
     * Custom thread factory for named threads.
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final ThreadFactory delegate = Executors.defaultThreadFactory();
        private int counter = 0;

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = delegate.newThread(r);
            thread.setName(prefix + "-" + counter++);
            return thread;
        }
    }

    /**
     * Exception thrown when a step times out.
     */
    public static class StepTimeoutException extends RuntimeException {
        public StepTimeoutException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown during workflow execution.
     */
    public static class WorkflowExecutionException extends RuntimeException {
        private final WorkflowInstance.ErrorInfo errorInfo;

        public WorkflowExecutionException(String message, WorkflowInstance.ErrorInfo errorInfo) {
            super(message);
            this.errorInfo = errorInfo;
        }

        public WorkflowInstance.ErrorInfo getErrorInfo() {
            return errorInfo;
        }
    }

    /**
     * Gets the workflow graph for a given workflow ID.
     * Public method for external access.
     */
    @SuppressWarnings("unchecked")
    public <T, R> WorkflowGraph<T, R> getWorkflowGraph(String workflowId) {
        WorkflowGraph<?, ?> graph = registeredWorkflows.get(workflowId);
        if (graph == null) {
            return null;
        }
        return (WorkflowGraph<T, R>) graph;
    }

    /**
     * Gets all registered workflow IDs.
     */
    public Set<String> getRegisteredWorkflows() {
        return new HashSet<>(registeredWorkflows.keySet());
    }
    
    /**
     * Gets the retry executor used by this engine.
     * Useful for accessing metrics and configuring listeners.
     * 
     * @return The retry executor
     */
    public RetryExecutor getRetryExecutor() {
        return workflowExecutor.getRetryExecutor();
    }

    /**
     * Gets the current result of a workflow execution.
     * For async steps, returns the latest progress update.
     *
     * @param instanceId The workflow instance ID
     * @return The current workflow result/event if available
     */
    public Optional<WorkflowEvent> getCurrentResult(String instanceId) {
        return stateRepository.load(instanceId)
                .map(instance -> {
                    // First check if current step has async state
                    Optional<AsyncStepState> asyncState = Optional.empty();
                    Optional<SuspensionData> suspensionData = suspensionDataRepository.findByInstanceId(instanceId);
                    if (suspensionData.isPresent()) {
                        asyncState = asyncStepStateRepository.findByMessageId(
                            suspensionData.get().messageId()
                        );
                    }
                    if (asyncState.isPresent()) {
                        AsyncStepState state = asyncState.get();

                        // Get latest progress from progress tracker
                        progressTracker.getProgress(state.getTaskId()).ifPresent(progress -> {
                            state.updateProgress(
                                    progress.percentComplete(),
                                    progress.message()
                            );
                        });

                        // Create event from current state
                        return WorkflowEvent.withProgress(
                            state.getPercentComplete(),
                            state.getStatusMessage()
                        );
                    }

                    // If suspended (non-async), return the suspension prompt as completed event
                    if (instance.getStatus() == WorkflowStatus.SUSPENDED &&
                            suspensionData.isPresent()) {
                        SuspensionData suspension = suspensionData.get();

                        // Suspension prompt is already a structured object, return it as-is
                        // The promptToUser should be a proper domain object (e.g., WorkflowEvent)
                        if (suspension.promptToUser() instanceof WorkflowEvent) {
                            return (WorkflowEvent) suspension.promptToUser();
                        }

                        // If not WorkflowEvent, wrap in a completed event
                        Map<String, String> props = new HashMap<>();
                        props.put("type", "suspension");
                        props.put("waitingForInput", "true");
                        return WorkflowEvent.completed(props);
                    }

                    // Return workflow status
                    return WorkflowEvent.withProgress(
                            instance.isTerminal() ? 100 : 0,
                            "Workflow " + instance.getStatus().toString().toLowerCase()
                    );
                });
    }

    /**
     * Gets the workflow instance state.
     *
     * @param instanceId The workflow instance ID
     * @return The workflow instance if found
     */
    public Optional<WorkflowInstance> getWorkflowInstance(String instanceId) {
        return stateRepository.load(instanceId);
    }

    /**
     * Cancels an async operation if it's running.
     *
     * @param instanceId The workflow instance ID
     * @return true if the operation was cancelled, false if not found or not async
     */
    public boolean cancelAsyncOperation(String instanceId) {
        Optional<WorkflowInstance> instanceOpt = stateRepository.load(instanceId);
        if (instanceOpt.isEmpty()) {
            return false;
        }

        WorkflowInstance instance = instanceOpt.get();
        Optional<AsyncStepState> asyncState = Optional.empty();
        Optional<SuspensionData> suspensionData = suspensionDataRepository.findByInstanceId(instanceId);
        if (suspensionData.isPresent()) {
            asyncState = asyncStepStateRepository.findByMessageId(
                suspensionData.get().messageId()
            );
        }

        if (asyncState.isPresent() && asyncState.get().isRunning()) {
            AsyncStepState state = asyncState.get();

            // Cancel the async operation
            state.cancel();

            // Resume workflow to failed state
            instance.resume();
            instance.fail(new RuntimeException("Async operation cancelled"), instance.getCurrentStepId());
            suspensionDataRepository.deleteByInstanceId(instanceId);
            stateRepository.save(instance);

            // Notify progress tracker
            progressTracker.onError(state.getTaskId(), new RuntimeException("Cancelled"));

            return true;
        }

        return false;
    }
    
    /**
     * Adapter that bridges ExecutionInterceptor to WorkflowExecutionListener.
     */
    private class ListenerAdapterInterceptor implements ExecutionInterceptor {
        @Override
        public void beforeStep(WorkflowInstance instance, StepNode step, Object input) {
            notifyListeners(l -> l.onStepStarted(instance, step.id()));
        }
        
        @Override
        public void afterStep(WorkflowInstance instance, StepNode step, StepResult<?> result) {
            notifyListeners(l -> l.onStepCompleted(instance, step.id(), result));
        }
        
        @Override
        public void onStepError(WorkflowInstance instance, StepNode step, Exception error) {
            notifyListeners(l -> l.onStepFailed(instance, step.id(), error));
        }
    }
}