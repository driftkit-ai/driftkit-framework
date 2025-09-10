package ai.driftkit.chat.framework.workflow;

import ai.driftkit.chat.framework.ai.client.AiClient;
import ai.driftkit.chat.framework.ai.domain.AIFunctionSchema;
import ai.driftkit.chat.framework.ai.domain.AIFunctionSchema.SchemaName;
import ai.driftkit.chat.framework.ai.utils.AIUtils;
import ai.driftkit.chat.framework.model.ChatDomain.ChatRequest;
import ai.driftkit.chat.framework.model.ChatDomain.ChatResponse;
import ai.driftkit.chat.framework.model.ChatDomain.ChatResponse.NextSchema;
import ai.driftkit.chat.framework.model.ChatDomain.MessageType;
import ai.driftkit.chat.framework.util.SchemaUtils;
import ai.driftkit.chat.framework.annotations.SchemaClass;
import ai.driftkit.chat.framework.annotations.AsyncStep;
import ai.driftkit.chat.framework.annotations.WorkflowStep;
import ai.driftkit.chat.framework.events.AsyncTaskEvent;
import ai.driftkit.chat.framework.events.StepEvent;
import ai.driftkit.chat.framework.events.WorkflowTransitionEvent;
import ai.driftkit.chat.framework.model.StepDefinition;
import ai.driftkit.chat.framework.model.WorkflowContext;
import ai.driftkit.chat.framework.service.AsyncResponseTracker;
import ai.driftkit.chat.framework.service.ChatHistoryService;
import ai.driftkit.chat.framework.service.ChatMessageService;
import ai.driftkit.chat.framework.repository.WorkflowContextRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Base class for workflows that use annotations to define steps.
 * This class handles the automatic discovery and registration of workflow steps
 * based on annotations. It implements the ChatWorkflow interface for integration
 * with the chat assistant framework.
 */
@Slf4j
public abstract class AnnotatedWorkflow implements ChatWorkflow {
    @Autowired
    protected AiClient aiClient;
    @Autowired
    protected AsyncResponseTracker asyncResponseTracker;
    @Autowired
    protected ChatHistoryService historyService;
    @Autowired
    protected ChatMessageService messageService;
    @Autowired
    protected WorkflowContextRepository workflowContextRepository;

    // Expression parser for conditions
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    
    // Cache for step methods
    private final Map<String, StepMetadata> stepMetadata = new ConcurrentHashMap<>();
    private final Map<String, AsyncStepMetadata> asyncStepMetadata = new ConcurrentHashMap<>();
    private final Map<String, Method> asyncStepMethods = new ConcurrentHashMap<>(); // Kept for backward compatibility
    private List<StepDefinition> stepDefinitions = new ArrayList<>();
    
    // Cache for schemas automatically generated from classes
    private final Map<Class<?>, AIFunctionSchema> schemaCache = new ConcurrentHashMap<>();
    private final Map<String, AIFunctionSchema> schemaIdCache = new ConcurrentHashMap<>();
    
    // Static class to store metadata about steps
    private static class StepMetadata {
        Method method;
        WorkflowStep annotation;
        List<Class<?>> inputClasses = new ArrayList<>();
        List<Class<?>> outputClasses = new ArrayList<>();
        List<Class<?>> nextClasses = new ArrayList<>();
        List<Parameter> parameters;
    }
    
    // Static class to store metadata about async steps
    private static class AsyncStepMetadata {
        Method method;
        AsyncStep annotation;
        List<Class<?>> inputClasses = new ArrayList<>();
        List<Class<?>> outputClasses = new ArrayList<>();
        List<Parameter> parameters;
    }
    
    public AnnotatedWorkflow() {
        // Discover and register steps
        discoverSteps();
        
        // Register this workflow in the registry
        WorkflowRegistry.registerWorkflow(this);
    }
    
    /**
     * The ID of this workflow.
     */
    public abstract String getWorkflowId();
    
    /**
     * Whether this workflow can handle a message based on its content and properties.
     */
    public abstract boolean canHandle(String message, Map<String, String> properties);
    
    /**
     * Process a chat request and return a response.
     */
    public ChatResponse processChat(ChatRequest request) {
        try {
            // Get or create a session for this chat from the central registry
            WorkflowContext session = WorkflowRegistry.getOrCreateSession(request.getChatId(), this);
            
            // Initialize a new response
            String responseId = asyncResponseTracker.generateResponseId();
            
            // Store the current request in the session
            Map<String, String> properties = new HashMap<>(request.getPropertiesMap());
            
            // Process the chat request based on current session state
            switch (session.getState()) {
                case NEW:
                    // Start the workflow from the first step
                    return startWorkflow(session, request, responseId, properties);

                case PROCESSING:
                case WAITING_FOR_USER_INPUT:
                    // Continue the workflow with user input
                    return continueWorkflow(session, request, responseId, properties);
                    
                case COMPLETED:
                    // Restart the workflow
                    return startWorkflow(session, request, responseId, properties);
                    
                default:
                    throw new IllegalStateException("Unknown session state: " + session.getState());
            }
        } catch (Exception e) {
            log.error("Error processing chat request", e);
            throw new RuntimeException("Failed to process chat request", e);
        }
    }
    
    /**
     * Get all steps defined in this workflow.
     */
    public List<StepDefinition> getStepDefinitions() {
        return Collections.unmodifiableList(stepDefinitions);
    }

    protected void saveContext(WorkflowContext session) {
        workflowContextRepository.saveOrUpdate(session);
    }

    protected Optional<WorkflowContext> getContext(String sessionId) {
        return workflowContextRepository.findById(sessionId);
    }

    /**
     * Create an error response.
     */
    protected ChatResponse createErrorResponse(WorkflowContext session, String errorMessage) {
        // Ensure session has a response ID
        if (session.getCurrentResponseId() == null) {
            session.setCurrentResponseId(asyncResponseTracker.generateResponseId());
        }
        
        return ChatResponse.fromSessionWithError(session, getWorkflowId(), errorMessage);
    }
    
    // Private methods
    
    /**
     * Start the workflow from the first step.
     */
    private ChatResponse startWorkflow(WorkflowContext session, ChatRequest request,
                                       String responseId, Map<String, String> properties) {
        // Reset session state
        session.setCurrentStepId(getFirstStepId());
        session.setCurrentResponseId(responseId);
        // Add properties to session context
        if (properties != null && !properties.isEmpty()) {
            session.getProperties().putAll(properties);
        }
        return executeStep(session, request, properties);
    }

    @Nullable
    private ChatResponse executeStep(WorkflowContext session, ChatRequest request, Map<String, String> properties) {
        if (request.getId() == null) {
            request.setId(UUID.randomUUID().toString());
        }
        session.setState(WorkflowContext.WorkflowSessionState.PROCESSING);

        if (session.getCurrentStepId() == null) {
            session.setCurrentStepId(getFirstStepId());
        }

        if (request.getRequestSchemaName() == null) {
            request.setRequestSchemaName(session.getCurrentStepId());
        } else {
            session.setCurrentStepId(request.getRequestSchemaName());
        }
        
        // Handle composable schemas - when frontend sends data field by field
        String requestSchemaName = request.getRequestSchemaName();
        
        // Check if this is a field from a composable schema
        if (requestSchemaName != null && requestSchemaName.contains("_")) {
            // Extract base schema name (e.g., "checkInfo" from "checkInfo_message")
            String baseSchemaName = requestSchemaName.substring(0, requestSchemaName.indexOf("_"));

            // Check if we have a step with this base schema name
            StepMetadata stepMetadata = this.stepMetadata.get(baseSchemaName);
            if (stepMetadata != null && !stepMetadata.inputClasses.isEmpty()) {
                // This is a composable schema field request
                Class<?> schemaClass = stepMetadata.inputClasses.get(0);
                
                // Check if this is a composable schema
                SchemaClass schemaAnnotation = schemaClass.getAnnotation(SchemaClass.class);
                if (schemaAnnotation != null && schemaAnnotation.composable()) {
                    log.debug("Detected composable schema field: {} for base schema: {}", 
                            requestSchemaName, baseSchemaName);
                    
                    // Get existing partial data from the session
                    @SuppressWarnings("unchecked")
                    Map<String, String> existingData = session.getContextValue(
                            "composable_" + baseSchemaName, Map.class);
                    
                    // Combine with the new data
                    Map<String, String> combinedData = SchemaUtils.combineComposableSchemaData(
                            schemaClass, existingData, properties, baseSchemaName);
                    
                    // Store updated data back in the session
                    session.setContextValue("composable_" + baseSchemaName, 
                            combinedData != null ? combinedData : (existingData != null ? existingData : new HashMap<>(properties)));
                    
                    if (combinedData == null) {
                        // Not all required fields are present yet, return a response asking for the next field
                        
                        // Find the next field schema
                        List<AIFunctionSchema> fieldSchemas = SchemaUtils.getAllSchemasFromClass(schemaClass);
                        
                        // Skip fields we already have data for
                        Map<String, String> currentData = session.getContextValue(
                                "composable_" + baseSchemaName, Map.class);
                        
                        if (currentData != null) {
                            String nextFieldSchema = fieldSchemas.stream()
                                    .filter(schema -> {
                                        // Extract field name from schema name (e.g., "message" from "checkInfo_message")
                                        String fieldName = schema.getSchemaName().substring(schema.getSchemaName().indexOf("_") + 1);
                                        return !currentData.containsKey(fieldName);
                                    })
                                    .findFirst()
                                    .map(AIFunctionSchema::getSchemaName)
                                    .orElse(null);
                            
                            if (nextFieldSchema != null) {
                                ChatResponse response = ChatResponse.fromSession(
                                        session,
                                        session.getWorkflowId(),
                                        Map.of("messageId", "composable_schema_fields_in_progress")
                                );

                                // Save the user message to history
                                saveUserMessage(session.getContextId(), request);
                                
                                // Set state to waiting for more input
                                session.setState(WorkflowContext.WorkflowSessionState.WAITING_FOR_USER_INPUT);
                                WorkflowRegistry.saveSession(session);
                                
                                return response;
                            }
                        }
                    } else {
                        // All required fields are present, continue with the full data
                        // Replace the current properties with the combined data for processing
                        properties = combinedData;
                        
                        // Set the correct schema name for the step
                        request.setRequestSchemaName(baseSchemaName);
                        session.setCurrentStepId(baseSchemaName);
                        
                        // Clear the composable data from the session
                        session.setContextValue("composable_" + baseSchemaName, null);
                    }
                }
            }
        }

        Optional<StepDefinition> stepDefinition = stepDefinitions.stream()
                .filter(e -> e.getId().equals(requestSchemaName))
                .findAny();


        if (stepDefinition.isPresent()) {
            List<AIFunctionSchema> inputSchemas = stepDefinition.get().getInputSchemas();

            if (CollectionUtils.isNotEmpty(inputSchemas)) {
                request.setComposable(inputSchemas.getFirst().isComposable());
                request.fillCurrentSchema(inputSchemas);
            }
        }


        // Execute the step
        ChatResponse response = executeStep(session, properties);

        String currentStepId = Optional.ofNullable(response.getNextSchema()).map(NextSchema::getSchemaName).orElse(session.getCurrentStepId());
        session.setCurrentStepId(currentStepId);
        
        // Save the user message to history
        saveUserMessage(session.getContextId(), request);

        // Save the updated session
        WorkflowRegistry.saveSession(session);

        return response;
    }

    /**
     * Continue the workflow with user input.
     */
    private ChatResponse continueWorkflow(WorkflowContext session, ChatRequest request,
                                          String responseId, Map<String, String> properties) {
        session.setCurrentResponseId(responseId);
        return executeStep(session, request, properties);
    }
    
    /**
     * Execute a step and return the response.
     */
    private ChatResponse executeStep(WorkflowContext session, Map<String, String> properties) {
        try {
            // Get the current step ID
            String stepId = session.getCurrentStepId();
            if (stepId == null) {
                throw new IllegalStateException("No current step ID in session");
            }
            
            // Get the step metadata
            StepMetadata metadata = stepMetadata.get(stepId);
            if (metadata == null) {
                throw new IllegalStateException("No metadata found for step: " + stepId);
            }

            // Prepare arguments for the method
            Object[] args = prepareMethodArguments(metadata, properties, session);
            
            // Invoke the step method
            StepEvent result = (StepEvent) metadata.method.invoke(this, args);
            
            // Store any output data in the session
            if (result.getProperties() != null) {
                session.putAll(result.getProperties());
            }
            
            // Process the result
            if (result instanceof AsyncTaskEvent) {
                // Handle async task
                return handleAsyncTask(session, (AsyncTaskEvent) result);
            } else {
                // Determine the next step based on conditions and flow rules
                String nextStepId = determineNextStep(metadata.annotation, result, session);
                if (nextStepId != null) {
                    result.setNextStepId(nextStepId);
                }
                
                // Handle normal step result
                return handleStepResult(session, result);
            }
        } catch (Exception e) {
            log.error("Error executing step", e);
            return createErrorResponse(session, "Error executing step: " + e.getMessage());
        }
    }
    
    /**
     * Prepare arguments for a step method based on its parameter types.
     * Automatically converts properties to input objects as needed.
     */
    private Object[] prepareMethodArguments(StepMetadata metadata, Map<String, String> properties, WorkflowContext session) {
        List<Object> args = new ArrayList<>();
        
        for (Parameter param : metadata.parameters) {
            Class<?> paramType = param.getType();
            
            if (Map.class.isAssignableFrom(paramType)) {
                // For Map parameters, pass the properties directly
                args.add(properties);
            } else if (WorkflowContext.class.isAssignableFrom(paramType)) {
                // For WorkflowSession parameters, pass the session
                args.add(session);
            } else {
                // Check if the parameter type matches any of the input classes
                boolean matched = false;
                for (Class<?> inputClass : metadata.inputClasses) {
                    if (inputClass.equals(paramType)) {
                        // Convert properties to an instance of the input class
                        Object inputObject = createSchemaInstance(inputClass, properties);
                        args.add(inputObject);
                        matched = true;
                        break;
                    }
                }
                
                if (!matched) {
                    // For other parameter types, try to find a matching property
                    String paramName = param.getName();
                    if (properties.containsKey(paramName)) {
                        args.add(convertValue(properties.get(paramName), paramType));
                    } else {
                        // If no matching property, pass null
                        args.add(null);
                    }
                }
            }
        }
        
        return args.toArray();
    }
    
    /**
     * Convert a string value to the specified type.
     */
    private Object convertValue(String value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (String.class.equals(targetType)) {
            return value;
        } else if (Integer.class.equals(targetType) || int.class.equals(targetType)) {
            return Integer.parseInt(value);
        } else if (Long.class.equals(targetType) || long.class.equals(targetType)) {
            return Long.parseLong(value);
        } else if (Boolean.class.equals(targetType) || boolean.class.equals(targetType)) {
            return Boolean.parseBoolean(value);
        } else if (Double.class.equals(targetType) || double.class.equals(targetType)) {
            return Double.parseDouble(value);
        } else if (Float.class.equals(targetType) || float.class.equals(targetType)) {
            return Float.parseFloat(value);
        } else if (targetType.isEnum()) {
            return Enum.valueOf((Class<Enum>) targetType, value);
        } else {
            try {
                // For complex types, try to use Jackson
                return AIUtils.OBJECT_MAPPER.readValue(value, targetType);
            } catch (Exception e) {
                log.warn("Failed to convert value '{}' to type {}: {}", value, targetType.getName(), e.getMessage());
                return null;
            }
        }
    }

    /**
     * Prepare arguments for an async step method based on its parameter types.
     * Similar to prepareMethodArguments but for async steps.
     */
    private Object[] prepareAsyncMethodArguments(AsyncStepMetadata metadata, Map<String, Object> properties, WorkflowContext session) {
        List<Object> args = new ArrayList<>();

        for (Parameter param : metadata.parameters) {
            Class<?> paramType = param.getType();

            if (Map.class.isAssignableFrom(paramType)) {
                // For Map parameters, pass the properties directly
                args.add(properties);
            } else if (WorkflowContext.class.isAssignableFrom(paramType)) {
                // For WorkflowSession parameters, pass the session
                args.add(session);
            } else {
                // For other parameter types, try to find a matching property
                String paramName = param.getName();
                if (properties.containsKey(paramName)) {
                    args.add(properties.get(paramName));
                } else {
                    // If no matching property, pass null
                    args.add(null);
                }
            }
        }

        return args.toArray();
    }

    /**
     * Determine the next step based on conditions and flow rules.
     */
    private String determineNextStep(WorkflowStep annotation, StepEvent result, WorkflowContext session) {
        // If result has a next step ID, use that
        if (result.getNextStepId() != null) {
            return result.getNextStepId();
        }
        
        // Check if there's a condition in the annotation
        if (StringUtils.hasText(annotation.condition())) {
            boolean conditionResult = evaluateCondition(annotation.condition(), result, session);
            
            if (conditionResult && StringUtils.hasText(annotation.onTrue())) {
                return annotation.onTrue();
            } else if (!conditionResult && StringUtils.hasText(annotation.onFalse())) {
                return annotation.onFalse();
            }
        }

        if (result.getNextInputSchema() != null) {
            return result.getNextInputSchema().getSchemaName();
        }
        
        // Check if there are next steps defined in the annotation
        if (annotation.nextSteps().length > 0) {
            return annotation.nextSteps()[0];
        }
        
        // Otherwise, let the framework determine the next step
        return null;
    }
    
    /**
     * Evaluate a condition expression.
     */
    private boolean evaluateCondition(String conditionExpression, StepEvent stepEvent, WorkflowContext session) {
        try {
            Expression expression = expressionParser.parseExpression(conditionExpression);
            StandardEvaluationContext context = new StandardEvaluationContext();
            
            // Add step event to context
            context.setVariable("event", stepEvent);
            
            // Add properties to context
            if (stepEvent.getProperties() != null) {
                for (Map.Entry<String, String> entry : stepEvent.getProperties().entrySet()) {
                    context.setVariable(entry.getKey(), entry.getValue());
                }
            }
            
            // Add session properties to context
            for (Map.Entry<String, String> entry : new HashMap<>(session.getProperties()).entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
            
            // Add session to context
            context.setVariable("session", session);
            
            // Evaluate the expression
            Object result = expression.getValue(context);
            
            if (result instanceof Boolean) {
                return (Boolean) result;
            } else {
                log.warn("Condition expression '{}' did not evaluate to a boolean: {}", conditionExpression, result);
                return false;
            }
        } catch (Exception e) {
            log.error("Error evaluating condition expression '{}': {}", conditionExpression, e.getMessage());
            return false;
        }
    }
    
    /**
     * Handle an asynchronous task.
     */
    private ChatResponse handleAsyncTask(WorkflowContext session, AsyncTaskEvent event) {
        try {
            // Register the response for tracking
            String responseId = session.getCurrentResponseId();
            
            // Create the response using the helper method
            ChatResponse response = ChatResponse.fromSession(
                    session,
                    getWorkflowId(),
                    event.getProperties()
            );

            response.fillCurrentSchema(event.getCurrentSchema());
            
            // Add next schema if available
            if (event.getNextInputSchema() != null) {
                response.setNextSchemaAsSchema(event.getNextInputSchema());
            }
            
            // Set completion status and required flag
            response.setCompleted(event.isCompleted());
            response.setPercentComplete(event.getPercentComplete());
            response.setRequired(event.isRequired());
            
            // Register for tracking
            asyncResponseTracker.trackResponse(responseId, response);
            
            // Find the async step metadata
            AsyncStepMetadata asyncMetadata = getAsyncStepMetadata(event);

            // Create a supplier for the async task
            Supplier<ChatResponse> asyncTask = () -> {
                try {
                    // Prepare arguments for the async method based on parameter types
                    Map<String, Object> stringProps = new HashMap<>();
                    if (event.getTaskArgs() != null) {
                        for (Map.Entry<String, Object> entry : event.getTaskArgs().entrySet()) {
                            if (entry.getValue() != null) {
                                stringProps.put(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                    
                    // Use the new prepareMethodArguments for async methods
                    Object[] asyncArgs = prepareAsyncMethodArguments(asyncMetadata, stringProps, session);
                    
                    // Invoke the async method
                    StepEvent asyncResult = (StepEvent) asyncMetadata.method.invoke(this, asyncArgs);
                    
                    // Update the response with the result
                    if (asyncResult != null) {
                        response.setCompleted(asyncResult.isCompleted());
                        response.setPercentComplete(asyncResult.getPercentComplete());
                        response.setRequired(asyncResult.isRequired());
                        
                        if (asyncResult.getProperties() != null) {
                            response.setPropertiesMap(asyncResult.getProperties());
                            
                            // Store result properties in session
                            session.putAll(asyncResult.getProperties());
                        }
                    }
                    
                    // Update the response with any changes from the asyncResult
                    if (asyncResult.getNextInputSchema() != null) {
                        // Make sure the nextInputSchema is properly set in the response
                        response.setNextSchemaAsSchema(asyncResult.getNextInputSchema());
                        log.debug("Setting next request schema from asyncResult: {}", 
                                asyncResult.getNextInputSchema().getSchemaName());
                    }
                    
                    // Update the tracked response
                    asyncResponseTracker.updateResponseStatus(responseId, response);
                    
                    // Update session state if completed
                    if (asyncResult.isCompleted()) {
                        // Move to the next step if specified
                        if (asyncResult.getNextStepId() != null) {
                            session.setCurrentStepId(asyncResult.getNextStepId());
                        }
                        
                        // Set session state
                        session.setState(WorkflowContext.WorkflowSessionState.WAITING_FOR_USER_INPUT);
                        
                        // Save the updated session
                        WorkflowRegistry.saveSession(session);
                        
                        // IMPORTANT: Also explicitly update the completed response in history
                        // This ensures history will show the completed status (100%)
                        historyService.updateResponse(response);
                        log.debug("Explicitly updated completed async response in history: {}", responseId);
                    }
                } catch (Exception e) {
                    log.error("Error executing async task", e);
                    // Update response with error
                    Map<String, String> errorProps = new HashMap<>();
                    errorProps.put("error", "Error executing async task: " + e.getMessage());
                    response.setPropertiesMap(errorProps);
                    response.setCompleted(true);
                    
                    // Preserve next request schema if it was set in AsyncTaskEvent
                    if (event.getNextInputSchema() != null) {
                        response.setNextSchemaAsSchema(event.getNextInputSchema());
                        log.debug("Preserving next request schema in error case: {}", 
                                event.getNextInputSchema().getSchemaName());
                    }
                    
                    // Update tracker
                    asyncResponseTracker.updateResponseStatus(responseId, response);
                    
                    // IMPORTANT: Also explicitly update the error response in history
                    // This ensures history will show the completed status (100%)
                    historyService.updateResponse(response);
                    log.debug("Explicitly updated error async response in history: {}", responseId);
                }
                
                // Return the updated response
                return response;
            };
            
            // Execute the async task with proper tracking
            asyncResponseTracker.executeAsync(responseId, response, asyncTask);
            
            // Set session state
            session.setState(WorkflowContext.WorkflowSessionState.WAITING_FOR_USER_INPUT);
            
            // Save the updated session
            WorkflowRegistry.saveSession(session);
            
            // Return the initial response
            return response;
        } catch (Exception e) {
            log.error("Error handling async task", e);
            return createErrorResponse(session, "Error handling async task: " + e.getMessage());
        }
    }

    @NotNull
    private AsyncStepMetadata getAsyncStepMetadata(AsyncTaskEvent event) {
        AsyncStepMetadata asyncMetadata = asyncStepMetadata.get(event.getTaskName());
        if (asyncMetadata == null) {
            // Fall back to legacy method lookup if no metadata found
            Method asyncMethod = asyncStepMethods.get(event.getTaskName());
            if (asyncMethod == null) {
                throw new IllegalStateException("No async method found for task: " + event.getTaskName());
            }

            // Create minimal metadata for backward compatibility
            asyncMetadata = new AsyncStepMetadata();
            asyncMetadata.method = asyncMethod;
            asyncMetadata.parameters = Arrays.asList(asyncMethod.getParameters());
        }
        return asyncMetadata;
    }

    /**
     * Handle a normal step result.
     */
    private ChatResponse handleStepResult(WorkflowContext session, StepEvent event) {
        try {
            // Check if this is a workflow transition event
            if (event instanceof WorkflowTransitionEvent) {
                WorkflowTransitionEvent transitionEvent = (WorkflowTransitionEvent) event;
                
                // Log the transition
                log.info("Workflow transition requested from {} to {}", 
                        transitionEvent.getSourceWorkflowId(), 
                        transitionEvent.getTargetWorkflowId());
                
                // Get the target workflow to find the appropriate step
                Optional<AnnotatedWorkflow> targetWorkflow = WorkflowRegistry.getWorkflow(transitionEvent.getTargetWorkflowId());
                if (targetWorkflow.isEmpty()) {
                    throw new IllegalStateException("Target workflow not found: " + transitionEvent.getTargetWorkflowId());
                }
                
                // Find the target step
                StepDefinition targetStep = null;
                if (transitionEvent.getTargetStepId() != null) {
                    // Find step by ID
                    targetStep = targetWorkflow.get().getStepDefinitions().stream()
                            .filter(step -> transitionEvent.getTargetStepId().equals(step.getId()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "Target step not found: " + transitionEvent.getTargetStepId() + 
                                    " in workflow " + transitionEvent.getTargetWorkflowId()));
                } else {
                    // Default to first step with index 0
                    List<StepDefinition> targetSteps = targetWorkflow.get().getStepDefinitions();
                    targetStep = targetSteps.stream()
                            .filter(step -> step.getIndex() == 0)
                            .findFirst()
                            .orElse(targetSteps.isEmpty() ? null : targetSteps.get(0));
                }
                
                if (targetStep == null) {
                    throw new IllegalStateException("No suitable target step found in workflow " + 
                            transitionEvent.getTargetWorkflowId());
                }
                
                // Create response with transition properties
                ChatResponse response = ChatResponse.fromSession(
                        session,
                        getWorkflowId(),
                        event.getProperties()
                );
                
                // Set the next schema to the target workflow's step
                if (!targetStep.getInputSchemas().isEmpty()) {
                    response.setNextSchemaAsSchema(targetStep.getInputSchemas().get(0));
                }
                
                return response;
            }
            
            // Update session state based on result
            if (event.getNextStepId() != null) {
                session.setCurrentStepId(event.getNextStepId());
            } else {
                // Move to the next step in the flow if no specific next step is specified
                moveToNextStep(session);
            }

            // Set session state
            session.setState(WorkflowContext.WorkflowSessionState.WAITING_FOR_USER_INPUT);
            
            // Save the updated session
            WorkflowRegistry.saveSession(session);
            
            // Create response using the helper method
            ChatResponse response = ChatResponse.fromSession(
                    session,
                    getWorkflowId(),
                    event.getProperties()
            );

            // Set required flag from StepEvent
            response.setRequired(event.isRequired());

            response.fillCurrentSchema(event.getCurrentSchema());

            // Add next schema if available
            if (event.getNextInputSchema() != null) {
                response.setNextSchemaAsSchema(event.getNextInputSchema());
            }
            
            return response;
        } catch (Exception e) {
            log.error("Error handling step result", e);
            return createErrorResponse(session, "Error handling step result: " + e.getMessage());
        }
    }
    
    /**
     * Move to the next step in the workflow.
     */
    private void moveToNextStep(WorkflowContext session) {
        String currentStepId = session.getCurrentStepId();
        int currentIndex = -1;
        
        // Find the current step index
        for (int i = 0; i < stepDefinitions.size(); i++) {
            if (stepDefinitions.get(i).getId().equals(currentStepId)) {
                currentIndex = i;
                break;
            }
        }
        
        // Move to the next step if found
        if (currentIndex >= 0 && currentIndex < stepDefinitions.size() - 1) {
            String nextStepId = stepDefinitions.get(currentIndex + 1).getId();
            session.setCurrentStepId(nextStepId);
        } else {
            // If no next step, mark as completed
            session.setState(WorkflowContext.WorkflowSessionState.COMPLETED);
        }
    }
    
    /**
     * Get the first step ID in the workflow.
     */
    private String getFirstStepId() {
        if (stepDefinitions.isEmpty()) {
            throw new IllegalStateException("No steps defined in workflow");
        }
        return stepDefinitions.get(0).getId();
    }
    
    /**
     * Save a user message to history.
     */
    private void saveUserMessage(String chatId, ChatRequest request) {
        historyService.addRequest(request);
    }
    
    
    /**
     * Discover and register steps from annotated methods.
     */
    private void discoverSteps() {
        log.info("Discovering steps for workflow: {}", getWorkflowId());
        
        // Scan for @WorkflowStep annotations
        Method[] methods = this.getClass().getMethods();
        
        for (Method method : methods) {
            // Process @WorkflowStep annotations
            WorkflowStep stepAnnotation = method.getAnnotation(WorkflowStep.class);
            if (stepAnnotation != null) {
                registerStepMethod(method, stepAnnotation);
            }
            
            // Process @AsyncStep annotations
            AsyncStep asyncAnnotation = method.getAnnotation(AsyncStep.class);
            if (asyncAnnotation != null) {
                registerAsyncStepMethod(method, asyncAnnotation);
            }
        }
        
        // Sort step definitions by their order in the class
        List<StepDefinition> steps = stepDefinitions.stream()
                .sorted(Comparator.comparing(StepDefinition::getIndex))
                .collect(Collectors.toList());

        this.stepDefinitions = steps;

        log.info("Discovered {} steps and {} async steps in workflow: {}", 
                stepMetadata.size(), asyncStepMethods.size(), getWorkflowId());
    }
    
    /**
     * Register a step method.
     */
    private void registerStepMethod(Method method, WorkflowStep annotation) {
        // Process input classes first to derive stepId if needed
        StepMetadata metadata = new StepMetadata();
        metadata.method = method;
        metadata.annotation = annotation;
        metadata.parameters = Arrays.asList(method.getParameters());
        
        // Process input classes - first try explicitly defined classes
        if (annotation.inputClasses().length > 0) {
            // Use explicitly defined multiple input classes
            metadata.inputClasses.addAll(Arrays.asList(annotation.inputClasses()));
        } else if (annotation.inputClass() != void.class) {
            // Use single input class
            metadata.inputClasses.add(annotation.inputClass());
        } else {
            // Auto-discover from method parameters
            for (Parameter param : metadata.parameters) {
                Class<?> paramType = param.getType();
                
                // Skip framework parameter types
                if (Map.class.isAssignableFrom(paramType) || WorkflowContext.class.isAssignableFrom(paramType)) {
                    continue;
                }
                
                // Skip classes without schema annotations
                if (!paramType.isAnnotationPresent(SchemaClass.class) && !paramType.isAnnotationPresent(SchemaName.class)) {
                    continue;
                }
                
                // Found a valid schema class parameter
                metadata.inputClasses.add(paramType);
                log.debug("Auto-discovered input class from parameter: {}", paramType.getName());
                break; // Only use the first valid parameter
            }
        }
        
        // Determine the step ID with the following priority:
        // 1. Explicit ID from annotation
        // 2. Name of the inputClass if available
        // 3. Method name as fallback
        String stepId;
        if (!annotation.id().isEmpty()) {
            // Explicit ID in annotation
            stepId = annotation.id();
        } else if (!metadata.inputClasses.isEmpty()) {
            // Use the first input class name
            Class<?> inputClass = metadata.inputClasses.get(0);
            // Get name from schema ID first, or class simple name as fallback
            stepId = SchemaUtils.getSchemaId(inputClass);
        } else {
            // Fallback to method name
            stepId = method.getName();
        }
        
        log.debug("Registering step: {} ({})", stepId, method.getName());
        
        // Process output classes
        if (annotation.outputClasses().length > 0) {
            // Use explicitly defined output classes (direct step outputs)
            metadata.outputClasses.addAll(Arrays.asList(annotation.outputClasses()));
        }
        
        // Process next classes
        if (annotation.nextClasses().length > 0) {
            // Use explicitly defined next classes (possible next step inputs)
            metadata.nextClasses.addAll(Arrays.asList(annotation.nextClasses()));
        }
        
        stepMetadata.put(stepId, metadata);
        
        // Determine description with priority: WorkflowStep annotation, then inputClass
        String description;
        
        if (!annotation.description().isEmpty()) {
            // Use explicit description from WorkflowStep annotation
            description = annotation.description();
        } else if (!metadata.inputClasses.isEmpty()) {
            // Try to get description from SchemaClass annotation on inputClass
            Class<?> inputClass = metadata.inputClasses.get(0);
            SchemaClass schemaAnnotation = inputClass.getAnnotation(SchemaClass.class);
            
            if (schemaAnnotation != null && !schemaAnnotation.description().isEmpty()) {
                description = schemaAnnotation.description();
            } else {
                // Fallback to generic description based on class name
                description = "Process " + SchemaUtils.getSchemaId(inputClass);
            }
        } else {
            // Default fallback
            description = "Execute " + stepId;
        }
        
        // Create a step definition for the workflow graph
        StepDefinition definition = StepDefinition.builder()
                .id(stepId)
                .index(annotation.index())
                .action(description)
                .userInputRequired(annotation.requiresUserInput())
                .asyncExecution(annotation.async())
                .build();
        
        // Process input schemas
        List<AIFunctionSchema> inputSchemas = processSchemasFromClasses(metadata.inputClasses);
        
        // If no class-based schemas, try ID-based schema
        if (inputSchemas.isEmpty() && !annotation.inputSchemaId().isEmpty()) {
            AIFunctionSchema schema = getSchemaById(annotation.inputSchemaId());
            if (schema != null) {
                inputSchemas.add(schema);
            }
        }
        
        // Set the input schemas
        if (!inputSchemas.isEmpty()) {
            definition.setInputSchemas(inputSchemas);
        }
        
        // Process output schemas
        List<AIFunctionSchema> outputSchemas = processSchemasFromClasses(metadata.outputClasses);
        
        // If no class-based schemas, try ID-based schema
        if (outputSchemas.isEmpty() && !annotation.outputSchemaId().isEmpty()) {
            AIFunctionSchema schema = getSchemaById(annotation.outputSchemaId());
            if (schema != null) {
                outputSchemas.add(schema);
            }
        }
        
        // Set the output schemas
        if (!outputSchemas.isEmpty()) {
            definition.setOutputSchemas(outputSchemas);
        }
        
        stepDefinitions.add(definition);
    }
    
    /**
     * Register an async step method.
     */
    private void registerAsyncStepMethod(Method method, AsyncStep annotation) {
        String stepId = annotation.forStep();
        
        log.debug("Registering async step for: {}", stepId);
        
        // Store the method by its step ID (for backward compatibility)
        asyncStepMethods.put(stepId, method);
        
        // Create and store async step metadata
        AsyncStepMetadata metadata = new AsyncStepMetadata();
        metadata.method = method;
        metadata.annotation = annotation;
        metadata.parameters = Arrays.asList(method.getParameters());
        
        // Process input classes - first try explicitly defined classes
        if (annotation.inputClasses().length > 0) {
            // Use explicitly defined multiple input classes
            metadata.inputClasses.addAll(Arrays.asList(annotation.inputClasses()));
        } else if (annotation.inputClass() != void.class) {
            // Use single input class
            metadata.inputClasses.add(annotation.inputClass());
        } else {
            // Auto-discover from method parameters
            for (Parameter param : metadata.parameters) {
                Class<?> paramType = param.getType();
                
                // Skip framework parameter types
                if (Map.class.isAssignableFrom(paramType) || WorkflowContext.class.isAssignableFrom(paramType)) {
                    continue;
                }
                
                // Skip classes without schema annotations
                if (!paramType.isAnnotationPresent(SchemaClass.class) && !paramType.isAnnotationPresent(SchemaName.class)) {
                    continue;
                }
                
                // Found a valid schema class parameter
                metadata.inputClasses.add(paramType);
                log.debug("Auto-discovered input class from parameter: {}", paramType.getName());
                break; // Only use the first valid parameter
            }
        }
        
        // Process output classes
        if (annotation.nextClasses().length > 0) {
            // Use explicitly defined multiple output classes
            metadata.outputClasses.addAll(Arrays.asList(annotation.nextClasses()));
        } else if (annotation.outputClass() != void.class) {
            // Use single output class
            metadata.outputClasses.add(annotation.outputClass());
        }
        
        // Generate and cache schemas for all input classes
        for (Class<?> inputClass : metadata.inputClasses) {
            AIFunctionSchema schema = getSchemaFromClass(inputClass);
            if (schema != null) {
                // Cache the schema by ID for future reference
                String schemaId = SchemaUtils.getSchemaId(inputClass);
                if (schemaId != null) {
                    schemaIdCache.put(schemaId, schema);
                }
            }
        }
        
        // Generate and cache schemas for all output classes
        for (Class<?> outputClass : metadata.outputClasses) {
            AIFunctionSchema schema = getSchemaFromClass(outputClass);
            if (schema != null) {
                // Cache the schema by ID for future reference
                String schemaId = SchemaUtils.getSchemaId(outputClass);
                if (schemaId != null) {
                    schemaIdCache.put(schemaId, schema);
                }
            }
        }
        
        // Store the metadata
        asyncStepMetadata.put(stepId, metadata);
    }

    
    /**
     * Get a schema by its ID.
     * First checks the cache, then tries to derive the schema from known classes.
     */
    protected AIFunctionSchema getSchemaById(String schemaId) {
        if (schemaId == null || schemaId.isEmpty()) {
            return null;
        }
        
        // Check cache first
        if (schemaIdCache.containsKey(schemaId)) {
            return schemaIdCache.get(schemaId);
        }
        
        // Try to find a class with this schema ID and cache it for next time
        for (Class<?> schemaClass : schemaCache.keySet()) {
            String derivedId = SchemaUtils.getSchemaId(schemaClass);
            if (schemaId.equals(derivedId)) {
                AIFunctionSchema schema = schemaCache.get(schemaClass);
                schemaIdCache.put(schemaId, schema);
                return schema;
            }
        }
        
        // If not found in cache, subclasses can override this to provide schemas
        return null;
    }
    
    /**
     * Process a list of classes and extract all schemas (regular or composable)
     * @param classes List of classes to process
     * @return List of schemas (combined for regular and composable)
     */
    private List<AIFunctionSchema> processSchemasFromClasses(List<Class<?>> classes) {
        List<AIFunctionSchema> schemas = new ArrayList<>();
        
        for (Class<?> cls : classes) {
            // Check if it's a composable schema
            // Check if class has SchemaClass annotation with composable=true
            SchemaClass schemaAnnotation = cls.getAnnotation(SchemaClass.class);
            if (schemaAnnotation != null && schemaAnnotation.composable()) {
                // Get all composable schemas
                List<AIFunctionSchema> composableSchemas = SchemaUtils.getAllSchemasFromClass(cls);
                schemas.addAll(composableSchemas);
                
                // Cache each schema by ID
                for (AIFunctionSchema schema : composableSchemas) {
                    if (schema.getSchemaName() != null) {
                        schemaIdCache.put(schema.getSchemaName(), schema);
                    }
                }
            } else {
                // Standard schema processing
                AIFunctionSchema schema = getSchemaFromClass(cls);
                if (schema != null) {
                    schemas.add(schema);
                    
                    // Cache the schema by ID for future reference
                    String schemaId = SchemaUtils.getSchemaId(cls);
                    if (schemaId != null) {
                        schemaIdCache.put(schemaId, schema);
                    }
                }
            }
        }
        
        return schemas;
    }
    
    /**
     * Get a schema from a class.
     * This uses the SchemaUtils class to convert the class to an AIFunctionSchema.
     */
    protected AIFunctionSchema getSchemaFromClass(Class<?> schemaClass) {
        if (schemaClass == null || schemaClass == void.class) {
            return null;
        }
        
        // Check cache first
        if (schemaCache.containsKey(schemaClass)) {
            return schemaCache.get(schemaClass);
        }
        
        // Generate schema from class
        AIFunctionSchema schema = SchemaUtils.getSchemaFromClass(schemaClass);
        
        // Cache for future use
        schemaCache.put(schemaClass, schema);
        
        return schema;
    }
    
    /**
     * Create an instance of a schema class from properties.
     */
    protected <T> T createSchemaInstance(Class<T> schemaClass, Map<String, String> properties) {
        return SchemaUtils.createInstance(schemaClass, properties);
    }
    
    /**
     * Extract properties from a schema class instance.
     */
    protected Map<String, String> extractSchemaProperties(Object schemaObject) {
        return SchemaUtils.extractProperties(schemaObject);
    }
    
    /**
     * Convert an object to a map of strings for response properties.
     */
    protected Map<String, String> objectToProperties(Object object) {
        Map<String, String> properties = new LinkedHashMap<>();
        
        if (object == null) {
            return properties;
        }
        
        try {
            if (object instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) object;
                
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    
                    if (value != null) {
                        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                            properties.put(key, value.toString());
                        } else {
                            // Convert complex objects to JSON
                            properties.put(key, AIUtils.OBJECT_MAPPER.writeValueAsString(value));
                        }
                    }
                }
            } else {
                // Use reflection to convert Java object to properties
                ReflectionUtils.doWithFields(object.getClass(), field -> {
                    field.setAccessible(true);
                    Object value = field.get(object);
                    
                    if (value != null) {
                        if (value instanceof Enum || value instanceof String || value instanceof Number || value instanceof Boolean) {
                            properties.put(field.getName(), value.toString());
                        } else {
                            // Convert complex objects to JSON
                            try {
                                properties.put(field.getName(), AIUtils.OBJECT_MAPPER.writeValueAsString(value));
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                });
            }
        } catch (JsonProcessingException e) {
            log.error("Error converting object to properties", e);
        }
        
        return properties;
    }

    protected void addDebugContext(WorkflowContext session, String message) {
        log.debug("[{}] [{}] {}", getWorkflowId(), session.getContextId(), message);
        messageService.addMessage(session.getContextId(), message, MessageType.CONTEXT);
    }
}