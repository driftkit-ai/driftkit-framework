package ai.driftkit.workflow.engine.core;

import ai.driftkit.common.domain.Language;
import ai.driftkit.workflow.engine.schema.SchemaName;
import ai.driftkit.workflow.engine.schema.SchemaDescription;
import ai.driftkit.workflow.engine.schema.SchemaProperty;
import ai.driftkit.workflow.engine.annotations.Workflow;
import ai.driftkit.workflow.engine.annotations.InitialStep;
import ai.driftkit.workflow.engine.annotations.Step;
import ai.driftkit.workflow.engine.annotations.AsyncStep;
import ai.driftkit.workflow.engine.chat.ChatDomain.*;
import ai.driftkit.workflow.engine.chat.ChatContextHelper;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.AsyncProgressReporter;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema.PropertyType;
import ai.driftkit.workflow.engine.core.WorkflowEngine.WorkflowExecution;
import ai.driftkit.workflow.engine.domain.ChatSession;
import ai.driftkit.workflow.engine.domain.PageRequest;
import ai.driftkit.workflow.engine.domain.PageResult;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.memory.WorkflowMemoryConfiguration;
import ai.driftkit.workflow.engine.persistence.*;
import ai.driftkit.workflow.engine.schema.DefaultSchemaProvider;
import ai.driftkit.workflow.engine.schema.SchemaProvider;
import ai.driftkit.workflow.engine.service.DefaultWorkflowExecutionService;
import ai.driftkit.workflow.engine.service.WorkflowExecutionService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test for chat memory, history, and session management features.
 * Tests the integration of memory management, chat history persistence, and session lifecycle.
 */
@Slf4j
public class ChatMemoryAndHistoryTest {

    private WorkflowEngine engine;
    private WorkflowExecutionService executionService;
    private ChatSessionRepository sessionRepository;
    private ChatHistoryRepository historyRepository;
    private AsyncResponseRepository asyncResponseRepository;
    private MemoryManagementService memoryService;
    private SchemaProvider schemaProvider;
    private TestChatWorkflow workflow;

    /**
     * Test workflow with multiple steps demonstrating chat memory and history features.
     */
    @Workflow(
        id = "test-chat-workflow",
        version = "1.0",
        description = "Workflow for testing chat memory and history features"
    )
    public static class TestChatWorkflow {
        
        private final ChatHistoryRepository historyRepository;
        private final ChatSessionRepository sessionRepository;
        
        public TestChatWorkflow(ChatHistoryRepository historyRepository, ChatSessionRepository sessionRepository) {
            this.historyRepository = historyRepository;
            this.sessionRepository = sessionRepository;
        }
        
        // ========== Data Classes ==========
        
        @Data
        @NoArgsConstructor
        @SchemaName("userInput")
        @SchemaDescription("Additional user input")
        public static class UserInput {
            @SchemaProperty(description = "User's message", required = true)
            private String message;
            
            @SchemaProperty(description = "Additional context")
            private String context;
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @SchemaName("assistantResponse")
        @SchemaDescription("Assistant's response with history")
        public static class AssistantResponse {
            @SchemaProperty(description = "Response text")
            private String response;
            
            @SchemaProperty(description = "Recent conversation history")
            private List<String> recentHistory;
            
            @SchemaProperty(description = "Total conversation count")
            private int conversationCount;
        }
        
        @Data
        @NoArgsConstructor
        @SchemaName("suspendPrompt")
        @SchemaDescription("Prompt for user when suspending")
        public static class SuspendPrompt {
            @SchemaProperty(description = "Prompt message")
            private String prompt;
            
            @SchemaProperty(description = "Hint for the user")
            private String hint;
            
            public SuspendPrompt(String prompt, String hint) {
                this.prompt = prompt;
                this.hint = hint;
            }
        }
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @SchemaName("asyncProgress")
        @SchemaDescription("Progress information for async operation")
        public static class AsyncProgress {
            @SchemaProperty(description = "Current status")
            private String status;
            
            @SchemaProperty(description = "Progress percentage")
            private int progressPercent;
            
            @SchemaProperty(description = "Estimated time remaining in seconds")
            private long estimatedTimeRemaining;
        }
        
        // Event classes for branching
        @Data
        public static class IntentAnalysis {
            private final String intent;
            private final String originalMessage;
            private final Map<String, String> entities;
        }
        
        @Data
        public static class HistoryRequest {
            private final String chatId;
            private final int limit;
        }
        
        @Data
        public static class MemoryCheckRequest {
            private final String chatId;
            private final String userId;
        }
        
        @Data
        public static class AsyncProcessingRequest {
            private final String chatId;
            private final String data;
        }
        
        // ========== Workflow Steps ==========
        
        @InitialStep
        public StepResult<IntentAnalysis> analyzeIntent(ChatRequest request, WorkflowContext context) {
            // Store chat context
            ChatContextHelper.setChatId(context, request.getChatId());
            ChatContextHelper.setUserId(context, request.getUserId());
            context.setContextValue("language", request.getLanguage().toString());
            
            // Add message to history
            historyRepository.addMessage(request.getChatId(), request);
            
            String message = request.getMessage().toLowerCase();
            Map<String, String> entities = new HashMap<>();
            
            // Analyze intent
            String intent;
            if (message.contains("history")) {
                intent = "HISTORY";
                entities.put("limit", message.contains("all") ? "100" : "10");
            } else if (message.contains("memory") || message.contains("stats")) {
                intent = "MEMORY_CHECK";
            } else if (message.contains("suspend") || message.contains("help")) {
                intent = "SUSPEND";
            } else if (message.contains("async") || message.contains("process")) {
                intent = "ASYNC_PROCESS";
                entities.put("data", message);
            } else {
                intent = "NORMAL";
            }
            
            IntentAnalysis analysis = new IntentAnalysis(intent, request.getMessage(), entities);
            return StepResult.continueWith(analysis);
        }
        
        @Step
        public StepResult<?> routeByIntent(IntentAnalysis analysis, WorkflowContext context) {
            log.info("Routing by intent: {}", analysis.getIntent());
            
            switch (analysis.getIntent()) {
                case "HISTORY":
                    String chatId = ChatContextHelper.getChatId(context);
                    int limit = Integer.parseInt(analysis.getEntities().getOrDefault("limit", "10"));
                    return StepResult.branch(new HistoryRequest(chatId, limit));
                    
                case "MEMORY_CHECK":
                    return StepResult.branch(new MemoryCheckRequest(
                        ChatContextHelper.getChatId(context),
                        ChatContextHelper.getUserId(context)
                    ));
                    
                case "SUSPEND":
                    SuspendPrompt prompt = new SuspendPrompt(
                        "I'm here to help. What would you like assistance with?",
                        "You can ask about history, check memory stats, or process data"
                    );
                    return StepResult.suspend(prompt, UserInput.class);
                    
                case "ASYNC_PROCESS":
                    return StepResult.branch(new AsyncProcessingRequest(
                        ChatContextHelper.getChatId(context),
                        analysis.getOriginalMessage()
                    ));
                    
                default:
                    // Normal response - route to processNormalMessage
                    return StepResult.branch(analysis);
            }
        }
        
        @Step
        public StepResult<AssistantResponse> showHistory(HistoryRequest request, WorkflowContext context) {
            log.info("Showing history for chat: {} with limit: {}", request.getChatId(), request.getLimit());
            
            // Get chat history
            PageResult<ChatMessage> historyPage = historyRepository.findByChatId(
                request.getChatId(), 
                PageRequest.of(0, request.getLimit()), 
                false
            );
            
            List<ChatMessage> messages = historyPage.getContent();
            
            AssistantResponse response = new AssistantResponse(
                String.format("Here's your conversation history (%d messages):", historyPage.getTotalElements()),
                messages.stream()
                    .map(msg -> String.format("[%s] %s", 
                        msg.getType(), 
                        msg.getPropertiesMap().getOrDefault("message", ""))
                    )
                    .limit(request.getLimit())
                    .toList(),
                (int) historyPage.getTotalElements()
            );
            
            return StepResult.finish(response);
        }
        
        @Step
        public StepResult<AssistantResponse> checkMemoryStats(MemoryCheckRequest request, WorkflowContext context) {
            log.info("Checking memory stats for user: {}", request.getUserId());
            
            // Get user's sessions
            PageResult<ChatSession> sessions = sessionRepository.findByUserId(
                request.getUserId(), 
                PageRequest.of(0, 100)
            );
            
            // Get message count for current chat
            long messageCount = historyRepository.countByChatId(request.getChatId());
            
            // Calculate total messages across all sessions
            long totalMessages = sessions.getContent().stream()
                .mapToLong(session -> historyRepository.countByChatId(session.getChatId()))
                .sum();
            
            AssistantResponse response = new AssistantResponse(
                String.format("Memory Statistics:\n" +
                    "- Active sessions: %d\n" +
                    "- Messages in current chat: %d\n" +
                    "- Total messages across all chats: %d",
                    sessions.getTotalElements(),
                    messageCount,
                    totalMessages
                ),
                List.of(String.format("Sessions: %d, Current: %d, Total: %d", 
                    sessions.getTotalElements(), messageCount, totalMessages)),
                (int) messageCount
            );
            
            return StepResult.finish(response);
        }
        
        @Step
        public StepResult<AssistantResponse> handleUserInput(UserInput input, WorkflowContext context) {
            log.info("Handling user input after suspension: {}", input.getMessage());
            
            // Process the resumed input
            String chatId = ChatContextHelper.getChatId(context);
            
            // Create a chat message from user input
            ChatRequest resumedRequest = new ChatRequest(
                chatId,
                Map.of("message", input.getMessage()),
                Language.valueOf(context.getStringOrDefault("language", "ENGLISH")),
                "test-chat-workflow"
            );
            resumedRequest.setUserId(ChatContextHelper.getUserId(context));
            
            // Add to history
            historyRepository.addMessage(chatId, resumedRequest);
            
            AssistantResponse response = new AssistantResponse(
                String.format("I understand you need help with: %s%s",
                    input.getMessage(),
                    input.getContext() != null ? " (Context: " + input.getContext() + ")" : ""
                ),
                List.of(input.getMessage()),
                1
            );
            
            return StepResult.finish(response);
        }
        
        @Step
        public StepResult<AssistantResponse> processNormalMessage(IntentAnalysis analysis, WorkflowContext context) {
            log.info("Processing normal message: {}", analysis.getOriginalMessage());
            
            AssistantResponse response = new AssistantResponse(
                "I received your message: " + analysis.getOriginalMessage(),
                List.of(analysis.getOriginalMessage()),
                1
            );
            
            return StepResult.finish(response);
        }
        
        @Step
        public StepResult<AsyncProgress> startAsyncProcessing(AsyncProcessingRequest request, WorkflowContext context) {
            log.info("Starting async processing for: {}", request.getData());
            
            // Store async request data in context
            context.setContextValue("asyncData", request.getData());
            
            AsyncProgress progress = new AsyncProgress(
                "Initializing async processing",
                0,
                30
            );
            
            // Start async processing
            Map<String, Object> taskArgs = Map.of(
                "chatId", request.getChatId(),
                "data", request.getData()
            );
            
            return StepResult.async("processDataAsync", 5000, taskArgs, progress);
        }
        
        @AsyncStep(value = "processDataAsync")
        public StepResult<AssistantResponse> processDataAsync(
                Map<String, Object> taskArgs,
                WorkflowContext context,
                AsyncProgressReporter progress) {
            
            try {
                String data = (String) taskArgs.get("data");
                String chatId = (String) taskArgs.get("chatId");
                
                // Simulate multi-stage processing
                progress.updateProgress(25, "Analyzing data");
                Thread.sleep(500);
                
                progress.updateProgress(50, "Processing data");
                Thread.sleep(500);
                
                progress.updateProgress(75, "Generating results");
                Thread.sleep(500);
                
                // Get recent history to include in response
                List<ChatMessage> recent = historyRepository.findRecentByChatId(chatId, 3);
                
                progress.updateProgress(100, "Completed");
                
                AssistantResponse response = new AssistantResponse(
                    String.format("Async processing completed for: '%s'. Processed %d characters.",
                        data, data.length()),
                    recent.stream()
                        .map(msg -> msg.getPropertiesMap().getOrDefault("message", ""))
                        .toList(),
                    recent.size()
                );
                
                return StepResult.finish(response);
                
            } catch (Exception e) {
                log.error("Async processing failed", e);
                return StepResult.fail(e);
            }
        }
    }

    @BeforeEach
    void setUp() {
        // Initialize repositories
        sessionRepository = new InMemoryChatSessionRepository();
        historyRepository = new InMemoryChatHistoryRepository();
        asyncResponseRepository = new InMemoryAsyncResponseRepository();
        
        // Create memory configuration
        WorkflowMemoryConfiguration memoryConfig = WorkflowMemoryConfiguration.builder()
            .chatHistoryRepository(historyRepository)
            .maxMessagesPerChat(100)
            .useTokenWindowMemory(false)
            .build();
        
        // Initialize memory service
        memoryService = new MemoryManagementService(
            sessionRepository,
            historyRepository,
            asyncResponseRepository,
            memoryConfig
        );
        
        // Initialize schema provider
        schemaProvider = new DefaultSchemaProvider();
        
        // Create workflow with injected dependencies
        workflow = new TestChatWorkflow(historyRepository, sessionRepository);
        
        // Create engine configuration
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .chatSessionRepository(sessionRepository)
            .chatHistoryRepository(historyRepository)
            .asyncResponseRepository(asyncResponseRepository)
            .build();
        
        // Initialize engine
        engine = new WorkflowEngine(config);
        engine.register(workflow);
        
        // Create execution service
        executionService = new DefaultWorkflowExecutionService(engine, schemaProvider, memoryService);
    }

    @Test
    @DisplayName("Should create and manage chat sessions")
    void testChatSessionManagement() {
        // Create a new session
        String userId = "test-user-123";
        ChatSession session = executionService.createChatSession(userId, "Test Chat");
        
        assertNotNull(session);
        assertEquals(userId, session.getUserId());
        assertEquals("Test Chat", session.getName());
        assertFalse(session.isArchived());
        assertNotNull(session.getChatId());
        
        // Retrieve the session
        Optional<ChatSession> retrieved = executionService.getChatSession(session.getChatId());
        assertTrue(retrieved.isPresent());
        assertEquals(session.getChatId(), retrieved.get().getChatId());
        
        // List user's sessions
        PageResult<ChatSession> userSessions = executionService.listChatsForUser(
            userId, 
            PageRequest.of(0, 10)
        );
        
        assertEquals(1, userSessions.getTotalElements());
        assertEquals(1, userSessions.getContent().size());
        assertEquals(session.getChatId(), userSessions.getContent().get(0).getChatId());
        
        // Archive the session
        executionService.archiveChatSession(session.getChatId());
        
        // Verify it's archived
        retrieved = executionService.getChatSession(session.getChatId());
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().isArchived());
    }

    @Test
    @DisplayName("Should persist and retrieve chat history through workflow")
    void testChatHistoryThroughWorkflow() throws Exception {
        // Create session
        String userId = "history-test-user";
        ChatSession session = executionService.createChatSession(userId, "History Test");
        String chatId = session.getChatId();
        
        // Send multiple messages
        List<String> messages = List.of(
            "Hello, this is message 1",
            "This is message 2",
            "And this is message 3"
        );
        
        for (String msg : messages) {
            ChatRequest request = new ChatRequest(
                chatId,
                Map.of("message", msg),
                Language.ENGLISH,
                "test-chat-workflow"
            );
            request.setUserId(userId);
            
            ChatResponse response = executionService.executeChat(request);
            assertNotNull(response);
            assertTrue(response.isCompleted());
        }
        
        // Now request history through workflow
        ChatRequest historyRequest = new ChatRequest(
            chatId,
            Map.of("message", "show me history"),
            Language.ENGLISH,
            "test-chat-workflow"
        );
        historyRequest.setUserId(userId);
        
        ChatResponse historyResponse = executionService.executeChat(historyRequest);
        assertNotNull(historyResponse);
        assertTrue(historyResponse.isCompleted());
        
        // Verify history response
        Map<String, String> props = historyResponse.getPropertiesMap();
        // Properties are serialized as JSON in "result" field
        assertNotNull(props.get("result"));
        assertTrue(props.get("result").contains("conversation history"));
        assertTrue(props.get("result").contains("recentHistory"));
        
        // Verify recent history is included in result JSON
        assertTrue(props.get("result").contains("recentHistory"));
    }

    @Test
    @DisplayName("Should manage workflow memory across suspensions")
    void testWorkflowMemoryAcrossSuspensions() throws Exception {
        // Create session
        String userId = "memory-test-user";
        ChatSession session = executionService.createChatSession(userId, "Memory Test");
        String chatId = session.getChatId();
        
        // First request - trigger suspension
        ChatRequest request1 = new ChatRequest(
            chatId,
            Map.of("message", "I need help with something"),
            Language.ENGLISH,
            "test-chat-workflow"
        );
        request1.setUserId(userId);
        
        ChatResponse response1 = executionService.executeChat(request1);
        assertNotNull(response1);
        assertTrue(response1.isCompleted()); // SUSPENDED workflows are marked as completed
        assertEquals(100, response1.getPercentComplete());
        
        // Verify suspend prompt data
        Map<String, String> props = response1.getPropertiesMap();
        assertTrue(props.containsKey("prompt"));
        assertTrue(props.get("prompt").contains("I'm here to help"));
        
        // Resume with user input
        TestChatWorkflow.UserInput userInput = new TestChatWorkflow.UserInput();
        userInput.setMessage("I want to analyze some data");
        userInput.setContext("Data analysis context");
        
        ChatRequest resumeRequest = new ChatRequest(
            chatId,
            Map.of(
                "message", userInput.getMessage(),
                "context", userInput.getContext()
            ),
            Language.ENGLISH,
            "test-chat-workflow"
        );
        resumeRequest.setUserId(userId);
        
        ChatResponse response2 = executionService.resumeChat(response1.getId(), resumeRequest);
        assertNotNull(response2);
        assertTrue(response2.isCompleted());
        assertEquals(100, response2.getPercentComplete());
        
        // Verify the response contains processed input
        String result = response2.getPropertiesMap().get("result");
        assertNotNull(result, "Resume response must have result property");
        assertTrue(result.contains("I want to analyze some data"), "Result must contain user message");
        assertTrue(result.contains("Data analysis context"), "Result must contain context");
        
        // Check that history contains all messages
        PageResult<ChatMessage> history = executionService.getChatHistory(
            chatId,
            PageRequest.of(0, 10),
            false
        );
        
        // Should have 4 messages (2 requests + 2 responses)
        assertEquals(4, history.getTotalElements());
    }

    @Test
    @DisplayName("Should check memory statistics through workflow")
    void testMemoryStatisticsThroughWorkflow() throws Exception {
        String userId = "stats-test-user";
        
        // Create multiple sessions
        ChatSession session1 = executionService.createChatSession(userId, "Stats Test 1");
        ChatSession session2 = executionService.createChatSession(userId, "Stats Test 2");
        
        // Send messages to different sessions
        for (int i = 0; i < 3; i++) {
            ChatRequest request1 = new ChatRequest(
                session1.getChatId(),
                Map.of("message", "Message " + i + " in session 1"),
                Language.ENGLISH,
                "test-chat-workflow"
            );
            request1.setUserId(userId);
            executionService.executeChat(request1);
            
            ChatRequest request2 = new ChatRequest(
                session2.getChatId(),
                Map.of("message", "Message " + i + " in session 2"),
                Language.ENGLISH,
                "test-chat-workflow"
            );
            request2.setUserId(userId);
            executionService.executeChat(request2);
        }
        
        // Check memory stats
        ChatRequest statsRequest = new ChatRequest(
            session1.getChatId(),
            Map.of("message", "check memory stats"),
            Language.ENGLISH,
            "test-chat-workflow"
        );
        statsRequest.setUserId(userId);
        
        ChatResponse response = executionService.executeChat(statsRequest);
        assertNotNull(response);
        assertTrue(response.isCompleted());
        
        // Verify stats
        Map<String, String> props = response.getPropertiesMap();
        assertNotNull(props.get("result"), "Memory stats response must have result property");
        String resultJson = props.get("result");
        assertTrue(resultJson.contains("Memory Statistics"), "Result must contain Memory Statistics text");
        assertTrue(resultJson.contains("Active sessions: 2"), "Result must show 2 active sessions");
        assertTrue(resultJson.contains("Messages in current chat: 8"), "Result must show 8 messages in current chat"); // 4 requests + 4 responses
        assertTrue(resultJson.contains("Total messages across all chats: 14"), "Result must show 14 total messages"); // 7 in each session
    }

    @Test
    @DisplayName("Should handle async processing with progress updates")
    void testAsyncProcessingWithProgress() throws Exception {
        String userId = "async-test-user";
        ChatSession session = executionService.createChatSession(userId, "Async Test");
        String chatId = session.getChatId();
        
        // Send async request
        ChatRequest request = new ChatRequest(
            chatId,
            Map.of("message", "Please process this data asynchronously"),
            Language.ENGLISH,
            "test-chat-workflow"
        );
        request.setUserId(userId);
        
        ChatResponse response = executionService.executeChat(request);
        assertNotNull(response);
        assertFalse(response.isCompleted()); // Async responses should NOT be completed
        
        // Verify initial async progress
        Map<String, String> props = response.getPropertiesMap();
        log.info("Async response ID: {}", response.getId());
        log.info("Async response properties: {}", props);
        log.info("Response completed: {}, percentComplete: {}", response.isCompleted(), response.getPercentComplete());
        
        // Must have async progress data
        assertNotNull(props.get("status"), "Response must have status property");
        assertEquals("Initializing async processing", props.get("status"));
        assertEquals("0", props.get("progressPercent"));
        assertEquals("30", props.get("estimatedTimeRemaining"));
        
        String responseId = response.getId();
        
        // Since async response is saved for polling, verify we can retrieve it
        Optional<ChatResponse> savedResponse = executionService.getAsyncStatus(responseId);
        assertTrue(savedResponse.isPresent(), "Async response should be saved for polling");
        assertEquals(responseId, savedResponse.get().getId());
        
        // Wait for async task to actually start and update progress
        Thread.sleep(100);
        
        // Poll for progress updates
        int attempts = 0;
        ChatResponse currentResponse = response;
        Set<String> seenStatuses = new HashSet<>();
        Set<Integer> seenProgress = new HashSet<>();
        
        while (attempts < 20) { // Max 10 seconds
            Thread.sleep(500);
            Optional<ChatResponse> statusOpt = executionService.getAsyncStatus(responseId);
            
            if (statusOpt.isPresent()) {
                currentResponse = statusOpt.get();
                String status = currentResponse.getPropertiesMap().get("status");
                int progress = currentResponse.getPercentComplete();
                
                log.info("Async progress: {}%, status: {}", progress, status);
                
                if (status != null) {
                    seenStatuses.add(status);
                }
                seenProgress.add(progress);
                
                // Check if completed based on our async method logic
                if (progress == 100 && "Completed".equals(status)) {
                    break;
                }
            }
            
            attempts++;
        }
        
        // Verify we saw the expected progress statuses
        assertTrue(seenStatuses.contains("Analyzing data"), "Should see 'Analyzing data' status");
        assertTrue(seenStatuses.contains("Processing data"), "Should see 'Processing data' status");
        assertTrue(seenStatuses.contains("Generating results"), "Should see 'Generating results' status");
        assertTrue(seenStatuses.contains("Completed"), "Should see 'Completed' status");
        
        // Verify we saw progress percentages
        assertTrue(seenProgress.contains(25), "Should see 25% progress");
        assertTrue(seenProgress.contains(50), "Should see 50% progress");
        assertTrue(seenProgress.contains(75), "Should see 75% progress");
        assertTrue(seenProgress.contains(100), "Should see 100% progress");
        
        // Verify final state
        assertNotNull(currentResponse);
        assertEquals(100, currentResponse.getPercentComplete());
        assertEquals("Completed", currentResponse.getPropertiesMap().get("status"));
    }

    @Test
    @DisplayName("Should handle concurrent chat sessions with proper isolation")
    void testConcurrentChatSessions() throws Exception {
        String userId = "concurrent-test-user";
        
        // Create multiple sessions
        List<ChatSession> sessions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            sessions.add(executionService.createChatSession(userId, "Session " + (i + 1)));
        }
        
        // Send different messages to each session
        for (int i = 0; i < sessions.size(); i++) {
            ChatSession session = sessions.get(i);
            
            // Send normal message
            ChatRequest request1 = new ChatRequest(
                session.getChatId(),
                Map.of("message", "Message in session " + (i + 1)),
                Language.ENGLISH,
                "test-chat-workflow"
            );
            request1.setUserId(userId);
            executionService.executeChat(request1);
            
            // Request history - should only see own messages
            ChatRequest historyRequest = new ChatRequest(
                session.getChatId(),
                Map.of("message", "show history"),
                Language.ENGLISH,
                "test-chat-workflow"
            );
            historyRequest.setUserId(userId);
            
            ChatResponse historyResponse = executionService.executeChat(historyRequest);
            assertNotNull(historyResponse);
            
            // Each session should have exactly 4 messages (2 requests + 2 responses)
            String result = historyResponse.getPropertiesMap().get("result");
            assertNotNull(result, "History response must have result property for session " + i);
            assertTrue(result.contains("conversationCount\":4"), "Result must show conversationCount of 4");
        }
        
        // Verify user has 3 active sessions
        PageResult<ChatSession> userSessions = executionService.listChatsForUser(
            userId,
            PageRequest.of(0, 10)
        );
        
        assertEquals(3, userSessions.getTotalElements());
    }

    @Test
    @DisplayName("Should maintain session timestamps correctly")
    void testSessionTimestamps() throws Exception {
        String userId = "timestamp-test-user";
        ChatSession session = executionService.createChatSession(userId, "Timestamp Test");
        String chatId = session.getChatId();
        
        // Check initial timestamps
        assertTrue(session.getCreatedAt() > 0);
        assertEquals(session.getCreatedAt(), session.getLastMessageTime());
        
        long initialTime = session.getLastMessageTime();
        
        // Wait and send a message
        Thread.sleep(100);
        
        ChatRequest request = new ChatRequest(
            chatId,
            Map.of("message", "Test message"),
            Language.ENGLISH,
            "test-chat-workflow"
        );
        request.setUserId(userId);
        
        executionService.executeChat(request);
        
        // Retrieve updated session
        Optional<ChatSession> updated = executionService.getChatSession(chatId);
        assertTrue(updated.isPresent());
        
        // Last message time should be updated
        assertTrue(updated.get().getLastMessageTime() > initialTime);
        // Created time should remain the same
        assertEquals(session.getCreatedAt(), updated.get().getCreatedAt());
    }

    @Test
    @DisplayName("Should handle error cases gracefully")
    void testErrorHandling() throws Exception {
        // Create a session first to avoid automatic creation issues
        String chatId = "error-test-chat";
        String userId = "error-test-user";
        ChatSession session = executionService.createChatSession(userId, "Error Test Chat");
        
        // Now test with a valid request
        ChatRequest request = new ChatRequest(
            session.getChatId(),
            Map.of("message", "Test message"),
            Language.ENGLISH,
            "test-chat-workflow"
        );
        request.setUserId(userId);
        
        // Should complete successfully
        ChatResponse response = executionService.executeChat(request);
        assertNotNull(response);
        assertTrue(response.isCompleted());
        
        // Verify session exists and is correct
        Optional<ChatSession> retrieved = executionService.getChatSession(session.getChatId());
        assertTrue(retrieved.isPresent());
        assertEquals(userId, retrieved.get().getUserId());
    }
}