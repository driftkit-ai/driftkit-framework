package ai.driftkit.chat.framework.service;

import ai.driftkit.chat.framework.model.ChatDomain.ChatRequest;
import ai.driftkit.chat.framework.model.ChatDomain.ChatResponse;
import ai.driftkit.chat.framework.model.ChatDomain.MessageType;
import ai.driftkit.chat.framework.model.WorkflowContext;
import ai.driftkit.chat.framework.repository.WorkflowContextRepository;
import ai.driftkit.chat.framework.workflow.AnnotatedWorkflow;
import ai.driftkit.chat.framework.workflow.ChatWorkflow;
import ai.driftkit.chat.framework.workflow.WorkflowRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for processing chat requests through workflows.
 * Uses persistent storage for sessions and responses.
 * Works exclusively with AnnotatedWorkflow implementations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatWorkflowService {
    private final WorkflowContextRepository sessionRepository;
    private final AsyncResponseTracker asyncResponseTracker;
    private final ChatHistoryService chatHistoryService;
    private final ChatMessageService chatMessageService;

    /**
     * Process a chat request either synchronously or asynchronously
     * depending on the step in the workflow.
     */
    public ChatResponse processChat(ChatRequest request) {
        try {
            String sessionId = request.getChatId();
            String workflowId = request.getWorkflowId();
            String userId = request.getUserId();

            chatMessageService.addRequest(sessionId, request);
            
            if (StringUtils.isBlank(userId)) {
                userId = request.getPropertiesMap().getOrDefault("userId", "anonymous");
                request.setUserId(userId);
            }

            WorkflowContext session = findOrCreateSession(sessionId, userId);
            Optional<? extends ChatWorkflow> workflowOpt = findWorkflow(workflowId, request, session);
            
            if (workflowOpt.isEmpty()) {
                throw new IllegalStateException("No suitable workflow found for this request");
            }
            
            ChatWorkflow workflow = workflowOpt.get();
            
            // Generate response ID for tracking
            String responseId = UUID.randomUUID().toString();
            session.setCurrentResponseId(responseId);
            
            if (StringUtils.isBlank(session.getUserId())) {
                session.setUserId(userId);
            }
            
            session.setWorkflowId(workflow.getWorkflowId());

            if (request.getLanguage() != null) {
                session.setLanguage(request.getLanguage());
            }

            WorkflowRegistry.saveSession(session);
            
            ChatResponse response = workflow.processChat(request);
            
            if (StringUtils.isBlank(response.getUserId())) {
                response.setUserId(userId);
            }

            try {
                sessionRepository.saveOrUpdate(session);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // If the request has properties with dataNameId references, resolve them
            if (hasDataNameIdReferences(request)) {
                resolveDataNameIdReferences(request);
                chatMessageService.addRequest(request.getChatId(), request);
            }

            // Track the response for status updates
            asyncResponseTracker.trackResponse(session.getCurrentResponseId(), response);
            
            return response;
        } catch (Exception e) {
            log.error("Error processing chat workflow", e);
            throw new RuntimeException("Error processing chat workflow: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if the request has any properties with dataNameId references
     */
    private boolean hasDataNameIdReferences(ChatRequest request) {
        if (request.getProperties() == null || request.getProperties().isEmpty()) {
            return false;
        }
        
        // Check if any property has a non-null dataNameId
        return request.getProperties().stream()
                .anyMatch(prop -> prop.getDataNameId() != null && !prop.getDataNameId().isEmpty());
    }
    
    /**
     * Resolve any dataNameId references in the request properties
     * by looking up values from previous messages in the chat.
     */
    private void resolveDataNameIdReferences(ChatRequest request) {
        try {
            if (request.getChatId() == null) {
                return;
            }
            
            // Get recent messages for this chat
            var previousMessages = chatHistoryService.getMessages(request.getChatId(), Pageable.ofSize(100))
                    .stream()
                    .filter(e -> e.getType() != MessageType.CONTEXT)
                    .toList();
            
            // Process the request to resolve dataNameId references
            request.resolveDataNameIdReferences(previousMessages);
        } catch (Exception e) {
            log.warn("Error resolving dataNameId references: {}", e.getMessage(), e);
            // This is non-critical, so we just log the error and continue
        }
    }
    
    public Optional<ChatResponse> getResponse(String responseId) {
        return asyncResponseTracker.getResponse(responseId);
    }
    
    private WorkflowContext findOrCreateSession(String sessionId, String userId) {
        Optional<WorkflowContext> existingSession = sessionRepository.findById(sessionId);
        
        if (existingSession.isPresent()) {
            WorkflowContext session = existingSession.get();
            
            if (StringUtils.isBlank(session.getUserId()) && StringUtils.isNotBlank(userId)) {
                session.setUserId(userId);
            }
            
            WorkflowRegistry.saveSession(session);
            
            return session;
        } else {
            long now = System.currentTimeMillis();

            WorkflowContext newSession = new WorkflowContext();
            newSession.setContextId(sessionId);
            newSession.setUserId(userId);
            newSession.setState(WorkflowContext.WorkflowSessionState.NEW);
            newSession.setProperties(new HashMap<>());
            newSession.setCreatedTime(now);
            newSession.setUpdatedTime(now);
            
            WorkflowRegistry.saveSession(newSession);
                
            return newSession;
        }
    }
    
    /**
     * Find the appropriate workflow to handle the request
     */
    private Optional<? extends ChatWorkflow> findWorkflow(String workflowId, ChatRequest request, WorkflowContext session) {
        // First check if the session has a workflow already
        if (StringUtils.isNotBlank(session.getWorkflowId())) {
            Optional<AnnotatedWorkflow> existingWorkflow = WorkflowRegistry.getWorkflow(session.getWorkflowId());
            if (existingWorkflow.isPresent()) {
                return existingWorkflow;
            }
        }
        
        // If workflowId is specified in the request, try to use it directly
        if (StringUtils.isNotBlank(workflowId)) {
            Optional<AnnotatedWorkflow> requestedWorkflow = WorkflowRegistry.getWorkflow(workflowId);
            if (requestedWorkflow.isPresent()) {
                return requestedWorkflow;
            }
        }
        
        // Look for a suitable workflow in the registry based on message content and properties
        return WorkflowRegistry.findWorkflowForMessage(
            request.getPropertiesMap().getOrDefault("message", ""),
            request.getPropertiesMap()
        );
    }
}