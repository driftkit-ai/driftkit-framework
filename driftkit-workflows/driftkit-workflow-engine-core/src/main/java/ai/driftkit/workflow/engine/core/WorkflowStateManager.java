package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.persistence.WorkflowStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;

/**
 * Manages workflow instance state and persistence.
 * This component handles creation, loading, saving, and state transitions
 * of workflow instances.
 */
@Slf4j
@RequiredArgsConstructor
public class WorkflowStateManager {
    
    private final WorkflowStateRepository stateRepository;
    
    /**
     * Creates a new workflow instance for a fresh run.
     */
    public WorkflowInstance createInstance(String workflowId, 
                                         String version, 
                                         Object input, 
                                         Map<String, Object> initialContext,
                                         String initialStepId) {
        // Create a temporary context with initial data
        WorkflowContext context = WorkflowContext.newRun(input);
        if (initialContext != null) {
            context.setStepOutputs(initialContext);
        }
        
        // Create instance manually since we don't have the full graph
        WorkflowInstance instance = WorkflowInstance.builder()
            .instanceId(context.getRunId())
            .workflowId(workflowId)
            .workflowVersion(version)
            .context(context)
            .status(WorkflowInstance.WorkflowStatus.RUNNING)
            .currentStepId(initialStepId)
            .createdAt(System.currentTimeMillis())
            .updatedAt(System.currentTimeMillis())
            .build();
            
        stateRepository.save(instance);
        log.debug("Created new workflow instance: {} for workflow: {}", 
            instance.getInstanceId(), workflowId);
        return instance;
    }
    
    /**
     * Loads an existing workflow instance.
     */
    public Optional<WorkflowInstance> loadInstance(String runId) {
        return stateRepository.load(runId);
    }
    
    /**
     * Saves the current state of a workflow instance.
     */
    public void saveInstance(WorkflowInstance instance) {
        stateRepository.save(instance);
    }
    
    /**
     * Updates instance after successful step execution.
     */
    public void updateAfterStepExecution(WorkflowInstance instance, 
                                       StepNode step, 
                                       StepResult<?> result) {
        // Update context with step output (for Continue results)
        if (result instanceof StepResult.Continue<?> cont) {
            instance.updateContext(step.id(), cont.data());
        } else if (result instanceof StepResult.Suspend<?> susp) {
            // Store the suspend prompt as step result
            instance.updateContext(step.id(), susp.promptToUser());
        } else if (result instanceof StepResult.Branch<?> branch) {
            // Store the branch event
            instance.updateContext(step.id(), branch.event());
        }
        
        // Save state after update
        saveInstance(instance);
    }
    
    /**
     * Suspends a workflow instance.
     */
    public void suspendInstance(WorkflowInstance instance) {
        instance.suspend();
        saveInstance(instance);
        log.debug("Workflow suspended: {} at step: {}", 
            instance.getInstanceId(), instance.getCurrentStepId());
    }
    
    /**
     * Resumes a suspended workflow instance.
     */
    public void resumeInstance(WorkflowInstance instance, Object userInput) {
        if (instance.getStatus() != WorkflowInstance.WorkflowStatus.SUSPENDED) {
            throw new IllegalStateException(
                "Cannot resume workflow that is not suspended: " + instance.getInstanceId()
            );
        }
        
        // Store user input in context
        if (userInput != null) {
            instance.updateContext(WorkflowContext.Keys.USER_INPUT, userInput);
            instance.updateContext(WorkflowContext.Keys.USER_INPUT_TYPE, 
                userInput.getClass().getName());
        }
        
        instance.resume();
        saveInstance(instance);
        log.debug("Workflow resumed: {}", instance.getInstanceId());
    }
    
    /**
     * Marks workflow as completed.
     */
    public void completeInstance(WorkflowInstance instance, Object finalResult) {
        instance.updateContext(WorkflowContext.Keys.FINAL_RESULT, finalResult);
        instance.updateStatus(WorkflowInstance.WorkflowStatus.COMPLETED);
        saveInstance(instance);
        log.debug("Workflow completed: {} with result type: {}", 
            instance.getInstanceId(), 
            finalResult != null ? finalResult.getClass().getSimpleName() : "null");
    }
    
    /**
     * Marks workflow as failed.
     */
    public void failInstance(WorkflowInstance instance, Throwable error, String stepId) {
        instance.fail(error, stepId);
        saveInstance(instance);
        log.error("Workflow failed: {} at step: {}", instance.getInstanceId(), stepId, error);
    }
    
    /**
     * Updates the current step of the workflow.
     */
    public void updateCurrentStep(WorkflowInstance instance, String stepId) {
        instance.setCurrentStepId(stepId);
        saveInstance(instance);
    }
    
    
    /**
     * Finds a step that can handle the given input type after suspension.
     */
    public Optional<String> findStepForResumeInput(WorkflowInstance instance,
                                                  Class<?> inputType,
                                                  WorkflowGraph<?, ?> graph) {
        String currentStepId = instance.getCurrentStepId();
        
        // Check outgoing edges first
        var edges = graph.getOutgoingEdges(currentStepId);
        for (var edge : edges) {
            var targetStep = graph.getNode(edge.toStepId());
            if (targetStep.isPresent() && targetStep.get().canAcceptInput(inputType)) {
                return Optional.of(edge.toStepId());
            }
        }
        
        // Search all nodes if no direct edge found
        for (var node : graph.nodes().values()) {
            if (!node.id().equals(currentStepId) && node.canAcceptInput(inputType)) {
                return Optional.of(node.id());
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Marks workflow as failed.
     * Alias for failInstance for consistency with WorkflowEngine.
     */
    public void markFailed(WorkflowInstance instance, Throwable error, String stepId) {
        failInstance(instance, error, stepId);
    }
    
    /**
     * Cancels a workflow instance.
     * 
     * @param instance The workflow instance to cancel
     */
    public void cancelInstance(WorkflowInstance instance) {
        instance.updateStatus(WorkflowInstance.WorkflowStatus.CANCELLED);
        stateRepository.save(instance);
        log.info("Cancelled workflow instance: {}", instance.getInstanceId());
    }
}