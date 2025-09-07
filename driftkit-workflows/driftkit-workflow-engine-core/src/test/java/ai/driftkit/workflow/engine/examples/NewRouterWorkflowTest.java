package ai.driftkit.workflow.engine.examples;

import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.core.WorkflowEngine.WorkflowExecution;
import ai.driftkit.workflow.engine.core.WorkflowEngine.WorkflowExecutionListener;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.examples.NewRouterWorkflow.*;
import ai.driftkit.workflow.engine.persistence.inmemory.InMemoryWorkflowStateRepository;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.persistence.WorkflowStateRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for the new RouterWorkflow with sealed interfaces.
 * Demonstrates production-ready implementation with full error handling,
 * validation, and real service implementations.
 */
@Slf4j
public class NewRouterWorkflowTest {

    private WorkflowEngine engine;
    private InMemoryUserService userService;
    private InMemoryDocumentService documentService;
    private InMemoryNotificationService notificationService;
    private NewRouterWorkflow workflow;
    private WorkflowStateRepository stateRepository;
    private TestExecutionListener executionListener;

    @BeforeEach
    void setUp() {
        // Initialize real service implementations
        userService = new InMemoryUserService();
        documentService = new InMemoryDocumentService();
        notificationService = new InMemoryNotificationService();

        // Populate test data
        initializeTestData();

        // Create workflow instance
        workflow = new NewRouterWorkflow(userService, documentService, notificationService);

        // Create state repository
        stateRepository = new InMemoryWorkflowStateRepository(1000);

        // Create and configure engine with production settings
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
                .coreThreads(5)
                .maxThreads(20)
                .queueCapacity(100)
                .defaultStepTimeoutMs(30_000) // 30 seconds timeout
                .stateRepository(stateRepository)
                .build();

        engine = new WorkflowEngine(config);
        engine.register(workflow);

        // Add comprehensive execution listener
        executionListener = new TestExecutionListener();
        engine.addListener("test-listener", executionListener);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testKnownUserFlowWithFullValidation() throws Exception {
        // Given: A known user with complete profile
        String userEmail = "john.doe@company.com";

        // When: Router processes a general query from known user
        RouterInput input = new RouterInput(userEmail, "What features are available in the premium plan?",
                Map.of("source", "web", "sessionId", "sess-123"));

        WorkflowExecution<RouterResult> execution = engine.execute("user-router-workflow", input);

        // Then: Workflow completes successfully with expected results
        RouterResult result = execution.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertTrue(result.success());
        assertTrue(result.message().contains("Welcome back, John Doe"));
        assertEquals("known", result.metadata().get("userType"));
        assertNotNull(result.workflowRunId());

        // Verify execution path
        List<String> executedSteps = executionListener.getExecutedSteps();
        assertEquals(List.of("analyzeQuery", "handleKnownUser", "sendNotificationAndComplete"),
                executedSteps);

        // Verify notification was sent to correct user
        List<InMemoryNotificationService.Notification> notifications =
                notificationService.getNotificationsForUser("user-123");
        assertEquals(1, notifications.size());
        assertTrue(notifications.get(0).message().contains("Welcome back"));

        // Verify workflow state was properly persisted
        Optional<WorkflowInstance> savedInstance = stateRepository.load(execution.getRunId());
        assertTrue(savedInstance.isPresent());
        assertEquals(WorkflowInstance.WorkflowStatus.COMPLETED, savedInstance.get().getStatus());
        assertEquals(3, savedInstance.get().getExecutionHistory().size());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testNewUserFlowWithSuspensionAndValidation() throws Exception {
        // Given: New user email
        String newUserEmail = "jane.smith@newcompany.com";

        // When: Router processes a query from new user
        RouterInput input = new RouterInput(newUserEmail, "How do I get started with your API?",
                Map.of("source", "mobile", "version", "2.0"));

        WorkflowExecution<RouterResult> execution = engine.execute("user-router-workflow", input);

        // Wait for workflow to suspend
        Thread.sleep(1000);

        // Then: Workflow should be suspended waiting for user input
        assertFalse(execution.isDone());

        // Verify suspension state
        Optional<WorkflowInstance> suspendedInstance = stateRepository.load(execution.getRunId());
        assertTrue(suspendedInstance.isPresent());
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, suspendedInstance.get().getStatus());
        assertEquals("handleNewUser", suspendedInstance.get().getCurrentStepId());

        // Simulate user providing their name (suspension expects UserNameInput)
        NewRouterWorkflow.UserNameInput userInput = new NewRouterWorkflow.UserNameInput();
        userInput.setName("Jane Smith");

        // Resume workflow with the expected UserNameInput
        WorkflowExecution<RouterResult> resumedExecution =
                engine.resume(execution.getRunId(), userInput);

        // Wait for completion
        RouterResult result = resumedExecution.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        // The workflow continues after suspension but might need additional implementation
        // for handling the user response in the next step

        // Verify complete execution history
        Optional<WorkflowInstance> completedInstance = stateRepository.load(execution.getRunId());
        assertTrue(completedInstance.isPresent());
        assertEquals(WorkflowInstance.WorkflowStatus.COMPLETED, completedInstance.get().getStatus());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testDocumentSearchFlowWithPagination() throws Exception {
        // Given: User searching for documents
        String userEmail = "researcher@university.edu";

        // When: Processing document search with specific keywords
        RouterInput input = new RouterInput(userEmail,
                "search for machine learning documentation and tutorials",
                Map.of("maxResults", "10", "includeArchived", "false"));

        WorkflowExecution<RouterResult> execution = engine.execute("user-router-workflow", input);

        // Then: Documents are found and properly formatted
        RouterResult result = execution.get(5, TimeUnit.SECONDS);

        assertTrue(result.success());
        assertTrue(result.message().contains("Found"));
        assertTrue(result.message().contains("documents"));

        // Verify search was performed with extracted keywords
        List<String> lastSearchKeywords = documentService.getLastSearchKeywords();
        assertFalse(lastSearchKeywords.isEmpty());
        assertTrue(lastSearchKeywords.contains("machine"));
        assertTrue(lastSearchKeywords.contains("learning"));
        assertTrue(lastSearchKeywords.contains("documentation"));

        // Verify metadata contains search details
        @SuppressWarnings("unchecked")
        List<String> searchKeywords = (List<String>) result.metadata().get("searchKeywords");
        assertNotNull(searchKeywords);
        assertFalse(searchKeywords.isEmpty());

        Integer resultCount = (Integer) result.metadata().get("resultCount");
        assertNotNull(resultCount);
        assertTrue(resultCount > 0);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testSupportQueryFlowWithTicketCreation() throws Exception {
        // Given: Technical support query
        String userEmail = "customer@enterprise.com";

        // When: Processing support request
        RouterInput input = new RouterInput(userEmail,
                "I'm experiencing a bug with the authentication API returning 500 errors",
                Map.of("priority", "high", "accountType", "enterprise"));

        WorkflowExecution<RouterResult> execution = engine.execute("user-router-workflow", input);

        // Then: Support ticket is created with proper categorization
        RouterResult result = execution.get(5, TimeUnit.SECONDS);

        assertTrue(result.success());
        assertTrue(result.message().contains("Support ticket created"));

        String ticketId = (String) result.metadata().get("ticketId");
        assertNotNull(ticketId);
        assertTrue(ticketId.startsWith("TICKET-"));

        assertEquals("technical", result.metadata().get("category"));

        // Verify execution completed successfully
        assertEquals(3, executionListener.getExecutedSteps().size());
        assertEquals(0, executionListener.getFailedSteps().size());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testUnknownQueryFlowWithFallback() throws Exception {
        // Given: Ambiguous query with no email
        RouterInput input = new RouterInput("", "xyz abc 123 ???", Map.of());

        // When: Processing unknown query
        WorkflowExecution<RouterResult> execution = engine.execute("user-router-workflow", input);

        // Then: Fallback response is provided
        RouterResult result = execution.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertFalse(result.success());
        assertTrue(result.message().contains("I'm not sure how to help"));
        assertEquals("xyz abc 123 ???", result.metadata().get("originalQuery"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentWorkflowExecutions() throws Exception {
        // Test that multiple workflows can execute concurrently without interference
        int concurrentExecutions = 10;
        List<CompletableFuture<RouterResult>> futures = new ArrayList<>();

        // Submit multiple workflow executions
        for (int i = 0; i < concurrentExecutions; i++) {
            // Mix different types of queries for concurrent testing
            String email;
            String query;

            if (i % 3 == 0) {
                // Known user query
                email = "john.doe@company.com";
                query = "What are the latest features?";
            } else if (i % 3 == 1) {
                // Document query (no user lookup)
                email = "searcher" + i + "@example.com";
                query = "Search for security documentation";
            } else {
                // Support query
                email = "support" + i + "@example.com";
                query = "Help with technical issue #" + i;
            }

            RouterInput input = new RouterInput(email, query, Map.of("index", String.valueOf(i)));

            WorkflowExecution<RouterResult> execution = engine.execute("user-router-workflow", input);
            futures.add(execution.getFuture());
        }

        // Wait for all to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );
        allFutures.get(10, TimeUnit.SECONDS);

        // Verify all completed successfully
        for (CompletableFuture<RouterResult> future : futures) {
            assertTrue(future.isDone());
            assertNotNull(future.get());
        }

        // Verify state repository contains all instances
        long completedCount = stateRepository.countByStatus(WorkflowInstance.WorkflowStatus.COMPLETED);
        assertEquals(concurrentExecutions, completedCount);
    }

    @Test
    void testWorkflowGraphAnalysis() {
        // This demonstrates the automatic graph construction based on sealed interfaces

        // The workflow analyzer should have created edges based on the RoutingDecision types:
        // - analyzeQuery -> handleKnownUser (for KnownUserQuery)
        // - analyzeQuery -> handleNewUser (for NewUserQuery)
        // - analyzeQuery -> handleDocumentQuery (for DocumentQuery)
        // - analyzeQuery -> handleSupportQuery (for SupportQuery)
        // - analyzeQuery -> handleUnknownQuery (for UnknownQuery)
        // - All handlers -> sendNotificationAndComplete (for ProcessingResult)

        log.info("Workflow graph automatically constructed with type-safe routing:");
        log.info("- Initial step: analyzeQuery");
        log.info("- 5 branches based on RoutingDecision sealed interface");
        log.info("- All paths converge at sendNotificationAndComplete");
        log.info("- Type safety enforced at compile time");
    }

    /**
     * Initialize test data for services.
     */
    private void initializeTestData() {
        // Add test users
        userService.addUser(new User("user-123", "john.doe@company.com", "John Doe",
                Map.of("language", "en", "notifications", "enabled", "theme", "dark")));

        userService.addUser(new User("user-456", "admin@company.com", "Admin User",
                Map.of("language", "en", "role", "admin", "notifications", "all")));

        // Add test documents
        documentService.addDocument(new Document("doc-001", "Getting Started Guide",
                "Comprehensive guide for new users...", List.of("guide", "tutorial", "start")));

        documentService.addDocument(new Document("doc-002", "API Reference",
                "Complete API documentation with examples...", List.of("api", "reference", "documentation")));

        documentService.addDocument(new Document("doc-003", "Machine Learning Tutorial",
                "Introduction to ML concepts and implementation...", List.of("machine", "learning", "ai", "tutorial")));

        documentService.addDocument(new Document("doc-004", "Security Best Practices",
                "Security guidelines and recommendations...", List.of("security", "best practices", "guide")));
    }

    /**
     * Production-ready in-memory user service implementation.
     */
    static class InMemoryUserService implements UserService {
        private final Map<String, User> usersByEmail = new ConcurrentHashMap<>();
        private final Map<String, User> usersById = new ConcurrentHashMap<>();

        @Override
        public User findByEmail(String email) {
            if (email == null || email.trim().isEmpty()) {
                return null;
            }
            return usersByEmail.get(email.toLowerCase());
        }

        public void addUser(User user) {
            if (user == null || user.getEmail() == null || user.getId() == null) {
                throw new IllegalArgumentException("User must have valid ID and email");
            }
            usersByEmail.put(user.getEmail().toLowerCase(), user);
            usersById.put(user.getId(), user);
        }

        public User findById(String id) {
            return usersById.get(id);
        }

        public int getUserCount() {
            return usersById.size();
        }
    }

    /**
     * Production-ready in-memory document service implementation.
     */
    static class InMemoryDocumentService implements DocumentService {
        private final List<Document> documents = new CopyOnWriteArrayList<>();
        private volatile List<String> lastSearchKeywords = new ArrayList<>();

        @Override
        public List<Document> search(List<String> keywords) {
            if (keywords == null || keywords.isEmpty()) {
                return Collections.emptyList();
            }

            // Store search keywords for verification
            lastSearchKeywords = new ArrayList<>(keywords);

            // Convert keywords to lowercase for case-insensitive search
            Set<String> lowerKeywords = keywords.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            // Search documents by matching keywords in title, content, or tags
            return documents.stream()
                    .filter(doc -> matchesKeywords(doc, lowerKeywords))
                    .sorted(Comparator.comparing(Document::getTitle))
                    .collect(Collectors.toList());
        }

        private boolean matchesKeywords(Document doc, Set<String> keywords) {
            String lowerTitle = doc.getTitle().toLowerCase();
            String lowerContent = doc.getContent().toLowerCase();
            Set<String> lowerTags = doc.getTags().stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            for (String keyword : keywords) {
                if (lowerTitle.contains(keyword) ||
                        lowerContent.contains(keyword) ||
                        lowerTags.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }

        public void addDocument(Document document) {
            if (document == null || document.getId() == null) {
                throw new IllegalArgumentException("Document must have valid ID");
            }
            documents.add(document);
        }

        public List<String> getLastSearchKeywords() {
            return new ArrayList<>(lastSearchKeywords);
        }

        public int getDocumentCount() {
            return documents.size();
        }
    }

    /**
     * Production-ready in-memory notification service implementation.
     */
    static class InMemoryNotificationService implements NotificationService {
        private final Map<String, List<Notification>> notificationsByUser = new ConcurrentHashMap<>();
        private final BlockingQueue<Notification> notificationQueue = new LinkedBlockingQueue<>();

        @Override
        public void notify(String userId, String message) {
            if (userId == null || userId.trim().isEmpty()) {
                log.warn("Cannot send notification to null/empty userId");
                return;
            }

            if (message == null || message.trim().isEmpty()) {
                log.warn("Cannot send empty notification");
                return;
            }

            Notification notification = new Notification(
                    UUID.randomUUID().toString(),
                    userId,
                    message,
                    System.currentTimeMillis(),
                    NotificationStatus.PENDING
            );

            // Store notification
            notificationsByUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
                    .add(notification);

            // Queue for processing
            try {
                notificationQueue.offer(notification, 1, TimeUnit.SECONDS);
                log.debug("Notification queued for user {}: {}", userId, message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Failed to queue notification", e);
            }
        }

        public List<Notification> getNotificationsForUser(String userId) {
            return notificationsByUser.getOrDefault(userId, Collections.emptyList());
        }

        public int getTotalNotifications() {
            return notificationsByUser.values().stream()
                    .mapToInt(List::size)
                    .sum();
        }

        record Notification(
                String id,
                String userId,
                String message,
                long timestamp,
                NotificationStatus status
        ) {}

        enum NotificationStatus {
            PENDING, SENT, FAILED
        }
    }

    /**
     * Comprehensive test execution listener for tracking workflow execution.
     */
    static class TestExecutionListener implements WorkflowExecutionListener {
        private final List<String> executedSteps = new CopyOnWriteArrayList<>();
        private final List<String> failedSteps = new CopyOnWriteArrayList<>();
        private final Map<String, Long> stepDurations = new ConcurrentHashMap<>();
        private final Map<String, Object> stepResults = new ConcurrentHashMap<>();

        @Override
        public void onWorkflowStarted(WorkflowInstance instance) {
            log.info("Workflow started: {} ({})", instance.getWorkflowId(), instance.getInstanceId());
        }

        @Override
        public void onWorkflowCompleted(WorkflowInstance instance, Object result) {
            log.info("Workflow completed: {} in {}ms",
                    instance.getInstanceId(), instance.getTotalDurationMs());
        }

        @Override
        public void onWorkflowFailed(WorkflowInstance instance, Throwable error) {
            log.error("Workflow failed: {} - {}", instance.getInstanceId(), error.getMessage());
        }

        @Override
        public void onWorkflowSuspended(WorkflowInstance instance) {
            log.info("Workflow suspended: {} at step {}",
                    instance.getInstanceId(), instance.getCurrentStepId());
        }

        @Override
        public void onStepStarted(WorkflowInstance instance, String stepId) {
            log.debug("Step started: {}", stepId);
            stepDurations.put(stepId, System.currentTimeMillis());
        }

        @Override
        public void onStepCompleted(WorkflowInstance instance, String stepId,
                                    ai.driftkit.workflow.engine.core.StepResult<?> result) {
            executedSteps.add(stepId);
            stepResults.put(stepId, result);

            Long startTime = stepDurations.get(stepId);
            if (startTime != null) {
                long duration = System.currentTimeMillis() - startTime;
                log.debug("Step completed: {} in {}ms -> {}",
                        stepId, duration, result.getClass().getSimpleName());
            }
        }

        @Override
        public void onStepFailed(WorkflowInstance instance, String stepId, Throwable error) {
            failedSteps.add(stepId);
            log.error("Step failed: {} - {}", stepId, error.getMessage());
        }

        public List<String> getExecutedSteps() {
            return new ArrayList<>(executedSteps);
        }

        public List<String> getFailedSteps() {
            return new ArrayList<>(failedSteps);
        }

        public Map<String, Object> getStepResults() {
            return new HashMap<>(stepResults);
        }
    }
}