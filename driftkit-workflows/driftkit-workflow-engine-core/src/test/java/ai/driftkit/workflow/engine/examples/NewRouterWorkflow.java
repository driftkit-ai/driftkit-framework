package ai.driftkit.workflow.engine.examples;

import ai.driftkit.workflow.engine.annotations.InitialStep;
import ai.driftkit.workflow.engine.annotations.Step;
import ai.driftkit.workflow.engine.annotations.Workflow;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.schema.annotations.SchemaClass;
import ai.driftkit.workflow.engine.schema.SchemaProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Example of RouterWorkflow implemented with the new workflow engine.
 * Demonstrates the use of sealed interfaces for type-safe branching.
 */
@Slf4j
@Workflow(id = "user-router-workflow", version = "2.0", 
          description = "Routes user queries to appropriate handlers using sealed interfaces")
@AllArgsConstructor
public class NewRouterWorkflow {
    
    private final UserService userService;
    private final DocumentService documentService;
    private final NotificationService notificationService;
    
    /**
     * Sealed interface defining all possible routing decisions.
     * This replaces string-based routing with compile-time type safety.
     */
    public sealed interface RoutingDecision 
        permits KnownUserQuery, NewUserQuery, DocumentQuery, SupportQuery, UnknownQuery {
    }
    
    /**
     * Query from a known user - contains user information.
     */
    public record KnownUserQuery(User user, String query) implements RoutingDecision {}
    
    /**
     * Query from a new user - requires onboarding.
     */
    public record NewUserQuery(String email, String query) implements RoutingDecision {}
    
    /**
     * Document-related query - requires document search.
     */
    public record DocumentQuery(String query, List<String> keywords) implements RoutingDecision {}
    
    /**
     * Support request - requires human intervention.
     */
    public record SupportQuery(String query, String category) implements RoutingDecision {}
    
    /**
     * Unknown query type - fallback handling.
     */
    public record UnknownQuery(String query) implements RoutingDecision {}
    
    /**
     * Input data for the router workflow.
     */
    @Data
    public static class RouterInput {
        private final String userEmail;
        private final String query;
        private final Map<String, String> metadata;
    }
    
    /**
     * Initial step - analyzes the query and determines the routing.
     * The return type explicitly shows all possible branches via sealed interface.
     */
    @InitialStep
    public StepResult<RoutingDecision> analyzeQuery(RouterInput input, WorkflowContext context) {
        log.info("Analyzing query from user: {} - '{}'", input.getUserEmail(), input.getQuery());
        
        String query = input.getQuery().toLowerCase();
        
        // Check if user exists
        User user = userService.findByEmail(input.getUserEmail());
        
        // Determine query type based on content
        if (query.contains("document") || query.contains("file") || query.contains("search")) {
            List<String> keywords = extractKeywords(query);
            return new StepResult.Continue<>(new DocumentQuery(input.getQuery(), keywords));
        }
        
        if (query.contains("help") || query.contains("support") || query.contains("issue") || 
            query.contains("bug") || query.contains("error") || query.contains("problem")) {
            String category = determineCategory(query);
            return new StepResult.Continue<>(new SupportQuery(input.getQuery(), category));
        }
        
        if (user != null) {
            return new StepResult.Continue<>(new KnownUserQuery(user, input.getQuery()));
        }
        
        if (input.getUserEmail() != null && !input.getUserEmail().isEmpty()) {
            return new StepResult.Continue<>(new NewUserQuery(input.getUserEmail(), input.getQuery()));
        }
        
        return new StepResult.Continue<>(new UnknownQuery(input.getQuery()));
    }
    
    /**
     * Handles queries from known users.
     * The method signature explicitly shows it handles KnownUserQuery type.
     */
    @Step
    public StepResult<ProcessingResult> handleKnownUser(KnownUserQuery query, WorkflowContext context) {
        log.info("Processing query for known user: {} (ID: {})", 
            query.user().getName(), query.user().getId());
        
        // Access original input from trigger data
        RouterInput originalInput = context.getTriggerData(RouterInput.class);
        
        // Process based on user preferences
        ProcessingResult result = ProcessingResult.builder()
            .success(true)
            .message("Welcome back, " + query.user().getName() + "! Processing your query: " + query.query())
            .userId(query.user().getId())
            .metadata(Map.of(
                "userType", "known",
                "preferences", query.user().getPreferences()
            ))
            .build();
        
        return new StepResult.Continue<>(result);
    }
    
    /**
     * Handles new user onboarding with suspension for user input.
     */
    @Step(nextClasses = {UserNameInput.class})
    public StepResult<UserNamePrompt> handleNewUser(NewUserQuery query, WorkflowContext context) {
        log.info("New user detected: {}", query.email());
        
        // Store query in context for later use
        context.setContextValue("newUserQuery", query);

        // Create prompt object with schema annotations
        UserNamePrompt prompt = new UserNamePrompt();
        prompt.setMessage(String.format(
            "Welcome! I see you're new here. Before I can help with '%s', " +
            "could you please tell me your name?", 
            query.query()
        ));
        prompt.setEmail(query.email());
        prompt.setOriginalQuery(query.query());
        
        // Store original query in metadata for later retrieval
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("email", query.email());
        metadata.put("originalQuery", query.query());
        
        // Suspend to get user information
        return StepResult.suspend(prompt, UserNameInput.class, metadata);
    }
    
    /**
     * Processes user name input and creates the account.
     */
    @Step
    public StepResult<ProcessingResult> processUserNameInput(UserNameInput input, WorkflowContext context) {
        log.info("Processing user name: {}", input.getName());
        
        // Retrieve the original query from context
        NewUserQuery originalQuery = context.getContextValue("newUserQuery", NewUserQuery.class);
        
        // Create user with the provided name
        User newUser = new User(
            "user-" + System.currentTimeMillis(),
            originalQuery.email(),
            input.getName(),
            Map.of("language", "en", "notifications", "email") // Default preferences
        );
        userService.addUser(newUser);
        
        // Process original query
        return new StepResult.Continue<>(
            ProcessingResult.builder()
                .success(true)
                .message("Welcome " + input.getName() + "! Account created. Now processing: " + originalQuery.query())
                .userId(newUser.getId())
                .metadata(Map.of(
                    "userType", "new",
                    "accountCreated", true
                ))
                .build()
        );
    }
    
    /**
     * Handles document search queries with async processing.
     */
    @Step
    public StepResult<ProcessingResult> handleDocumentQuery(DocumentQuery query, WorkflowContext context) {
        log.info("Processing document query with keywords: {}", query.keywords());
        
        // Search for documents
        List<Document> documents = documentService.search(query.keywords());
        
        if (documents.isEmpty()) {
            return new StepResult.Continue<>(
                ProcessingResult.builder()
                    .success(false)
                    .message("No documents found matching your query: " + query.query())
                    .metadata(Map.of("searchKeywords", query.keywords()))
                    .build()
            );
        }
        
        return new StepResult.Continue<>(
            ProcessingResult.builder()
                .success(true)
                .message("Found " + documents.size() + " documents")
                .documents(documents)
                .metadata(Map.of(
                    "searchKeywords", query.keywords(),
                    "resultCount", documents.size()
                ))
                .build()
        );
    }
    
    /**
     * Handles support requests by creating tickets.
     */
    @Step
    public StepResult<ProcessingResult> handleSupportQuery(SupportQuery query) {
        log.info("Creating support ticket for category: {}", query.category());
        
        String ticketId = createSupportTicket(query.query(), query.category());
        
        return new StepResult.Continue<>(
            ProcessingResult.builder()
                .success(true)
                .message("Support ticket created: " + ticketId)
                .metadata(Map.of(
                    "ticketId", ticketId,
                    "category", query.category()
                ))
                .build()
        );
    }
    
    /**
     * Fallback handler for unknown queries.
     */
    @Step
    public StepResult<ProcessingResult> handleUnknownQuery(UnknownQuery query) {
        log.warn("Unknown query type: {}", query.query());
        
        return new StepResult.Continue<>(
            ProcessingResult.builder()
                .success(false)
                .message("I'm not sure how to help with that. Could you please rephrase?")
                .metadata(Map.of("originalQuery", query.query()))
                .build()
        );
    }
    
    /**
     * Final step - sends notification and completes the workflow.
     */
    @Step
    public StepResult<RouterResult> sendNotificationAndComplete(ProcessingResult result, WorkflowContext context) {
        // Send notification if user is known
        if (result.getUserId() != null) {
            notificationService.notify(result.getUserId(), result.getMessage());
        }
        
        // Create final result
        RouterResult finalResult = new RouterResult(
            result.isSuccess(),
            result.getMessage(),
            result.getMetadata(),
            context.getRunId()
        );
        
        return new StepResult.Finish<>(finalResult);
    }
    
    // Helper methods
    
    private List<String> extractKeywords(String query) {
        // Simple keyword extraction - in production, use NLP
        return List.of(query.split("\\s+")).stream()
            .filter(word -> word.length() > 3)
            .filter(word -> !isStopWord(word))
            .toList();
    }
    
    private boolean isStopWord(String word) {
        return List.of("the", "and", "for", "with", "from", "about").contains(word.toLowerCase());
    }
    
    private String determineCategory(String query) {
        if (query.contains("technical") || query.contains("bug")) return "technical";
        if (query.contains("billing") || query.contains("payment")) return "billing";
        if (query.contains("account") || query.contains("login")) return "account";
        return "general";
    }
    
    private String createSupportTicket(String query, String category) {
        return "TICKET-" + System.currentTimeMillis();
    }
    
    // Prompt class for requesting user name
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SchemaClass(id = "userNamePrompt", description = "Prompt requesting user's name")
    public static class UserNamePrompt {
        @SchemaProperty(required = true, description = "Message to display to user")
        private String message;
        
        @SchemaProperty(description = "User's email for context")
        private String email;
        
        @SchemaProperty(description = "Original query for context")
        private String originalQuery;
    }
    
    // Input class for user name collection
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @SchemaClass(id = "userNameInput", description = "User's name input")
    public static class UserNameInput {
        @SchemaProperty(required = true, description = "User's name")
        private String name;
    }
    
    // Domain models
    
    @Data
    @AllArgsConstructor
    public static class User {
        private String id;
        private String email;
        private String name;
        private Map<String, String> preferences;
    }
    
    @Data
    @AllArgsConstructor
    public static class Document {
        private String id;
        private String title;
        private String content;
        private List<String> tags;
    }
    
    @Data
    @lombok.Builder
    public static class ProcessingResult {
        private boolean success;
        private String message;
        private String userId;
        private List<Document> documents;
        private Map<String, Object> metadata;
    }
    
    public record RouterResult(
        boolean success,
        String message,
        Map<String, Object> metadata,
        String workflowRunId
    ) {}
    
    // Mock services for the example
    
    public interface UserService {
        User findByEmail(String email);
        void addUser(User user);
    }
    
    public interface DocumentService {
        List<Document> search(List<String> keywords);
    }
    
    public interface NotificationService {
        void notify(String userId, String message);
    }
}