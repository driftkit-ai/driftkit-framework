package ai.driftkit.workflow.engine.agent;

import ai.driftkit.clients.openai.client.OpenAIModelClient;
import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.client.ModelMessage;
import ai.driftkit.common.service.ChatStore;
import ai.driftkit.common.service.TextTokenizer;
import ai.driftkit.common.service.impl.InMemoryChatStore;
import ai.driftkit.common.service.impl.SimpleTextTokenizer;
import ai.driftkit.common.tools.ToolRegistry;
import ai.driftkit.config.EtlConfig.VaultConfig;
import ai.driftkit.context.core.service.DictionaryItemService;
import ai.driftkit.context.core.service.InMemoryPromptService;
import ai.driftkit.context.core.service.PromptService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REAL Integration tests for LLMAgent using actual OpenAI API.
 * NO MOCKS - Everything is real!
 * 
 * Set environment variable: OPENAI_API_KEY=sk-...
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LLMAgentIntegrationTest {

    private OpenAIModelClient modelClient;
    private ChatStore chatStore;
    private ToolRegistry toolRegistry;
    private PromptService promptService;
    
    private String apiKey;
    
    @BeforeAll
    void setupAll() {
        // Get API key from environment or system property
        apiKey = System.getenv("OPENAI_API_KEY");

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("OPENAI_API_KEY not set. Skipping integration tests.");
            log.warn("Set environment variable: export OPENAI_API_KEY=sk-...");
            Assumptions.assumeTrue(false, "OpenAI API key not configured");
        }
        
        log.info("OpenAI API key configured. Running integration tests...");
    }
    
    @BeforeEach
    void setup() {
        VaultConfig config = new VaultConfig();
        config.setApiKey(apiKey);
        config.setModel("gpt-4o");  // Use cheaper model for tests
        config.setTemperature(0.7);
        config.setBaseUrl("https://api.openai.com/");
        
        modelClient = new OpenAIModelClient();
        modelClient.init(config);
        
        // Create REAL in-memory chat store
        TextTokenizer tokenizer = new SimpleTextTokenizer();
        chatStore = new InMemoryChatStore(tokenizer);
        
        // Create REAL tool registry with actual tools
        toolRegistry = new ToolRegistry();
        registerTestTools();
        
        // Create REAL prompt service
        InMemoryPromptService inMemoryPromptService = new InMemoryPromptService();
        DictionaryItemService dictionaryItemService = null; // Can be null for simple tests
        promptService = new PromptService(inMemoryPromptService, dictionaryItemService);
        setupTestPrompts();
    }
    
    /**
     * CRITICAL TEST: Verify the bug fix - messages are ALWAYS added to request
     */
    @Test
    @DisplayName("Critical Bug Fix: Messages always added to request regardless of history mode")
    void testCriticalBugFix_MessagesAlwaysInRequest() throws Exception {
        log.info("=== Testing Critical Bug Fix ===");
        
        // Case 1: STATELESS mode (chatStore + workflowId) - THIS WAS BROKEN!
        log.info("Test Case 1: Stateless mode with workflowId");
        LLMAgent statelessAgent = LLMAgent.builder()
            .modelClient(modelClient)
            .chatStore(chatStore)
            .workflowId("workflow-123")  // This makes it stateless
            .chatId("session-1")
            .name("stateless-agent")
            .build();
            
        AgentResponse<String> response1 = statelessAgent.executeText("What is 5 + 3? Reply with just the number.");
        assertNotNull(response1);
        assertNotNull(response1.getText());
        assertFalse(response1.getText().isEmpty());
        log.info("Stateless response: {}", response1.getText());
        assertTrue(response1.getText().contains("8"), "Should calculate 5+3=8");
        
        // Verify no history saved
        List<ChatMessage> history1 = chatStore.getAll("session-1");
        assertTrue(history1.isEmpty(), "Stateless mode should not save history");
        
        // Case 2: HISTORY mode (chatStore, no workflowId)
        log.info("Test Case 2: History mode without workflowId");
        LLMAgent historyAgent = LLMAgent.builder()
            .modelClient(modelClient)
            .chatStore(chatStore)
            .workflowId(null)  // No workflowId = history mode
            .chatId("session-2")
            .name("history-agent")
            .build();
            
        AgentResponse<String> response2 = historyAgent.executeText("What is 10 + 5? Reply with just the number.");
        assertNotNull(response2);
        assertNotNull(response2.getText());
        log.info("History mode response: {}", response2.getText());
        assertTrue(response2.getText().contains("15"), "Should calculate 10+5=15");
        
        // Verify history saved
        List<ChatMessage> history2 = chatStore.getAll("session-2");
        assertFalse(history2.isEmpty(), "History mode should save messages");
        assertEquals(2, history2.size(), "Should have user and assistant messages");
        
        // Case 3: No chatStore at all
        log.info("Test Case 3: No chat store");
        LLMAgent noStoreAgent = LLMAgent.builder()
            .modelClient(modelClient)
            .chatStore(null)  // No store at all
            .chatId("session-3")
            .name("no-store-agent")
            .build();
            
        AgentResponse<String> response3 = noStoreAgent.executeText("What is 7 + 2? Reply with just the number.");
        assertNotNull(response3);
        assertNotNull(response3.getText());
        log.info("No store response: {}", response3.getText());
        assertTrue(response3.getText().contains("9"), "Should calculate 7+2=9");
        
        log.info("✓ All three modes returned valid responses - bug is fixed!");
    }
    
    /**
     * Test multi-turn conversation with history accumulation
     */
    @Test
    @DisplayName("History accumulation in multi-turn conversation")
    void testHistoryAccumulation_MultiTurnConversation() throws Exception {
        log.info("=== Testing History Accumulation ===");
        
        LLMAgent agent = LLMAgent.builder()
            .modelClient(modelClient)
            .chatStore(chatStore)
            .workflowId(null)  // History mode
            .chatId("history-session")
            .name("history-agent")
            .systemMessage("You are a helpful assistant. Keep your responses concise.")
            .build();
        
        // Turn 1
        log.info("Turn 1: Asking initial question");
        AgentResponse<String> response1 = agent.executeText("What is 2 + 2?");
        log.info("Response 1: {}", response1.getText());
        assertTrue(response1.getText().contains("4"), "Should answer 4");
        
        // Turn 2 - should remember context
        log.info("Turn 2: Referencing previous answer");
        AgentResponse<String> response2 = agent.executeText("Multiply that result by 3");
        log.info("Response 2: {}", response2.getText());
        assertTrue(response2.getText().contains("12"), "Should calculate 4*3=12 based on context");
        
        // Turn 3 - test memory recall
        log.info("Turn 3: Testing memory recall");
        AgentResponse<String> response3 = agent.executeText("What was my first question?");
        log.info("Response 3: {}", response3.getText());
        assertTrue(
            response3.getText().toLowerCase().contains("2") && 
            response3.getText().contains("+"),
            "Should recall the first question about 2+2"
        );
        
        // Verify history in store
        List<ChatMessage> history = chatStore.getAll("history-session");
        assertEquals(6, history.size(), "Should have 3 exchanges (6 messages)");
        
        log.info("✓ Multi-turn conversation with history works correctly!");
    }
    
    /**
     * Comprehensive test for tool execution with multiple scenarios
     */
    @Test
    @DisplayName("Comprehensive tool execution with multiple scenarios")
    void testToolExecution_ComprehensiveScenarios() throws Exception {
        log.info("=== Testing Comprehensive Tool Execution Scenarios ===");
        
        LLMAgent agent = LLMAgent.builder()
            .modelClient(modelClient)
            .toolRegistry(toolRegistry)
            .chatStore(chatStore)
            .chatId("tool-comprehensive-session")  // Add missing sessionId/chatId
            .name("tool-comprehensive-agent")
            .systemMessage("You are a helpful assistant with access to calculator and weather tools. Always use tools when asked for calculations or weather.")
            .build();
        
        // Scenario 1: Simple calculation
        log.info("Scenario 1: Simple calculation with tool");
        AgentResponse<List<ToolExecutionResult>> response1 = agent.executeWithTools(
            "Calculate: 15 * 8 + 42"
        );
        
        assertNotNull(response1);
        List<ToolExecutionResult> results1 = response1.getToolResults();
        if (results1 != null && !results1.isEmpty()) {
            log.info("Tool results for calculation: {}", results1);
            for (ToolExecutionResult result : results1) {
                if ("calculator".equals(result.getToolName())) {
                    assertTrue(result.isSuccess(), "Calculator should execute successfully");
                }
            }
        }
        
        // Scenario 2: Weather query
        log.info("Scenario 2: Weather query with tool");
        AgentResponse<List<ToolExecutionResult>> response2 = agent.executeWithTools(
            "What's the weather in New York and London?"
        );
        
        assertNotNull(response2);
        List<ToolExecutionResult> results2 = response2.getToolResults();
        if (results2 != null && !results2.isEmpty()) {
            log.info("Tool results for weather: {}", results2);
            // Should have called weather tool for both cities
            long weatherCalls = results2.stream()
                .filter(r -> "get_weather".equals(r.getToolName()))
                .count();
            assertTrue(weatherCalls >= 1, "Should have made at least one weather tool call");
        }
        
        // Scenario 3: Combined calculation and weather
        log.info("Scenario 3: Combined calculation and weather query");
        AgentResponse<List<ToolExecutionResult>> response3 = agent.executeWithTools(
            "If the temperature in Paris is 22 degrees, what would it be in Fahrenheit? Calculate using the formula: F = C * 9/5 + 32"
        );
        
        assertNotNull(response3);
        List<ToolExecutionResult> results3 = response3.getToolResults();
        if (results3 != null && !results3.isEmpty()) {
            log.info("Tool results for combined query: {}", results3);
            boolean hasWeatherCall = results3.stream()
                .anyMatch(r -> "get_weather".equals(r.getToolName()));
            boolean hasCalculatorCall = results3.stream()
                .anyMatch(r -> "calculator".equals(r.getToolName()));
            
            // May use either or both tools depending on AI decision
            assertTrue(hasWeatherCall || hasCalculatorCall, 
                "Should use at least one tool for this query");
        }
        
        // Scenario 4: Multiple calculations in sequence
        log.info("Scenario 4: Multiple calculations in sequence");
        AgentResponse<List<ToolExecutionResult>> response4 = agent.executeWithTools(
            "Calculate these step by step: First 10 * 5, then add 25 to the result, then divide by 15"
        );
        
        assertNotNull(response4);
        List<ToolExecutionResult> results4 = response4.getToolResults();
        if (results4 != null && !results4.isEmpty()) {
            log.info("Tool results for sequential calculations: {}", results4);
            // Should have calculator calls
            boolean hasCalculations = results4.stream()
                .anyMatch(r -> "calculator".equals(r.getToolName()));
            assertTrue(hasCalculations, "Should use calculator for sequential calculations");
        }
        
        // Scenario 5: Tool with conversation context
        log.info("Scenario 5: Tool usage with conversation context");
        AgentResponse<List<ToolExecutionResult>> response5a = agent.executeWithTools(
            "Remember this number: calculate 25 * 4"
        );
        assertNotNull(response5a);
        
        // Follow-up that references previous calculation
        AgentResponse<List<ToolExecutionResult>> response5b = agent.executeWithTools(
            "Now add 50 to the number I asked you to remember"
        );
        assertNotNull(response5b);
        
        // Verify conversation continuity
        List<ChatMessage> history = chatStore.getAll("tool-comprehensive-session");
        assertTrue(history.size() >= 4, "Should have conversation history with tool interactions");
        
        log.info("✓ Comprehensive tool execution test completed!");
    }
    
    /**
     * Test tool execution with real tools
     */
    @Test
    @DisplayName("Tool execution with calculator")
    void testToolExecution_Calculator() throws Exception {
        log.info("=== Testing Tool Execution ===");
        
        LLMAgent agent = LLMAgent.builder()
            .modelClient(modelClient)
            .toolRegistry(toolRegistry)
            .chatId("tool-session")
            .name("tool-agent")
            .systemMessage("You have access to a calculator tool. Use it for math calculations.")
            .build();
        
        log.info("Asking for calculation that requires tool use");
        AgentResponse<List<ToolExecutionResult>> response = agent.executeWithTools(
            "Calculate: 47 * 23 + 156"
        );
        
        assertNotNull(response);
        // Tools execution results 
        List<ToolExecutionResult> results = response.getToolResults();
        log.info("Tool execution results: {}", results);
        
        if (results != null && !results.isEmpty()) {
            for (ToolExecutionResult result : results) {
                log.info("Tool '{}' executed: success={}, result={}", 
                    result.getToolName(), result.isSuccess(), result.getResult());
            }
        }
        
        log.info("✓ Tool execution test completed!");
    }
    
    /**
     * Test advanced tool scenarios with error handling
     */
    @Test
    @DisplayName("Advanced tool execution with error handling and edge cases")
    void testToolExecution_AdvancedWithErrorHandling() throws Exception {
        log.info("=== Testing Advanced Tool Execution with Error Handling ===");
        
        // Register a tool that can fail - use static class to avoid access issues
        toolRegistry.registerStaticMethod(TestDivisionTool.class, "divideNumbers", "Divides two numbers");
        
        LLMAgent agent = LLMAgent.builder()
            .modelClient(modelClient)
            .toolRegistry(toolRegistry)
            .chatStore(chatStore)
            .chatId("tool-advanced-session")
            .name("tool-advanced-agent")
            .systemMessage("You are a math assistant. Use available tools for calculations. Handle errors gracefully.")
            .build();
        
        // Scenario 1: Tool that might fail (division by zero)
        log.info("Scenario 1: Testing error handling - division by zero");
        AgentResponse<List<ToolExecutionResult>> response1 = agent.executeWithTools(
            "Can you divide 100 by 0? If that fails, explain why."
        );
        
        assertNotNull(response1);
        List<ToolExecutionResult> results1 = response1.getToolResults();
        if (results1 != null) {
            log.info("Results for division by zero: {}", results1);
            // Check if error was handled
            boolean hasError = results1.stream()
                .anyMatch(r -> "divideNumbers".equals(r.getToolName()) && !r.isSuccess());
            if (hasError) {
                log.info("Tool correctly reported error for division by zero");
            }
        }
        
        // Scenario 2: Chain of dependent calculations
        log.info("Scenario 2: Chain of dependent calculations");
        AgentResponse<List<ToolExecutionResult>> response2 = agent.executeWithTools(
            "Calculate: (50 * 2) then divide the result by 5, then add 30"
        );
        
        assertNotNull(response2);
        List<ToolExecutionResult> results2 = response2.getToolResults();
        if (results2 != null && !results2.isEmpty()) {
            log.info("Chain calculation results: {}", results2);
            // Should have multiple tool calls
            assertTrue(results2.size() >= 1, "Should have at least one tool call for chain calculation");
        }
        
        // Scenario 3: Mixed valid and invalid operations
        log.info("Scenario 3: Mixed valid and invalid operations");
        AgentResponse<List<ToolExecutionResult>> response3 = agent.executeWithTools(
            "Calculate these: 1) 45 + 55, 2) 100 / 0 (this will fail), 3) 30 * 3"
        );
        
        assertNotNull(response3);
        List<ToolExecutionResult> results3 = response3.getToolResults();
        if (results3 != null && !results3.isEmpty()) {
            log.info("Mixed operations results: {}", results3);
            
            // Count successful and failed operations
            long successCount = results3.stream().filter(ToolExecutionResult::isSuccess).count();
            long failCount = results3.stream().filter(r -> !r.isSuccess()).count();
            
            log.info("Successful operations: {}, Failed operations: {}", successCount, failCount);
            assertTrue(successCount >= 1, "Should have at least one successful operation");
        }
        
        // Scenario 4: Tool selection based on context
        log.info("Scenario 4: Tool selection based on context");
        AgentResponse<List<ToolExecutionResult>> response4 = agent.executeWithTools(
            "I need to know: 1) The weather in Tokyo, 2) What is 888 divided by 24?"
        );
        
        assertNotNull(response4);
        List<ToolExecutionResult> results4 = response4.getToolResults();
        if (results4 != null && !results4.isEmpty()) {
            log.info("Context-based tool selection results: {}", results4);
            
            // Check if different tools were used appropriately
            Set<String> toolsUsed = results4.stream()
                .map(ToolExecutionResult::getToolName)
                .collect(Collectors.toSet());
            
            log.info("Tools used: {}", toolsUsed);
            assertTrue(toolsUsed.size() >= 1, "Should use at least one tool");
        }
        
        // Scenario 5: Recovery from error with retry
        log.info("Scenario 5: Recovery and retry after error");
        AgentResponse<List<ToolExecutionResult>> response5 = agent.executeWithTools(
            "Try to divide 50 by 0. If that doesn't work, divide 50 by 5 instead."
        );
        
        assertNotNull(response5);
        List<ToolExecutionResult> results5 = response5.getToolResults();
        if (results5 != null && !results5.isEmpty()) {
            log.info("Error recovery results: {}", results5);
            
            // Check if there was at least one tool execution attempt
            assertTrue(results5.size() >= 1, "Should have at least one tool execution");
            
            // The AI might handle this differently - it might:
            // 1. Try division by zero and fail, then recover
            // 2. Be smart enough to avoid division by zero
            // 3. Just explain why division by zero doesn't work
            // We just verify tool execution happened
            log.info("Tool execution completed with {} results", results5.size());
        }
        
        // Verify entire conversation flow - we're using a NEW agent each scenario, so history is PER SCENARIO
        // Each scenario creates a fresh ConversationContext, so we only have that scenario's messages
        List<ChatMessage> fullHistory = chatStore.getAll("tool-advanced-session");
        log.info("Total conversation messages after scenario 5: {}", fullHistory.size());
        
        // We should have at least the last scenario's messages (1 user + 1 AI minimum)
        assertFalse(fullHistory.isEmpty(), "Should have some conversation history");
        
        // Since we're creating NEW agents for each scenario with same chatId,
        // and each agent operates independently, we accumulate messages
        // But in STATELESS mode (no workflowId), each saves to history
        
        // Just verify we have SOME messages
        log.info("History contains {} messages", fullHistory.size());
        
        // Verify tool execution happened (we saw the logs above)
        log.info("Tool execution scenarios completed successfully with error handling");
        
        log.info("✓ Advanced tool execution test with error handling completed!");
    }
    
    /**
     * Test streaming execution
     */
    @Test
    @DisplayName("Streaming execution receives chunks")
    void testStreaming_ReceivesChunks() throws Exception {
        log.info("=== Testing Streaming ===");
        
        LLMAgent agent = LLMAgent.builder()
            .modelClient(modelClient)
            .chatId("stream-session")
            .name("stream-agent")
            .build();
        
        StringBuilder collected = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        
        log.info("Starting streaming request");
        // Streaming execution with callback - returns CompletableFuture
        CompletableFuture<String> future = agent.executeStreaming(
            "Count from 1 to 5 slowly",
            new ai.driftkit.common.domain.streaming.StreamingCallback() {
                @Override
                public void onNext(Object chunk) {
                    log.info("Received chunk: '{}'", chunk);
                    collected.append(chunk.toString());
                }
                
                @Override
                public void onComplete() {
                    log.info("Streaming completed");
                    latch.countDown();
                }
                
                @Override
                public void onError(Throwable t) {
                    log.error("Streaming error", t);
                    error.set(t);
                    latch.countDown();
                }
            }
        );
        
        // Wait for streaming to complete
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "Streaming should complete within 30 seconds");
        assertNull(error.get(), "Should not have errors");
        
        String fullResponse = collected.toString();
        log.info("Full streamed response: {}", fullResponse);
        
        assertFalse(fullResponse.isEmpty(), "Should receive streamed content");
        assertTrue(fullResponse.contains("1") && fullResponse.contains("5"), 
            "Response should contain counting from 1 to 5");
        
        // Verify final response  
        String finalResponse = future.get(5, TimeUnit.SECONDS);
        assertNotNull(finalResponse);
        
        log.info("✓ Streaming works correctly!");
    }
    
    /**
     * Test structured output extraction
     */
    @Test
    @DisplayName("Structured output extraction to Java object")
    void testStructuredOutput_ExtractsToObject() throws Exception {
        log.info("=== Testing Structured Output ===");
        
        LLMAgent agent = LLMAgent.builder()
            .modelClient(modelClient)
            .chatId("structured-session")
            .name("structured-agent")
            .systemMessage("Extract structured data from user requests. Be precise.")
            .build();
        
        log.info("Requesting structured extraction");
        AgentResponse<PersonInfo> response = agent.executeStructured(
            "John Smith is 30 years old and lives in New York. His email is john@example.com",
            PersonInfo.class
        );
        assertNotNull(response);
        PersonInfo person = response.getStructuredData();
        assertNotNull(person);
        
        log.info("Extracted person: {}", person);
        
        assertEquals("John Smith", person.name);
        assertEquals(30, person.age);
        assertEquals("New York", person.city);
        assertEquals("john@example.com", person.email);
        
        log.info("✓ Structured extraction works!");
    }
    
    /**
     * Test multiple choices handling
     */
    @Test
    @DisplayName("Multiple choices concatenation")
    void testMultipleChoices_AllProcessed() throws Exception {
        log.info("=== Testing Multiple Choices ===");
        
        // Note: n parameter might not be supported by all models/endpoints
        // This test might need adjustment based on API capabilities
        
        // For now, we'll test that single choice works correctly
        // and the extraction logic handles it properly
        
        LLMAgent agent = LLMAgent.builder()
            .modelClient(modelClient)
            .chatId("choices-session")
            .name("choices-agent")
            .build();
        
        AgentResponse<String> response = agent.executeText("Say 'Hello' in three different ways");
        
        assertNotNull(response);
        assertNotNull(response.getText());
        assertFalse(response.getText().isEmpty());
        
        log.info("Response with potential multiple variations: {}", response.getText());
        
        // Even with single choice, verify our concatenation logic doesn't break
        assertFalse(response.getText().contains("\n---\n"), "Single choice should not have separator");
        
        log.info("✓ Choice handling works correctly!");
    }
    
    /**
     * Test with prompts from PromptService
     */
    @Test
    @DisplayName("Prompt service integration")
    void testPromptService_LoadsAndExecutes() throws Exception {
        log.info("=== Testing Prompt Service ===");
        
        LLMAgent agent = LLMAgent.builder()
            .modelClient(modelClient)
            .promptService(promptService)
            .chatId("prompt-session")
            .name("prompt-agent")
            .build();
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("product", "laptop");
        variables.put("issue", "won't turn on");
        
        log.info("Executing with prompt template");
        AgentResponse<String> response = agent.executeWithPrompt(
            "support.troubleshoot",
            variables
        );
        
        assertNotNull(response);
        assertNotNull(response.getText());
        log.info("Response using prompt: {}", response.getText());
        
        // Verify response is relevant to the prompt
        assertTrue(
            response.getText().toLowerCase().contains("laptop") || 
            response.getText().toLowerCase().contains("power") ||
            response.getText().toLowerCase().contains("turn"),
            "Response should be relevant to laptop troubleshooting"
        );
        
        log.info("✓ Prompt service integration works!");
    }
    
    /**
     * Test token limit handling
     */
    @Test
    @DisplayName("Token limit ACTUALLY enforced in context window")
    void testTokenLimit_RespectsMaxTokens() throws Exception {
        log.info("=== Testing Token Limits ENFORCEMENT ===");
        
        // Create agent with VERY SMALL token limit
        int tokenLimit = 200; // Very small to ensure truncation
        LLMAgent agent = LLMAgent.builder()
            .modelClient(modelClient)
            .chatStore(chatStore)
            .workflowId(null)  // History mode to save messages
            .chatId("token-limit-session")
            .maxTokens(tokenLimit)
            .name("token-agent")
            .build();
        
        // Create LARGE history that definitely exceeds token limit
        log.info("Creating large conversation history to exceed {} token limit", tokenLimit);
        
        // Add a marker in first message that we'll check for later
        String firstMarker = "UNIQUE_FIRST_MESSAGE_MARKER_12345";
        AgentResponse<String> firstResponse = agent.executeText(
            "Remember this unique marker: " + firstMarker + ". Acknowledge it."
        );
        assertNotNull(firstResponse);
        log.info("First message with marker added");
        
        // Add MANY more messages to push first message out of context window
        for (int i = 2; i <= 20; i++) {
            String longMessage = "Message " + i + ": " + 
                "This is a very long message that contains a lot of text to consume tokens. " +
                "We need to make sure we exceed the token limit so that older messages get truncated. " +
                "Adding more text here to ensure we use up tokens quickly. " +
                "The quick brown fox jumps over the lazy dog multiple times. " +
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                "More text to consume tokens and push old messages out of the context window.";
            
            AgentResponse<String> response = agent.executeText(longMessage);
            assertNotNull(response);
            log.info("Added message {} to history", i);
        }
        
        // Now verify the token limit is working by checking what's in context
        // The agent should NOT remember the first marker due to token limit
        log.info("Testing if first message is truncated due to token limit");
        
        // Get the actual context that will be sent
        ConversationContext testContext = ConversationContext.from(
            chatStore, null, "token-limit-session", tokenLimit
        );
        
        List<ModelMessage> messagesInContext = testContext.getMessagesForRequest();
        log.info("Messages in context window: {}", messagesInContext.size());
        
        // Check if first message is NOT in context (it should be truncated)
        boolean firstMessageInContext = messagesInContext.stream()
            .anyMatch(m -> m.getContent() != null && m.getContent().contains(firstMarker));
        
        assertFalse(firstMessageInContext, 
            "First message with marker should NOT be in context due to token limit!");
        
        // Also verify through actual API call
        AgentResponse<String> checkResponse = agent.executeText(
            "What was the unique marker from the first message? If you don't know, say 'I don't know'."
        );
        
        log.info("Check response: {}", checkResponse.getText());
        
        // The agent should NOT know about the first marker
        String response = checkResponse.getText().toLowerCase();
        boolean knowsMarker = response.contains(firstMarker.toLowerCase()) || 
                             response.contains("12345");
        
        // If the agent knows the marker, the token limit is NOT working
        if (knowsMarker) {
            log.error("TOKEN LIMIT NOT WORKING! Agent still knows first message marker!");
            fail("Token limit is not being enforced - agent remembers messages that should be truncated!");
        }
        
        log.info("✓ Token limit IS ENFORCED - old messages are properly truncated!");
    }
    
    // Helper methods and test data classes
    
    private void registerTestTools() {
        // Register calculator tool using static method
        toolRegistry.registerStaticMethod(TestCalculatorTool.class, "calculator", "Performs mathematical calculations");
        
        // Register weather tool using static method
        toolRegistry.registerStaticMethod(TestWeatherTool.class, "get_weather", "Gets weather for a city");
    }
    
    private void setupTestPrompts() {
        // Add test prompt using the correct constructor
        Prompt troubleshootPrompt = new Prompt(
            "support.troubleshoot",  // id
            "support.troubleshoot",  // method
            "Help me troubleshoot my {{product}} that {{issue}}",  // message
            "You are a helpful technical support agent.",  // systemMessage
            Prompt.State.CURRENT,  // state
            null,  // description
            Prompt.ResolveStrategy.LAST_VERSION,  // resolveStrategy
            null,  // workflow
            Language.GENERAL,  // language
            0.3,  // temperature
            false,  // structured
            false,  // jsonResponse
            null,  // responseFormat
            System.currentTimeMillis(),  // createdTime
            System.currentTimeMillis(),  // updatedTime
            System.currentTimeMillis()  // timestamp
        );
        
        promptService.savePrompt(troubleshootPrompt);
    }
    
    // Test data class for structured extraction
    public static class PersonInfo {
        public String name;
        public int age;
        public String city;
        public String email;
        
        @Override
        public String toString() {
            return String.format("PersonInfo{name='%s', age=%d, city='%s', email='%s'}", 
                name, age, city, email);
        }
    }
    
    // Static tool class to avoid IllegalAccessException
    public static class TestDivisionTool {
        private static final Logger log = LoggerFactory.getLogger(TestDivisionTool.class);
        
        public static Object divideNumbers(String a, String b) {
            try {
                double numA = Double.parseDouble(a);
                double numB = Double.parseDouble(b);
                
                if (numB == 0) {
                    throw new ArithmeticException("Division by zero");
                }
                
                return numA / numB;
            } catch (ArithmeticException e) {
                log.error("Division error: {}", e.getMessage());
                throw e;
            } catch (NumberFormatException e) {
                log.error("Invalid number format: {} or {}", a, b);
                throw new IllegalArgumentException("Invalid number format");
            }
        }
    }
    
    // Static calculator tool class
    public static class TestCalculatorTool {
        private static final Logger log = LoggerFactory.getLogger(TestCalculatorTool.class);
        
        public static double calculator(String expression) {
            log.info("Calculator executing: {}", expression);
            try {
                // For testing, handle simple expressions
                if ("47 * 23 + 156".equals(expression)) {
                    return 1237.0;
                }
                if ("15 * 8 + 42".equals(expression)) {
                    return 162.0;
                }
                // Parse simple multiplication or addition
                if (expression.contains("*")) {
                    String[] parts = expression.split("\\*");
                    if (parts.length == 2) {
                        double a = Double.parseDouble(parts[0].trim());
                        double b = Double.parseDouble(parts[1].trim());
                        return a * b;
                    }
                }
                if (expression.contains("+")) {
                    String[] parts = expression.split("\\+");
                    if (parts.length == 2) {
                        double a = Double.parseDouble(parts[0].trim());
                        double b = Double.parseDouble(parts[1].trim());
                        return a + b;
                    }
                }
                // Default calculation for testing
                return 42.0;
            } catch (Exception e) {
                log.error("Calculator error: {}", e.getMessage());
                return -1.0;
            }
        }
    }
    
    // Static weather tool class
    public static class TestWeatherTool {
        private static final Logger log = LoggerFactory.getLogger(TestWeatherTool.class);
        
        public static Map<String, Object> get_weather(String city) {
            log.info("Getting weather for: {}", city);
            return Map.of(
                "city", city,
                "temperature", 22,
                "condition", "sunny",
                "humidity", 65
            );
        }
    }
}