package ai.driftkit.chat.framework.workflow;

import ai.driftkit.chat.framework.ai.domain.AIFunctionSchema;
import ai.driftkit.chat.framework.model.StepDefinition;
import ai.driftkit.chat.framework.model.WorkflowContext;
import ai.driftkit.chat.framework.repository.WorkflowContextRepository;
import ai.driftkit.chat.framework.util.ApplicationContextProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for workflow sessions and workflows.
 * Provides centralized management of workflow state.
 */
@Slf4j
public class WorkflowRegistry {
    private static final Map<String, WorkflowContext> sessions = new ConcurrentHashMap<>();
    private static final Map<String, AnnotatedWorkflow> workflows = new ConcurrentHashMap<>();
    
    /**
     * Register a workflow
     * @param workflow The workflow to register
     */
    public static void registerWorkflow(AnnotatedWorkflow workflow) {
        workflows.put(workflow.getWorkflowId(), workflow);
        log.info("Registered workflow: {}", workflow.getWorkflowId());
    }
    
    /**
     * Get a workflow by ID
     * @param workflowId The workflow ID
     * @return The workflow
     */
    public static Optional<AnnotatedWorkflow> getWorkflow(String workflowId) {
        return Optional.ofNullable(workflows.get(workflowId));
    }
    
    /**
     * Get all registered workflows
     * @return Collection of all registered workflows
     */
    public static Collection<AnnotatedWorkflow> getAllWorkflows() {
        return Collections.unmodifiableCollection(workflows.values());
    }
    
    /**
     * Find a workflow that can handle a message
     * @param message The message
     * @param properties Additional properties
     * @return The workflow that can handle the message
     */
    public static Optional<AnnotatedWorkflow> findWorkflowForMessage(String message, Map<String, String> properties) {
        // If workflowId is explicitly specified, try to use it
        if (properties != null && properties.containsKey("workflowId")) {
            String workflowId = properties.get("workflowId");
            AnnotatedWorkflow workflow = workflows.get(workflowId);
            if (workflow != null) {
                return Optional.of(workflow);
            }
        }
        
        // If requestSchemaName is provided, look for a workflow with that schema
        if (properties != null && properties.containsKey("requestSchemaName")) {
            String schemaName = properties.get("requestSchemaName");
            for (AnnotatedWorkflow workflow : workflows.values()) {
                if (hasMatchingSchema(workflow, schemaName)) {
                    log.info("Selected workflow {} based on schema: {}", workflow.getWorkflowId(), schemaName);
                    return Optional.of(workflow);
                }
            }
        }
        
        // Find the first workflow that can handle the message
        for (AnnotatedWorkflow workflow : workflows.values()) {
            if (workflow.canHandle(message, properties)) {
                return Optional.of(workflow);
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Check if workflow has a step with a matching schema name
     * @param workflow The workflow to check
     * @param schemaName The schema name to match
     * @return True if the workflow has a step with the schema name
     */
    private static boolean hasMatchingSchema(AnnotatedWorkflow workflow, String schemaName) {
        if (workflow == null || StringUtils.isBlank(schemaName)) {
            return false;
        }
        
        for (StepDefinition step : workflow.getStepDefinitions()) {
            if (step.getInputSchemas() != null) {
                boolean foundSchema = step.getInputSchemas().stream().map(AIFunctionSchema::getSchemaName).anyMatch(e -> e.equals(schemaName));
                if (foundSchema) {
                    return true;
                }
            }
            if (step.getOutputSchemas() != null) {
                boolean foundSchema = step.getOutputSchemas().stream().map(AIFunctionSchema::getSchemaName).anyMatch(e -> e.equals(schemaName));

                if (foundSchema) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get or create a session for a chat
     * @param chatId The chat ID
     * @param workflow The workflow
     * @return The session
     */
    public static WorkflowContext getOrCreateSession(String chatId, AnnotatedWorkflow workflow) {
        // Check if we already have a session for this chat
        WorkflowContext session = sessions.get(chatId);
        
        if (session == null) {
            // Try to get from repository
            try {
                WorkflowContextRepository repository = getRepository();
                Optional<WorkflowContext> savedSession = repository.findById(chatId);
                if (savedSession.isPresent()) {
                    session = savedSession.get();
                    sessions.put(chatId, session);
                }
            } catch (Exception e) {
                log.warn("Could not retrieve session from repository: {}", e.getMessage());
            }
        }
        
        if (session == null) {
            // Create a new session
            session = new WorkflowContext();
            session.setContextId(chatId);
            session.setWorkflowId(workflow.getWorkflowId());
            session.setState(WorkflowContext.WorkflowSessionState.NEW);
            session.setCreatedTime(System.currentTimeMillis());
            session.setUpdatedTime(System.currentTimeMillis());
            
            // Store the session
            sessions.put(chatId, session);
            saveSession(session);
            log.info("Created new session: {} for workflow: {}", chatId, workflow.getWorkflowId());
        } else if (!session.getWorkflowId().equals(workflow.getWorkflowId())) {
            // If the session exists but is for a different workflow, update it
            log.info("Switching session: {} from workflow: {} to: {}", 
                    chatId, session.getWorkflowId(), workflow.getWorkflowId());
            
            // Reset the session for the new workflow
            session.setWorkflowId(workflow.getWorkflowId());
            session.setState(WorkflowContext.WorkflowSessionState.NEW);
            session.setCurrentStepId(null);
            session.setCurrentResponseId(null);
            session.setProperties(new HashMap<>());
            session.setUpdatedTime(System.currentTimeMillis());
            saveSession(session);
        }
        
        return session;
    }
    
    /**
     * Get a session for a chat
     * @param chatId The chat ID
     * @return The session, if it exists
     */
    public static Optional<WorkflowContext> getSession(String chatId) {
        WorkflowContext session = sessions.get(chatId);
        
        if (session == null) {
            // Try to get from repository
            try {
                WorkflowContextRepository repository = getRepository();
                Optional<WorkflowContext> savedSession = repository.findById(chatId);
                if (savedSession.isPresent()) {
                    session = savedSession.get();
                    sessions.put(chatId, session);
                }
            } catch (Exception e) {
                log.warn("Could not retrieve session from repository: {}", e.getMessage());
            }
        }
        
        return Optional.ofNullable(session);
    }
    
    /**
     * Save a session
     * @param session The session to save
     */
    public static void saveSession(WorkflowContext session) {
        if (session == null || session.getContextId() == null) {
            log.warn("Cannot save session: session or contextId is null");
            return;
        }
        
        // Update in-memory cache
        sessions.put(session.getContextId(), session);
        
        // Save to repository
        try {
            WorkflowContextRepository repository = getRepository();
            repository.saveOrUpdate(session);
        } catch (Exception e) {
            log.warn("Could not save session to repository: {}", e.getMessage());
        }
    }
    
    /**
     * Get the repository bean
     * @return The repository
     */
    private static WorkflowContextRepository getRepository() {
        try {
            return ApplicationContextProvider.getBean(WorkflowContextRepository.class);
        } catch (Exception e) {
            log.warn("Could not get repository bean: {}", e.getMessage());
            throw e;
        }
    }
}