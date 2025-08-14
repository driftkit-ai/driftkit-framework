package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.domain.SuspensionData;
import ai.driftkit.workflow.engine.domain.WorkflowException;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.SuspensionDataRepository;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance.WorkflowStatus;
import ai.driftkit.workflow.engine.schema.SchemaProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the execution of workflows by coordinating between various components.
 * This class contains the main workflow execution logic that was previously in WorkflowEngine.
 */
@Slf4j
@RequiredArgsConstructor
public class WorkflowOrchestrator {
    
    private final WorkflowStateManager stateManager;
    private final WorkflowExecutor executor;
    private final StepRouter router;
    private final InputPreparer inputPreparer;
    private final SuspensionDataRepository suspensionDataRepository;
    private final SchemaProvider schemaProvider;
    
    /**
     * Orchestrates the execution of a workflow instance.
     * 
     * @param instance The workflow instance to execute
     * @param graph The workflow graph definition
     * @param execution The execution handle for completion notification
     * @param engine The workflow engine reference for async callbacks
     * @param <R> The return type of the workflow
     */
    public <R> void orchestrateExecution(WorkflowInstance instance,
                                       WorkflowGraph<?, R> graph,
                                       WorkflowEngine.WorkflowExecution<R> execution,
                                       WorkflowEngine engine) {
        log.debug("Starting workflow execution: {} (instance: {})",
                graph.id(), instance.getInstanceId());

        try {
            while (!instance.isTerminal() &&
                    instance.getStatus() != WorkflowStatus.SUSPENDED) {

                String currentStepId = instance.getCurrentStepId();
                StepNode currentStep = graph.getNode(currentStepId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Step not found in graph: " + currentStepId
                        ));

                // Execute the current step
                StepResult<?> result = executor.executeStep(instance, currentStep, graph);

                // Process the result
                processStepResult(instance, graph, currentStep, result, execution, engine);
            }

            // Handle terminal states
            if (instance.getStatus() == WorkflowStatus.COMPLETED) {
                WorkflowContext ctx = instance.getContext();
                log.debug("Workflow completed. Context has {} step results, contains __final__: {}",
                        ctx.getStepCount(), ctx.hasStepResult(WorkflowContext.Keys.FINAL_RESULT));

                if (ctx.hasStepResult(WorkflowContext.Keys.FINAL_RESULT)) {
                    R result = ctx.getStepResult(WorkflowContext.Keys.FINAL_RESULT, graph.outputType());
                    log.debug("Completing future with final result: {}", result);
                    execution.getFuture().complete(result);
                } else {
                    log.warn("Workflow completed but no final result set. Completing with null.");
                    execution.getFuture().complete(null);
                }
            }

            if (instance.getStatus() == WorkflowStatus.FAILED) {
                WorkflowInstance.ErrorInfo errorInfo = instance.getErrorInfo();
                Throwable error;
                if (errorInfo != null) {
                    error = new WorkflowException(
                        errorInfo.errorMessage(),
                        new Exception(errorInfo.errorType())
                    );
                } else {
                    error = new WorkflowException("Workflow failed without specific error");
                }
                execution.getFuture().completeExceptionally(error);
            }

            // Suspended workflows don't complete the future - they wait for resume

        } catch (Exception e) {
            log.error("Workflow execution failed for instance: {}", instance.getInstanceId(), e);
            stateManager.failInstance(instance, e, instance.getCurrentStepId());
            execution.getFuture().completeExceptionally(e);
        }
    }
    
    /**
     * Processes the result of a step execution.
     *
     * @param instance The workflow instance
     * @param graph The workflow graph
     * @param currentStep The step that was just executed
     * @param result The result of the step execution
     * @param execution The execution handle
     * @param engine The workflow engine reference for async handling
     * @param <R> The return type of the workflow
     */
    public <R> void processStepResult(WorkflowInstance instance,
                                    WorkflowGraph<?, R> graph,
                                    StepNode currentStep,
                                    StepResult<?> result,
                                    WorkflowEngine.WorkflowExecution<R> execution,
                                    WorkflowEngine engine) {
        switch (result) {
            case StepResult.Continue<?> cont -> {
                // Store the output
                instance.updateContext(currentStep.id(), cont.data());

                // Find next step using enhanced type-based resolution
                String nextStepId = router.findNextStep(graph, currentStep.id(), cont.data());
                if (nextStepId != null) {
                    instance.setCurrentStepId(nextStepId);
                    stateManager.saveInstance(instance);
                } else {
                    log.warn("No next step found for Continue from: {} (data type: {})",
                            currentStep.id(),
                            cont.data() != null ? cont.data().getClass().getSimpleName() : "null");
                    instance.fail(new IllegalStateException(
                                    "No next step found after " + currentStep.id() +
                                            " for data type: " + (cont.data() != null ? cont.data().getClass().getName() : "null")),
                            currentStep.id());
                    stateManager.saveInstance(instance);
                }
            }

            case StepResult.Suspend<?> susp -> {
                // Get the step input that was passed to this step
                Object stepInput = inputPreparer.prepareStepInput(instance, currentStep);

                // Generate schema for next input class to register it
                if (susp.nextInputClass() != null) {
                    schemaProvider.generateSchema(susp.nextInputClass());
                    String schemaId = schemaProvider.getSchemaId(susp.nextInputClass());
                    log.debug("Registered schema for class {} with ID: {}", 
                        susp.nextInputClass().getName(), schemaId);
                }

                // Create suspension data with type preservation
                SuspensionData suspensionData = SuspensionData.create(
                        susp.promptToUser(),
                        susp.metadata(),
                        stepInput,
                        currentStep.id(),
                        susp.nextInputClass()
                );

                // Suspend the workflow
                instance.suspend();
                
                // Save suspension data to repository
                suspensionDataRepository.save(instance.getInstanceId(), suspensionData);

                // Store the result from this step as it may contain data to return to user
                instance.updateContext(currentStep.id(), susp.promptToUser());

                stateManager.saveInstance(instance);
            }

            case StepResult.Branch<?> branch -> {
                // Find next step based on event type
                String nextStepId = router.findBranchTarget(graph, currentStep.id(), branch.event());
                if (nextStepId != null) {
                    instance.setCurrentStepId(nextStepId);
                    // Don't store the branch event - let the next step use the previous step's output
                    // The branch event is only for routing, not for data flow
                    stateManager.saveInstance(instance);
                } else {
                    throw new IllegalStateException(
                            "No branch target found for event type: " +
                                    branch.event().getClass().getName()
                    );
                }
            }

            case StepResult.Finish<?> finish -> {
                // Workflow completed successfully
                log.debug("Processing Finish result with value: {}", finish.result());
                instance.updateContext(WorkflowContext.Keys.FINAL_RESULT, finish.result());
                instance.updateStatus(WorkflowStatus.COMPLETED);
                stateManager.saveInstance(instance);
                log.debug("Workflow completed, final result stored under __final__");
            }

            case StepResult.Fail<?> fail -> {
                // Workflow failed
                instance.fail(fail.error(), currentStep.id());
                stateManager.saveInstance(instance);
            }
            
            case StepResult.Async<?> async -> {
                // Delegate to engine's async handling method
                engine.handleAsyncStep(instance, graph, currentStep, async, execution);
            }
        }
    }
    
    /**
     * Gets the final result from a completed workflow instance.
     * 
     * @param instance The workflow instance
     * @param graph The workflow graph
     * @return The final result or null if not available
     */
    public <R> R getFinalResult(WorkflowInstance instance, WorkflowGraph<?, R> graph) {
        if (instance.getStatus() == WorkflowStatus.COMPLETED) {
            WorkflowContext ctx = instance.getContext();
            if (ctx.hasStepResult(WorkflowContext.Keys.FINAL_RESULT)) {
                return ctx.getStepResult(WorkflowContext.Keys.FINAL_RESULT, graph.outputType());
            }
        }
        return null;
    }
    
    /**
     * Creates a Throwable from workflow error info.
     * 
     * @param errorInfo The error info from workflow instance
     * @return A Throwable representing the error
     */
    public Throwable createErrorFromInfo(WorkflowInstance.ErrorInfo errorInfo) {
        if (errorInfo != null) {
            return new WorkflowException(
                errorInfo.errorMessage(),
                new Exception(errorInfo.errorType())
            );
        } else {
            return new WorkflowException("Workflow failed without specific error");
        }
    }
}