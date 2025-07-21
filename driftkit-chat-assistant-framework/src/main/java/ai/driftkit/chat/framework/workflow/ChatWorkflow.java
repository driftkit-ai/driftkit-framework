package ai.driftkit.chat.framework.workflow;

import ai.driftkit.chat.framework.model.ChatDomain.ChatRequest;
import ai.driftkit.chat.framework.model.ChatDomain.ChatResponse;

import java.util.Map;

/**
 * Interface for chat-based workflows.
 * This interface is now a marker interface that extends AnnotatedWorkflow.
 * All implementations should extend AnnotatedWorkflow and implement this interface.
 */
public interface ChatWorkflow {
    /**
     * Get the unique identifier for this workflow
     */
    String getWorkflowId();
    
    /**
     * Check if this workflow is suitable for handling the given input
     * 
     * @param message The message from the user
     * @param properties Additional properties
     * @return true if the workflow can handle this input
     */
    boolean canHandle(String message, Map<String, String> properties);
    
    /**
     * Process a chat request and return a response.
     * 
     * @param request The chat request to process
     * @return A chat response
     */
    ChatResponse processChat(ChatRequest request);
}