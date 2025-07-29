package ai.driftkit.workflows.core.service;

import ai.driftkit.workflows.core.domain.ExecutableWorkflow;
import ai.driftkit.workflows.core.domain.StartEvent;
import ai.driftkit.workflows.core.domain.StopEvent;
import ai.driftkit.workflows.core.domain.WorkflowContext;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A registry for all available workflows in the system.
 * This provides a centralized place to register and retrieve workflows.
 */
public class WorkflowRegistry {
    
    private static final Map<String, RegisteredWorkflow> registeredWorkflows = new HashMap<>();

    /**
     * Register a new workflow.
     * 
     * @param id The unique identifier for the workflow
     * @param name A human-readable name for the workflow
     * @param description A brief description of what the workflow does
     * @param workflow The executable workflow instance
     */
    public static void registerWorkflow(String id, String name, String description, Object workflow) {
        if (!(workflow instanceof ExecutableWorkflow)) {
            throw new IllegalArgumentException("Workflow must be an instance of ExecutableWorkflow");
        }
        
        RegisteredWorkflow registeredWorkflow = new RegisteredWorkflow();
        registeredWorkflow.setId(id);
        registeredWorkflow.setName(name);
        registeredWorkflow.setDescription(description);
        registeredWorkflow.setWorkflow((ExecutableWorkflow<?,?>) workflow);
        
        registeredWorkflows.put(id, registeredWorkflow);
    }

    /**
     * Get a workflow by its ID.
     * 
     * @param id The workflow ID
     * @return The registered workflow or null if not found
     */
    public static RegisteredWorkflow getWorkflow(String id) {
        return registeredWorkflows.get(id);
    }

    /**
     * Get all registered workflows.
     * 
     * @return A list of all registered workflows
     */
    public static List<RegisteredWorkflow> getAllWorkflows() {
        return new ArrayList<>(registeredWorkflows.values());
    }

    /**
     * Check if a workflow is registered with the given ID.
     * 
     * @param id The workflow ID to check
     * @return true if the workflow is registered, false otherwise
     */
    public static boolean hasWorkflow(String id) {
        return registeredWorkflows.containsKey(id);
    }
    
    /**
     * Execute a workflow by its ID.
     * 
     * @param workflowId The ID of the workflow to execute
     * @param startEvent The start event to pass to the workflow
     * @param workflowContext The workflow context
     * @return The result of the workflow execution
     * @throws Exception If the workflow execution fails
     */
    @SuppressWarnings("unchecked")
    public static <T, E extends StartEvent> StopEvent<T> executeWorkflow(String workflowId, E startEvent, WorkflowContext workflowContext) throws Exception {
        RegisteredWorkflow registeredWorkflow = getWorkflow(workflowId);
        if (registeredWorkflow == null) {
            throw new IllegalArgumentException("No workflow registered with ID: " + workflowId);
        }
        
        ExecutableWorkflow<E, T> workflow = (ExecutableWorkflow<E, T>) registeredWorkflow.getWorkflow();
        return workflow.execute(startEvent, workflowContext);
    }

    /**
     * Represents a registered workflow in the system.
     */
    @Data
    public static class RegisteredWorkflow {
        private String id;
        private String name;
        private String description;
        private ExecutableWorkflow<?, ?> workflow;
        
        // Hide the actual workflow instance from serialization
        public ExecutableWorkflow<?, ?> getWorkflow() {
            return workflow;
        }
        
        // For JSON serialization
        public Map<String, String> toMap() {
            Map<String, String> map = new HashMap<>();
            map.put("id", id);
            map.put("name", name);
            map.put("description", description);
            return map;
        }
    }
}