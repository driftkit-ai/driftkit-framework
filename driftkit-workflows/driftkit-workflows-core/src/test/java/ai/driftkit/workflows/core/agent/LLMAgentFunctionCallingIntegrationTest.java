package ai.driftkit.workflows.core.agent;

import ai.driftkit.clients.openai.client.OpenAIModelClient;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.tools.Tool;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.config.EtlConfig.VaultConfig;
import ai.driftkit.workflows.core.TestHelper;
import ai.driftkit.workflows.core.agent.LLMAgent;
import ai.driftkit.workflows.core.agent.AgentResponse;
import ai.driftkit.workflows.core.agent.ToolExecutionResult;
import ai.driftkit.workflows.core.chat.ChatMemory;
import ai.driftkit.workflows.core.chat.Message;
import ai.driftkit.workflows.core.chat.SimpleTokenizer;
import ai.driftkit.workflows.core.chat.TokenWindowChatMemory;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for LLMAgent Function Calling functionality.
 * Tests tool registration, tool calling, and execution with various scenarios.
 * 
 * To run these tests:
 * 1. Set OPENAI_API_KEY environment variable
 * 2. Enable tests by removing @Disabled annotation
 * 3. Run: mvn test -Dtest=LLMAgentFunctionCallingIntegrationTest
 */
@Slf4j
@Disabled("Integration test - requires OpenAI API key and is disabled by default")
public class LLMAgentFunctionCallingIntegrationTest {

    private LLMAgent agent;
    private TestToolService testService;

    @BeforeEach
    void setUp() {
        // Initialize OpenAI client with configuration
        VaultConfig config = TestHelper.createTestConfig(null);
        ModelClient modelClient = new OpenAIModelClient().init(config);
        
        // Create chat memory
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(4000, new SimpleTokenizer());
        
        // Create agent
        agent = LLMAgent.builder()
            .modelClient(modelClient)
            .systemMessage("You are a helpful assistant that can use tools to help users.")
            .temperature(0.1)
            .maxTokens(1000)
            .chatMemory(chatMemory)
            .build();
        
        // Setup test tools
        testService = new TestToolService();
        setupTestTools();
        
        log.info("Test setup completed. Model: {}, Temperature: {}", 
                config.getModel(), config.getTemperature());
    }

    /**
     * Setup test tools for the agent
     */
    private void setupTestTools() {
        // Register test service methods as tools
        agent.registerTool("add", testService, "Add two numbers")
             .registerTool("multiply", testService, "Multiply two numbers")
             .registerTool("getWeather", testService, "Get weather for a city")
             .registerTool("createCustomer", testService, "Create a new customer")
             .registerTool("getCurrentTime", testService, "Get current time");
        
        log.info("Registered test tools for LLMAgent");
    }

    /**
     * Test basic function calling with simple math operation
     */
    @Test
    void testBasicFunctionCalling() {
        log.info("=== Testing Basic Function Calling ===");

        AgentResponse<List<ToolExecutionResult>> response = agent.executeWithTools(
            "What is 15 multiplied by 7? Please use the math function to calculate this."
        );
        
        assertNotNull(response);
        assertTrue(response.hasToolResults());
        
        List<ToolExecutionResult> results = response.getToolResults();
        assertNotNull(results);
        assertFalse(results.isEmpty());
        
        log.info("Tool results found: {}", results.size());
        
        for (ToolExecutionResult result : results) {
            log.info("Tool execution: {} -> {}", result.getToolName(), result.getResult());
            assertTrue(result.isSuccess());
            
            // Verify the math result
            if ("multiply".equals(result.getToolName())) {
                Integer mathResult = result.getTypedResult();
                assertEquals(105, mathResult);
            }
        }
    }

    /**
     * Test getting tool calls without automatic execution
     */
    @Test
    void testGetToolCallsWithoutExecution() {
        log.info("=== Testing Get Tool Calls Without Execution ===");

        AgentResponse<List<ToolCall>> response = agent.executeForToolCalls(
            "Add 25 and 17 using the addition function."
        );
        
        assertNotNull(response);
        assertTrue(response.hasToolCalls());
        
        List<ToolCall> toolCalls = response.getToolCalls();
        assertNotNull(toolCalls);
        assertFalse(toolCalls.isEmpty());
        
        log.info("Tool calls found: {}", toolCalls.size());
        
        for (ToolCall toolCall : toolCalls) {
            log.info("Tool call: {} with args: {}", 
                toolCall.getFunction().getName(), 
                toolCall.getFunction().getArguments());
            
            // Manually execute the tool call
            ToolExecutionResult result = agent.executeToolCall(toolCall);
            assertNotNull(result);
            assertTrue(result.isSuccess());
            
            if ("add".equals(toolCall.getFunction().getName())) {
                Integer sum = result.getTypedResult();
                assertEquals(42, sum);
            }
            
            log.info("Manual execution result: {}", result.getResult());
        }
    }

    /**
     * Test function calling with complex parameters
     */
    @Test
    void testComplexFunctionCalling() {
        log.info("=== Testing Complex Function Calling ===");

        AgentResponse<List<ToolExecutionResult>> response = agent.executeWithTools(
            "Get weather information for Paris, France with temperature in Celsius."
        );
        
        assertNotNull(response);
        
        if (response.hasToolResults()) {
            List<ToolExecutionResult> results = response.getToolResults();
            
            for (ToolExecutionResult result : results) {
                log.info("Complex tool execution: {} -> {}", result.getToolName(), result.getResult());
                
                if ("getWeather".equals(result.getToolName())) {
                    WeatherInfo weather = result.getTypedResult();
                    assertNotNull(weather);
                    assertNotNull(weather.getCity());
                    assertNotNull(weather.getTemperature());
                    assertTrue(weather.getCity().toLowerCase().contains("paris"));
                    
                    log.info("Weather result: {}", weather);
                }
            }
        }
    }

    /**
     * Test multiple function calls in sequence
     */
    @Test
    void testMultipleFunctionCalls() {
        log.info("=== Testing Multiple Function Calls ===");

        AgentResponse<List<ToolExecutionResult>> response = agent.executeWithTools(
            "I need to: 1) Add 10 and 5, 2) Multiply the result by 3, and 3) Get the current time. Please use the appropriate functions."
        );
        
        assertNotNull(response);
        
        if (response.hasToolResults()) {
            List<ToolExecutionResult> results = response.getToolResults();
            log.info("Multiple tool results found: {}", results.size());
            
            int mathCalls = 0;
            int timeCalls = 0;
            
            for (ToolExecutionResult result : results) {
                String toolName = result.getToolName();
                log.info("Function: {} -> {}", toolName, result.getResult());
                
                assertTrue(result.isSuccess());
                
                if ("add".equals(toolName) || "multiply".equals(toolName)) {
                    mathCalls++;
                    Integer mathResult = result.getTypedResult();
                    assertNotNull(mathResult);
                    assertTrue(mathResult > 0);
                } else if ("getCurrentTime".equals(toolName)) {
                    timeCalls++;
                    String timeResult = result.getTypedResult();
                    assertNotNull(timeResult);
                    assertFalse(timeResult.isEmpty());
                }
            }
            
            log.info("Math calls: {}, Time calls: {}", mathCalls, timeCalls);
            assertTrue(mathCalls > 0 || timeCalls > 0);
        }
    }

    /**
     * Test customer service functions with objects
     */
    @Test
    void testCustomerServiceFunctions() {
        log.info("=== Testing Customer Service Functions ===");

        AgentResponse<List<ToolExecutionResult>> response = agent.executeWithTools(
            "Create a new customer with name 'Alice Smith', email 'alice@example.com', and phone '555-9876'."
        );
        
        assertNotNull(response);
        
        if (response.hasToolResults()) {
            List<ToolExecutionResult> results = response.getToolResults();
            
            for (ToolExecutionResult result : results) {
                if ("createCustomer".equals(result.getToolName())) {
                    log.info("Customer creation result: {}", result.getResult());
                    assertTrue(result.isSuccess());
                    
                    String customerResult = result.getTypedResult();
                    assertNotNull(customerResult);
                    assertTrue(customerResult.contains("Alice Smith"));
                }
            }
        }
    }

    /**
     * Test conversation memory with multiple interactions
     */
    @Test
    void testConversationMemory() {
        log.info("=== Testing Conversation Memory ===");

        // First interaction
        AgentResponse<String> response1 = agent.executeText("Hello! My name is John.");
        assertNotNull(response1.getText());
        log.info("First response: {}", response1.getText());

        // Second interaction - should remember the name
        AgentResponse<String> response2 = agent.executeText("What's my name?");
        assertNotNull(response2.getText());
        assertTrue(response2.getText().toLowerCase().contains("john"));
        log.info("Second response: {}", response2.getText());

        // Third interaction with tool usage
        AgentResponse<List<ToolExecutionResult>> response3 = agent.executeWithTools("Add 5 and 3 for me.");
        if (response3.hasToolResults()) {
            List<ToolExecutionResult> results = response3.getToolResults();
            assertFalse(results.isEmpty());
            log.info("Tool results: {}", results.size());
        }

        // Check conversation history
        List<Message> history = agent.getConversationHistory();
        assertTrue(history.size() >= 6); // At least 3 user messages and 3 assistant messages
        log.info("Conversation history length: {}", history.size());
    }

    /**
     * Test error handling with tool execution
     */
    @Test
    void testErrorHandling() {
        log.info("=== Testing Error Handling ===");

        // Test with a request that might not trigger tools
        AgentResponse<List<ToolExecutionResult>> response = agent.executeWithTools(
            "Just say hello, don't use any tools."
        );
        
        assertNotNull(response);
        
        // Should either have no tool results or have successful execution
        if (response.hasToolResults()) {
            List<ToolExecutionResult> results = response.getToolResults();
            for (ToolExecutionResult result : results) {
                // All executions should succeed if they happened
                assertTrue(result.isSuccess());
            }
        } else {
            log.info("No tools were called, which is expected for this request");
        }
    }

    /**
     * Test clearing conversation history
     */
    @Test
    void testClearHistory() {
        log.info("=== Testing Clear History ===");

        // Add some conversation
        agent.executeText("Remember that I like pizza.");
        agent.executeText("Also remember I'm from New York.");
        
        List<Message> historyBefore = agent.getConversationHistory();
        assertTrue(historyBefore.size() >= 4); // At least 2 user + 2 assistant messages
        
        // Clear history
        agent.clearHistory();
        
        List<Message> historyAfter = agent.getConversationHistory();
        assertTrue(historyAfter.size() < historyBefore.size());
        
        log.info("History before clear: {}, after clear: {}", historyBefore.size(), historyAfter.size());
    }

    // ============= Test Service Class =============

    /**
     * Test service with tool methods
     */
    public static class TestToolService {
        
        @Tool(name = "add", description = "Add two numbers")
        public int add(int a, int b) {
            return a + b;
        }
        
        @Tool(name = "multiply", description = "Multiply two numbers")
        public int multiply(int a, int b) {
            return a * b;
        }
        
        @Tool(name = "getWeather", description = "Get weather information for a city")
        public WeatherInfo getWeather(@NotNull String city, String unit) {
            String temp = "celsius".equalsIgnoreCase(unit) ? "22°C" : "72°F";
            return new WeatherInfo(city, temp, "Sunny with light clouds", 65);
        }
        
        @Tool(name = "createCustomer", description = "Create a new customer record")
        public String createCustomer(CustomerInfo customerInfo) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return String.format("Customer created: %s (Email: %s) at %s", 
                customerInfo.getName(), 
                customerInfo.getEmail(),
                timestamp);
        }
        
        @Tool(name = "getCurrentTime", description = "Get current time")
        public String getCurrentTime() {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    // ============= Test Data Classes =============

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeatherInfo {
        
        @JsonProperty(value = "city", required = true)
        @JsonPropertyDescription("City name")
        private String city;
        
        @JsonProperty(value = "temperature", required = true)
        @JsonPropertyDescription("Temperature information")
        private String temperature;
        
        @JsonProperty("description")
        @JsonPropertyDescription("Weather description")
        private String description;
        
        @JsonProperty("humidity")
        @JsonPropertyDescription("Humidity percentage")
        private Integer humidity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {
        
        @JsonProperty(value = "name", required = true)
        @JsonPropertyDescription("Customer's full name")
        @NotNull
        private String name;
        
        @JsonProperty(value = "email", required = true)
        @JsonPropertyDescription("Customer's email address")
        @NotNull
        private String email;
        
        @JsonProperty("phone")
        @JsonPropertyDescription("Customer's phone number")
        private String phone;
        
        @JsonProperty("address")
        @JsonPropertyDescription("Customer's address")
        private String address;
    }
}