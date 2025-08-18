package ai.driftkit.workflow.engine.service;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.chat.ChatRequest;
import ai.driftkit.common.domain.chat.ChatResponse;
import ai.driftkit.workflow.engine.domain.PageRequest;
import ai.driftkit.workflow.engine.domain.PageResult;
import ai.driftkit.workflow.engine.chat.ChatMessageTask;
import ai.driftkit.workflow.engine.domain.ChatSession;
import ai.driftkit.workflow.engine.domain.StepMetadata;
import ai.driftkit.workflow.engine.domain.WorkflowDetails;
import ai.driftkit.workflow.engine.domain.WorkflowMetadata;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;

import java.util.List;
import java.util.Optional;

/**
 * Core workflow execution service interface.
 * Provides workflow execution and chat management capabilities.
 */
public interface WorkflowExecutionService {
    
    // ========== Chat Workflow Execution ==========
    
    /**
     * Execute a chat request through the workflow engine.
     * The workflowId is obtained from the request.
     * 
     * @param request The chat request containing workflowId and other parameters
     * @return The chat response
     */
    ChatResponse executeChat(ChatRequest request);
    
    /**
     * Resume a suspended workflow with user input.
     * 
     * @param messageId The message ID of the suspended workflow
     * @param request The chat request with user input
     * @return The chat response
     */
    ChatResponse resumeChat(String messageId, ChatRequest request);
    
    /**
     * Get the status of an async workflow execution.
     * 
     * @param messageId The message ID to check
     * @return The current chat response status
     */
    Optional<ChatResponse> getAsyncStatus(String messageId);
    
    // ========== Session Management ==========
    
    /**
     * Get or create a chat session.
     * 
     * @param chatId The chat ID
     * @param userId The user ID
     * @param initialMessage The initial message (optional)
     * @return The chat session
     */
    ChatSession getOrCreateSession(String chatId, String userId, String initialMessage);
    
    /**
     * Get a chat session by ID.
     * 
     * @param chatId The chat ID
     * @return The chat session if found
     */
    Optional<ChatSession> getChatSession(String chatId);
    
    /**
     * Create a new chat session.
     * 
     * @param userId The user ID
     * @param name The session name
     * @return The created chat session
     */
    ChatSession createChatSession(String userId, String name);
    
    /**
     * Archive a chat session.
     * 
     * @param chatId The chat ID to archive
     */
    void archiveChatSession(String chatId);
    
    /**
     * List active chats for a user.
     * 
     * @param userId The user ID
     * @param pageRequest Pagination parameters
     * @return Page of chat sessions
     */
    PageResult<ChatSession> listChatsForUser(String userId, PageRequest pageRequest);
    
    // ========== Chat History ==========
    
    /**
     * Get chat history with pagination.
     * 
     * @param chatId The chat ID
     * @param pageRequest Pagination parameters
     * @param includeContext Whether to include context data
     * @return Page of chat messages
     */
    PageResult<ChatMessage> getChatHistory(String chatId, PageRequest pageRequest, boolean includeContext);
    
    /**
     * Convert a chat message to tasks for display.
     * 
     * @param message The chat message
     * @return List of chat message tasks
     */
    List<ChatMessageTask> convertMessageToTasks(ChatMessage message);
    
    // ========== Workflow Management ==========
    
    /**
     * List all available workflows.
     * 
     * @return List of workflow metadata
     */
    List<WorkflowMetadata> listWorkflows();
    
    /**
     * Get detailed information about a workflow.
     * 
     * @param workflowId The workflow ID
     * @return The workflow details
     */
    WorkflowDetails getWorkflowDetails(String workflowId);
    
    /**
     * Get the initial input schema for a workflow.
     * 
     * @param workflowId The workflow ID
     * @return The initial schema or null if not applicable
     */
    AIFunctionSchema getInitialSchema(String workflowId);
    
    /**
     * Get all schemas used by a workflow.
     * 
     * @param workflowId The workflow ID
     * @return List of schemas
     */
    List<AIFunctionSchema> getWorkflowSchemas(String workflowId);
    
    /**
     * Get workflow instance state by run ID.
     * 
     * @param runId The workflow run ID
     * @return The workflow instance if found
     */
    Optional<WorkflowInstance> getWorkflowState(String runId);
}