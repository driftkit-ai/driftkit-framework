package ai.driftkit.workflow.engine.examples;

import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.annotations.*;
import ai.driftkit.workflow.engine.domain.WorkflowEvent;
import ai.driftkit.workflow.engine.domain.WorkflowException;
import ai.driftkit.workflow.engine.schema.*;
import ai.driftkit.workflow.engine.chat.ChatDomain.*;
import ai.driftkit.workflow.engine.chat.ChatContextHelper;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Comprehensive chat workflow example that demonstrates various workflow patterns:
 * - Suspend: Wait for user input
 * - Continue: Process data and move to next step
 * - Branch: Choose different paths based on conditions
 * - Async: Handle long-running operations
 * - Finish: Complete the workflow
 * - Fail: Handle errors gracefully
 */
@Slf4j
@RequiredArgsConstructor
@Workflow(
    id = "advanced-chat-workflow",
    version = "1.0",
    description = "Advanced chat workflow demonstrating all step result types"
)
public class ChatWorkflowExample {
    
    private final SchemaProvider schemaProvider;
    private final ExternalApiService apiService; // Mock external service
    
    @InitialStep(description = "Process initial chat request and determine intent")
    public StepResult<IntentAnalysis> processInitialRequest(
            WorkflowContext context, 
            ChatRequest request) {
        
        log.info("Processing initial request: {}", request.getMessage());
        
        // Store chat context
        ChatContextHelper.setChatId(context, request.getChatId());
        ChatContextHelper.setUserId(context, request.getUserId());
        ChatContextHelper.addUserMessage(context, request.getMessage());
        
        // Analyze intent (in real app, this would use NLP)
        IntentAnalysis analysis = analyzeIntent(request.getMessage());
        
        // Continue to next step with analysis data
        return new StepResult.Continue<>(analysis);
    }
    
    @Step(
        id = "routeByIntent",
        description = "Route to different handlers based on intent",
        nextClasses = {
            GreetingEvent.class,
            QuestionEvent.class,
            TaskRequestPrompt.class,
            FeedbackEvent.class,
            ClarificationPrompt.class
        }
    )
    public StepResult<?> routeByIntent(
            WorkflowContext context,
            IntentAnalysis analysis) {
        
        log.info("Routing based on intent: {}", analysis.getIntent());
        log.debug("Intent is {} with sentiment {}", analysis.getIntent(), analysis.getSentiment());
        
        // Branch based on intent type
        switch (analysis.getIntent()) {
            case GREETING:
                return new StepResult.Branch<>(new GreetingEvent(analysis.getEntities()));
            
            case QUESTION:
                return new StepResult.Branch<>(new QuestionEvent(
                    analysis.getQuery(), 
                    analysis.getConfidence()
                ));
            
            case TASK_REQUEST:
                // For tasks, we need more info from user
                TaskRequestPrompt prompt = new TaskRequestPrompt();
                prompt.setMessage("I can help you with that task. Could you provide more details?");
                prompt.setTaskType(analysis.getEntities().get("taskType"));
                
                // Suspend and wait for task details
                return SuspendHelper.suspendForInput(prompt, TaskDetails.class);
            
            case FEEDBACK:
                return new StepResult.Branch<>(new FeedbackEvent(analysis.getSentiment()));
            
            default:
                // Unknown intent - ask for clarification
                ClarificationPrompt clarificationPrompt = new ClarificationPrompt();
                clarificationPrompt.setMessage("I'm not sure I understand. Could you please rephrase?");
                // Get original message from trigger data or context
                ChatRequest originalRequest = context.getTriggerData(ChatRequest.class);
                if (originalRequest != null) {
                    clarificationPrompt.setOriginalMessage(originalRequest.getMessage());
                }
                
                return SuspendHelper.suspendForInput(clarificationPrompt, UserClarification.class);
        }
    }
    
    @Step(
        id = "handleGreeting",
        description = "Handle greeting intent"
    )
    public StepResult<ChatResponse> handleGreeting(
            WorkflowContext context,
            GreetingEvent event) {
        
        String response = generateGreetingResponse(event.getEntities());
        
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setMessage(response);
        
        // Continue conversation
        return SuspendHelper.suspendForInput(chatResponse, UserChatMessage.class);
    }
    
    @Step(
        id = "handleQuestion",
        description = "Handle question with potential async search"
    )
    public StepResult<ChatResponse> handleQuestion(
            WorkflowContext context,
            QuestionEvent event) {
        
        log.info("Handling question: {}", event.getQuery());
        
        // For complex questions, do async search
        if (event.getConfidence() < 0.8) {
            try {
                // Start async search operation
                String taskId = UUID.randomUUID().toString();
                CompletableFuture<SearchResult> searchFuture = apiService.searchAsync(event.getQuery());
                
                // Store future for tracking
                context.setStepOutput("searchTaskId", taskId);
                
                // Create immediate data for user
                Map<String, Object> immediateData = Map.of(
                    "status", "SEARCHING",
                    "message", "Searching for information about: " + event.getQuery(),
                    "percentComplete", 10
                );
                
                // Return async result
                return new StepResult.Async<ChatResponse>(
                    "handleQuestion",  // Must be the @AsyncStep method ID
                    30000, // 30 second estimate
                    Map.of("query", event.getQuery(), "future", searchFuture),
                    immediateData
                );
            } catch (Exception e) {
                // Handle API errors by failing the step
                log.error("Failed to initiate search", e);
                return new StepResult.Fail<>(e);
            }
        }
        
        // For simple questions, answer immediately and suspend for next input
        ChatResponse response = new ChatResponse();
        response.setMessage("Here's what I found: " + getSimpleAnswer(event.getQuery()));
        
        return SuspendHelper.suspendForInput(response, UserChatMessage.class);
    }
    
    @Step(
        id = "processTaskDetails",
        description = "Process task details provided by user",
        nextClasses = {TaskExecutionEvent.class}
    )
    public StepResult<?> processTaskDetails(
            WorkflowContext context,
            TaskDetails details) {
        
        log.info("Processing task details: {}", details);
        
        // Validate task details
        if (!isValidTask(details)) {
            ErrorResponse error = new ErrorResponse();
            error.setError("Invalid task details. Please check the requirements.");
            error.setErrorCode("INVALID_TASK");
            
            // Ask user to correct the details
            return SuspendHelper.suspendForInput(error, TaskDetails.class);
        }
        
        // Create task execution event
        return new StepResult.Branch<>(new TaskExecutionEvent(details));
    }
    
    @Step(
        id = "executeTask",
        description = "Execute the requested task"
    )
    public StepResult<TaskResult> executeTask(
            WorkflowContext context,
            TaskExecutionEvent event) {
        
        // For async steps, return immediately with an Async result
        String taskId = UUID.randomUUID().toString();
        
        // Create immediate data for user
        Map<String, Object> immediateData = Map.of(
            "status", "EXECUTING",
            "message", "Starting task execution: " + event.getDetails().getTaskName(),
            "percentComplete", 0
        );
        
        // Return async result
        return new StepResult.Async<TaskResult>(
            "executeTask",  // Must be the @AsyncStep method ID
            10000, // 10 second estimate
            Map.of("event", event),
            immediateData
        );
    }
    
    @Step(
        id = "handleFeedback",
        description = "Process user feedback"
    )
    public StepResult<FeedbackResponse> handleFeedback(
            WorkflowContext context,
            FeedbackEvent event) {
        
        FeedbackResponse response = new FeedbackResponse();
        
        if (event.getSentiment() > 0.5) {
            response.setMessage("Thank you for your positive feedback! Is there anything else I can help with?");
            response.setRequestMoreFeedback(false);
        } else {
            response.setMessage("I'm sorry to hear that. Could you tell me more about what went wrong?");
            response.setRequestMoreFeedback(true);
        }
        
        if (response.isRequestMoreFeedback()) {
            // Wait for detailed feedback
            return SuspendHelper.suspendForInput(response, DetailedFeedback.class);
        } else {
            // End conversation on positive note
            return new StepResult.Finish<>(response);
        }
    }
    
    @Step(
        id = "processDetailedFeedback",
        description = "Process detailed feedback and improve"
    )
    public StepResult<ChatResponse> processDetailedFeedback(
            WorkflowContext context,
            DetailedFeedback feedback) {
        
        // Log feedback for improvement
        log.info("Detailed feedback received: {}", feedback);
        
        ChatResponse response = new ChatResponse();
        response.setMessage("Thank you for the detailed feedback. We'll use this to improve our service. How else can I assist you?");
        
        // Continue conversation
        return SuspendHelper.suspendForInput(response, UserChatMessage.class);
    }
    
    @AsyncStep("executeTask")
    public StepResult<TaskResult> executeTaskAsync(
            Map<String, Object> taskArgs,
            WorkflowContext context,
            AsyncProgressReporter progressReporter) {
        
        log.info("Processing task asynchronously");
        
        TaskExecutionEvent event = (TaskExecutionEvent) taskArgs.get("event");
        
        try {
            // Simulate task execution with progress updates
            for (int i = 0; i <= 100; i += 20) {
                progressReporter.updateProgress(i, "Processing task... " + i + "% complete");
                Thread.sleep(200); // Simulate work
            }
            
            TaskResult result = new TaskResult();
            result.setSuccess(true);
            result.setMessage("Task completed successfully!");
            result.setTaskId(UUID.randomUUID().toString());
            
            return new StepResult.Finish<>(result);
            
        } catch (Exception e) {
            log.error("Task execution failed", e);
            return new StepResult.Fail<>(e);
        }
    }
    
    @AsyncStep(value = "handleQuestion")
    public StepResult<ChatResponse> handleQuestionAsync(
            Map<String, Object> taskArgs,
            WorkflowContext context,
            AsyncProgressReporter progressReporter) {
        
        log.info("Processing async search result");
        
        // Extract the search future from task args
        @SuppressWarnings("unchecked")
        CompletableFuture<SearchResult> searchFuture = (CompletableFuture<SearchResult>) taskArgs.get("future");
        String query = (String) taskArgs.get("query");
        
        try {
            // Update progress
            progressReporter.updateProgress(50, "Processing search results...");
            
            // Get the search result
            SearchResult searchResult = searchFuture.get();
            
            // Update to complete
            progressReporter.updateProgress(100, "Search completed");
            
            // Create response with search results
            ChatResponse response = new ChatResponse();
            response.setMessage(searchResult.getAnswer() + "\n\nIs there anything else you'd like to know?");
            
            // Suspend workflow for next user input
            return SuspendHelper.suspendForInput(response, UserChatMessage.class);
            
        } catch (Exception e) {
            log.error("Search failed", e);
            
            // On error, create error response and suspend
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setMessage("I encountered an error while searching. Could you please rephrase your question?");
            
            return SuspendHelper.suspendForInput(errorResponse, UserChatMessage.class);
        }
    }
    
    
    @Step(
        id = "processContinuedConversation",
        description = "Process continued conversation after suspension"
    )
    public StepResult<IntentAnalysis> processContinuedConversation(
            WorkflowContext context,
            UserChatMessage message) {
        
        log.info("Processing continued conversation: {}", message.getMessage());
        
        // Analyze the new message and route accordingly
        IntentAnalysis analysis = analyzeIntent(message.getMessage());
        
        // Continue to routing based on new intent
        return new StepResult.Continue<>(analysis);
    }
    
    @Step(
        id = "processClarification",
        description = "Process user clarification for unknown intent"
    )
    public StepResult<IntentAnalysis> processClarification(
            WorkflowContext context,
            UserClarification clarification) {
        
        log.info("Processing clarification: {}", clarification.getClarification());
        
        // Analyze the clarified message
        IntentAnalysis analysis = analyzeIntent(clarification.getClarification());
        
        // Continue to routing based on clarified intent
        return new StepResult.Continue<>(analysis);
    }
    
    @Step(
        id = "handleUserChoice",
        description = "Handle user's choice after search result"
    )
    public StepResult<?> handleUserChoice(
            WorkflowContext context,
            UserChoice choice) {
        
        switch (choice.getSelectedOption()) {
            case "Yes, tell me more":
                // Branch to detailed explanation
                return new StepResult.Branch<>(new DetailedExplanationRequest(
                    context.getStepResult("searchResult", SearchResult.class)
                ));
            
            case "No, that's enough":
                // Complete workflow
                ChatResponse thanks = new ChatResponse();
                thanks.setMessage("Great! Feel free to ask if you need anything else.");
                return new StepResult.Finish<>(thanks);
            
            case "Ask a different question":
                // Loop back to start
                ChatResponse prompt = new ChatResponse();
                prompt.setMessage("Sure! What would you like to know?");
                return SuspendHelper.suspendForInput(prompt, UserChatMessage.class);
            
            default:
                // Unexpected choice
                return new StepResult.Fail<>("Unexpected user choice: " + choice.getSelectedOption());
        }
    }
    
    @Step(
        id = "handleError",
        description = "Global error handler"
    )
    public StepResult<ErrorResponse> handleError(
            WorkflowContext context,
            WorkflowException error) {
        
        log.error("Workflow error occurred", error);
        
        ErrorResponse response = new ErrorResponse();
        response.setError("I encountered an error: " + error.getMessage());
        response.setErrorCode(error.getCode());
        response.setSuggestion("Would you like to try again or speak with a human agent?");
        
        // Give user option to retry or escalate
        return SuspendHelper.suspendForInput(response, ErrorRecoveryChoice.class);
    }
    
    // Helper methods
    
    private IntentAnalysis analyzeIntent(String message) {
        IntentAnalysis analysis = new IntentAnalysis();
        
        String lowerMessage = message.toLowerCase();
        log.info("Analyzing intent for message: {}", message);
        
        if (lowerMessage.matches(".*\\b(hello|hi|hey)\\b.*")) {
            analysis.setIntent(Intent.GREETING);
            analysis.setConfidence(0.9);
        } else if (lowerMessage.contains("?") || lowerMessage.contains("what") || lowerMessage.contains("how") || 
                   lowerMessage.contains("search") || lowerMessage.contains("find") || lowerMessage.contains("information")) {
            analysis.setIntent(Intent.QUESTION);
            analysis.setQuery(message);
            analysis.setConfidence(0.7);
        } else if (lowerMessage.contains("thank")) {
            analysis.setIntent(Intent.FEEDBACK);
            analysis.setSentiment(0.8); // Positive feedback
            analysis.setConfidence(0.9);
        } else if (lowerMessage.contains("n't working") || lowerMessage.contains("not working") || 
                   lowerMessage.contains("frustrated") || lowerMessage.contains("problem") || 
                   lowerMessage.contains("issue") || lowerMessage.contains("wrong") || 
                   lowerMessage.contains("bad")) {
            analysis.setIntent(Intent.FEEDBACK);
            analysis.setSentiment(-0.8); // Negative feedback
            analysis.setConfidence(0.8);
        } else if (lowerMessage.contains("do") || lowerMessage.contains("create") || lowerMessage.contains("make") ||
                   lowerMessage.contains("help me") || lowerMessage.contains("need") || lowerMessage.contains("organize") ||
                   lowerMessage.contains("build") || lowerMessage.contains("setup")) {
            analysis.setIntent(Intent.TASK_REQUEST);
            analysis.setConfidence(0.6);
            Map<String, String> entities = new HashMap<>();
            entities.put("taskType", "generic");
            analysis.setEntities(entities);
        } else {
            analysis.setIntent(Intent.UNKNOWN);
            analysis.setConfidence(0.3);
        }
        
        log.info("Detected intent: {} with confidence: {}", analysis.getIntent(), analysis.getConfidence());
        return analysis;
    }
    
    private String generateGreetingResponse(Map<String, String> entities) {
        List<String> greetings = Arrays.asList(
            "Hello! How can I assist you today?",
            "Hi there! What can I help you with?",
            "Welcome! I'm here to help. What do you need?"
        );
        return greetings.get(new Random().nextInt(greetings.size()));
    }
    
    private String getSimpleAnswer(String query) {
        // Mock simple answer generation
        return "Based on my knowledge, the answer involves understanding the context and applying relevant information.";
    }
    
    private boolean isValidTask(TaskDetails details) {
        return details.getTaskName() != null && 
               !details.getTaskName().isEmpty() &&
               details.getRequirements() != null &&
               !details.getRequirements().isEmpty();
    }
    
    // Schema classes and events
    
    public enum Intent {
        GREETING, QUESTION, TASK_REQUEST, FEEDBACK, UNKNOWN
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntentAnalysis {
        private Intent intent;
        private double confidence;
        private String query;
        private Map<String, String> entities = new HashMap<>();
        private double sentiment;
    }
    
    @Data
    @AllArgsConstructor
    public static class GreetingEvent {
        private Map<String, String> entities;
    }
    
    @Data
    @AllArgsConstructor
    public static class QuestionEvent {
        private String query;
        private double confidence;
    }
    
    @Data
    @AllArgsConstructor
    public static class FeedbackEvent {
        private double sentiment;
    }
    
    @Data
    @AllArgsConstructor
    public static class TaskExecutionEvent {
        private TaskDetails details;
    }
    
    @Data
    @AllArgsConstructor
    public static class DetailedExplanationRequest {
        private SearchResult originalResult;
    }
    
    @Data
    @AllArgsConstructor
    public static class SearchInProgress {
        private String taskId;
        private int percentComplete;
    }
    
    @Data
    @NoArgsConstructor
    @SchemaName("userChatMessage")
    @SchemaDescription("User message in chat conversation")
    public static class UserChatMessage {
        @SchemaProperty(required = true, description = "The user's message", nameId = "chat.message")
        private String message;
    }
    
    @Data
    @NoArgsConstructor
    @SchemaName("taskDetails")
    @SchemaDescription("Details about the task to be performed")
    public static class TaskDetails {
        @SchemaProperty(required = true, description = "Task name", nameId = "task.name")
        private String taskName;
        
        @SchemaProperty(required = true, description = "Task requirements", nameId = "task.requirements")
        private List<String> requirements;
        
        @SchemaProperty(description = "Task priority", nameId = "task.priority", 
                       values = {"low", "medium", "high"})
        private String priority = "medium";
        
        @SchemaProperty(description = "Additional notes", nameId = "task.notes")
        private String notes;
    }
    
    @Data
    @NoArgsConstructor
    @SchemaName("userClarification")
    @SchemaDescription("User clarification for ambiguous request")
    public static class UserClarification {
        @SchemaProperty(required = true, description = "Clarified message", nameId = "chat.clarification")
        private String clarification;
    }
    
    @Data
    @NoArgsConstructor
    @SchemaName("userChoice")
    @SchemaDescription("User's choice from options")
    public static class UserChoice {
        @SchemaProperty(required = true, description = "Selected option", nameId = "chat.choice")
        private String selectedOption;
    }
    
    @Data
    @NoArgsConstructor
    @SchemaName("detailedFeedback")
    @SchemaDescription("Detailed feedback from user")
    public static class DetailedFeedback {
        @SchemaProperty(required = true, description = "What went wrong", nameId = "feedback.issue")
        private String issue;
        
        @SchemaProperty(description = "Suggestions for improvement", nameId = "feedback.suggestions")
        private String suggestions;
        
        @SchemaProperty(description = "Rating (1-5)", nameId = "feedback.rating")
        private Integer rating;
    }
    
    @Data
    @NoArgsConstructor
    @SchemaName("errorRecoveryChoice")
    @SchemaDescription("User's choice for error recovery")
    public static class ErrorRecoveryChoice {
        @SchemaProperty(required = true, description = "Recovery option", nameId = "error.recovery",
                       values = {"retry", "human_agent", "cancel"})
        private String recoveryOption;
    }
    
    @Data
    @NoArgsConstructor
    public static class ChatResponse {
        private String message;
    }
    
    @Data
    @NoArgsConstructor
    public static class TaskRequestPrompt {
        private String message;
        private String taskType;
    }
    
    @Data
    @NoArgsConstructor
    public static class ClarificationPrompt {
        private String message;
        private String originalMessage;
    }
    
    @Data
    @NoArgsConstructor
    public static class FollowUpPrompt {
        private String message;
        private List<String> options;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResult {
        private String answer;
        private double confidence;
        private List<String> sources;
    }
    
    @Data
    @NoArgsConstructor
    public static class TaskResult {
        private boolean success;
        private String message;
        private String taskId;
    }
    
    @Data
    @NoArgsConstructor
    public static class FeedbackResponse {
        private String message;
        private boolean requestMoreFeedback;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String errorCode;
        private String suggestion;
    }
    
    // Mock external service
    public interface ExternalApiService {
        CompletableFuture<SearchResult> searchAsync(String query);
        SearchResult getSearchResult(String taskId);
    }
}