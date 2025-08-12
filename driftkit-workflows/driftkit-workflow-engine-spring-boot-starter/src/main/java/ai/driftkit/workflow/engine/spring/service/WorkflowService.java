package ai.driftkit.workflow.engine.spring.service;

import ai.driftkit.workflow.engine.chat.ChatDomain.*;
import ai.driftkit.workflow.engine.chat.ChatMessageTask;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.domain.ChatSession;
import ai.driftkit.workflow.engine.domain.WorkflowDetails;
import ai.driftkit.workflow.engine.domain.WorkflowMetadata;
import ai.driftkit.workflow.engine.persistence.MemoryManagementService;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import ai.driftkit.workflow.engine.schema.SchemaProvider;
import ai.driftkit.workflow.engine.service.DefaultWorkflowExecutionService;
import ai.driftkit.workflow.engine.service.WorkflowExecutionService;
import ai.driftkit.workflow.engine.spring.adapter.PageResultAdapter;
import ai.driftkit.workflow.engine.spring.websocket.WorkflowEventWebSocketBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Spring service adapter for workflow management and chat integration.
 * This is a thin wrapper that delegates to the core WorkflowExecutionService.
 */
@Slf4j
@Service
public class WorkflowService {

    private final WorkflowExecutionService coreService;
    
    // Optional WebSocket bridge (may be null if WebSocket is disabled)
    @Autowired(required = false)
    private WorkflowEventWebSocketBridge webSocketBridge;
    
    /**
     * Constructor that creates the core service.
     */
    @Autowired
    public WorkflowService(WorkflowEngine engine, 
                          SchemaProvider schemaProvider,
                          MemoryManagementService memoryService) {
        this.coreService = new DefaultWorkflowExecutionService(engine, schemaProvider, memoryService);
    }
    

    // ========== Chat Management - Delegate to Core ==========

    public ChatSession getOrCreateChatSession(String chatId, String userId, String initialMessage) {
        return coreService.getOrCreateSession(chatId, userId, initialMessage);
    }

    public Optional<ChatSession> getChatSession(String chatId) {
        return coreService.getChatSession(chatId);
    }

    public ChatSession createChatSession(String userId, String name) {
        return coreService.createChatSession(userId, name);
    }

    public void archiveChatSession(String chatId) {
        coreService.archiveChatSession(chatId);
    }

    public Page<ChatSession> listChatsForUser(String userId, Pageable pageable) {
        var pageRequest = PageResultAdapter.toPageRequest(pageable);
        var pageResult = coreService.listChatsForUser(userId, pageRequest);
        return PageResultAdapter.toPage(pageResult, pageable);
    }

    // ========== Chat Processing - Delegate to Core ==========

    public ChatResponse processChatRequest(ChatRequest request) {
        // Send request to WebSocket if available
        if (webSocketBridge != null) {
            webSocketBridge.sendChatRequest(request);
        }
        
        ChatResponse response = coreService.executeChat(request);
        
        // Send response to WebSocket if available
        if (webSocketBridge != null) {
            webSocketBridge.sendChatResponse(response);
        }
        
        return response;
    }
    
    public ChatResponse resumeChatRequest(String messageId, ChatRequest request) {
        // Send request to WebSocket if available
        if (webSocketBridge != null) {
            webSocketBridge.sendChatRequest(request);
        }
        
        ChatResponse response = coreService.resumeChat(messageId, request);
        
        // Send response to WebSocket if available
        if (webSocketBridge != null) {
            webSocketBridge.sendChatResponse(response);
        }
        
        return response;
    }

    public Optional<ChatResponse> getChatResponse(String responseId) {
        return coreService.getAsyncStatus(responseId);
    }

    public Page<ChatMessage> getChatHistory(String chatId, Pageable pageable, boolean includeContext, boolean includeSchema) {
        var pageRequest = PageResultAdapter.toPageRequest(pageable);
        var pageResult = coreService.getChatHistory(chatId, pageRequest, includeContext);
        return PageResultAdapter.toPage(pageResult, pageable);
    }

    public List<ChatMessageTask> convertMessageToTasks(ChatMessage message) {
        return coreService.convertMessageToTasks(message);
    }

    // ========== Workflow Management - Delegate to Core ==========

    public List<WorkflowMetadata> listWorkflows() {
        return coreService.listWorkflows();
    }

    public WorkflowDetails getWorkflowDetails(String workflowId) {
        return coreService.getWorkflowDetails(workflowId);
    }

    public List<AIFunctionSchema> getWorkflowSchemas(String workflowId) {
        return coreService.getWorkflowSchemas(workflowId);
    }

    public AIFunctionSchema getInitialSchema(String workflowId) {
        return coreService.getInitialSchema(workflowId);
    }

    public Optional<WorkflowInstance> getWorkflowState(String runId) {
        return coreService.getWorkflowState(runId);
    }
}