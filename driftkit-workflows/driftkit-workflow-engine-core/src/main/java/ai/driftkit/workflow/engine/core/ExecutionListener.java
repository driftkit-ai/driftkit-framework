package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.domain.WorkflowEvent;

/**
 * Listener interface for workflow execution events.
 * Implementations can react to workflow state changes, progress updates, etc.
 */
@FunctionalInterface
public interface ExecutionListener {
    
    /**
     * Called when a workflow event occurs.
     * 
     * @param event The workflow event
     */
    void onEvent(WorkflowEvent event);
}