package ai.driftkit.workflows.core;

import ai.driftkit.clients.openai.client.OpenAIModelClient;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage.ModelContentElement;
import ai.driftkit.common.domain.client.ModelTextRequest.MessageType;
import ai.driftkit.common.domain.client.ModelTextRequest.ToolMode;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.common.tools.Tool;
import ai.driftkit.common.tools.ToolInfo;
import ai.driftkit.common.tools.ToolRegistry;
import ai.driftkit.config.EtlConfig.VaultConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Function Calling functionality.
 * Tests tool registration, tool calling, and execution with various scenarios.
 * 
 * To run these tests:
 * 1. Set OPENAI_API_KEY environment variable
 * 2. Enable tests by removing @Disabled annotation
 * 3. Run: mvn test -Dtest=FunctionCallingIntegrationTest
 */
@Slf4j
@Disabled("Integration test - requires OpenAI API key and is disabled by default")
public class FunctionCallingIntegrationTest {

    private ModelClient modelClient;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        // Initialize OpenAI client with configuration
        VaultConfig config = TestHelper.createTestConfig(null);

        modelClient = new OpenAIModelClient().init(config);
        toolRegistry = new ToolRegistry();
        
        // Register test tools
        setupTestTools();
        
        log.info("Test setup completed. Model: {}, Temperature: {}", 
                config.getModel(), config.getTemperature());
    }

    /**
     * Setup test tools for the registry
     */
    private void setupTestTools() {
        // Register static utility functions
        toolRegistry.registerStaticClass(WeatherService.class);
        toolRegistry.registerStaticClass(MathService.class);
        
        // Register instance methods
        CustomerService customerService = new CustomerService();
        toolRegistry.registerClass(customerService);
        
        log.info("Registered {} tools", toolRegistry.size());
    }

    /**
     * Test basic function calling with a simple math operation
     */
    @Test
    void testBasicFunctionCalling() {
        log.info("=== Testing Basic Function Calling ===");

        ModelTextRequest request = ModelTextRequest.builder()
            .messages(Arrays.asList(
                ModelContentMessage.builder()
                    .role(Role.user)
                    .content(Arrays.asList(
                        ModelContentElement.builder()
                            .type(MessageType.text)
                            .text("What is 15 multiplied by 7? Please use the math function to calculate this.")
                            .build()
                    ))
                    .build()
            ))
            .toolMode(ToolMode.auto)
            .tools(Arrays.asList(toolRegistry.getTools()))
            .build();

        ModelTextResponse response = modelClient.textToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getChoices());
        assertFalse(response.getChoices().isEmpty());
        
        // Check if tools were called
        ModelImageResponse.ModelMessage message = response.getChoices().get(0).getMessage();
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            log.info("Tool calls found: {}", message.getToolCalls().size());
            
            for (ToolCall toolCall : message.getToolCalls()) {
                log.info("Tool call: {} with args: {}", 
                    toolCall.getFunction().getName(), 
                    toolCall.getFunction().getArguments());
                
                // Execute the tool call
                Object result = toolRegistry.executeToolCall(toolCall);
                log.info("Tool execution result: {}", result);
                
                // Verify the math result
                if ("multiply".equals(toolCall.getFunction().getName())) {
                    assertEquals(105, result);
                }
            }
        } else {
            log.info("No tool calls made. Response: {}", message.getContent());
        }
    }

    /**
     * Test function calling with complex parameters
     */
    @Test
    void testComplexFunctionCalling() {
        log.info("=== Testing Complex Function Calling ===");

        ModelTextRequest request = ModelTextRequest.builder()
            .messages(Arrays.asList(
                ModelContentMessage.builder()
                    .role(Role.user)
                    .content(Arrays.asList(
                        ModelContentElement.builder()
                            .type(MessageType.text)
                            .text("Get weather information for New York City. I need temperature in Celsius.")
                            .build()
                    ))
                    .build()
            ))
            .toolMode(ToolMode.auto)
            .tools(Arrays.asList(toolRegistry.getTools()))
            .build();

        ModelTextResponse response = modelClient.textToText(request);
        
        assertNotNull(response);
        ModelImageResponse.ModelMessage message = response.getChoices().get(0).getMessage();
        
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            for (ToolCall toolCall : message.getToolCalls()) {
                log.info("Complex tool call: {} with args: {}", 
                    toolCall.getFunction().getName(), 
                    toolCall.getFunction().getArguments());
                
                if ("getWeather".equals(toolCall.getFunction().getName())) {
                    Map<String, JsonNode> args = toolCall.getFunction().getArguments();
                    assertTrue(args.containsKey("city"));
                    assertEquals("New York City", args.get("city").asText());
                    
                    Object result = toolRegistry.executeToolCall(toolCall);
                    log.info("Weather result: {}", result);
                    assertNotNull(result);
                    assertTrue(result.toString().contains("New York City"));
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

        ModelTextRequest request = ModelTextRequest.builder()
            .messages(Arrays.asList(
                ModelContentMessage.builder()
                    .role(Role.user)
                    .content(Arrays.asList(
                        ModelContentElement.builder()
                            .type(MessageType.text)
                            .text("I need to: 1) Add 10 and 5, 2) Multiply the result by 3, and 3) Check weather in Paris. Please use the appropriate functions.")
                            .build()
                    ))
                    .build()
            ))
            .toolMode(ToolMode.auto)
            .tools(Arrays.asList(toolRegistry.getTools()))
            .build();

        ModelTextResponse response = modelClient.textToText(request);
        
        assertNotNull(response);
        ModelImageResponse.ModelMessage message = response.getChoices().get(0).getMessage();
        
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            log.info("Multiple tool calls found: {}", message.getToolCalls().size());
            
            int mathCalls = 0;
            int weatherCalls = 0;
            
            for (ToolCall toolCall : message.getToolCalls()) {
                String functionName = toolCall.getFunction().getName();
                log.info("Function: {} with args: {}", functionName, toolCall.getFunction().getArguments());
                
                Object result = toolRegistry.executeToolCall(toolCall);
                log.info("Result: {}", result);
                
                if ("add".equals(functionName) || "multiply".equals(functionName)) {
                    mathCalls++;
                } else if ("getWeather".equals(functionName)) {
                    weatherCalls++;
                }
            }
            
            log.info("Math calls: {}, Weather calls: {}", mathCalls, weatherCalls);
        }
    }

    /**
     * Test customer service functions with objects
     */
    @Test
    void testCustomerServiceFunctions() {
        log.info("=== Testing Customer Service Functions ===");

        ModelTextRequest request = ModelTextRequest.builder()
            .messages(Arrays.asList(
                ModelContentMessage.builder()
                    .role(Role.user)
                    .content(Arrays.asList(
                        ModelContentElement.builder()
                            .type(MessageType.text)
                            .text("Create a new customer with name 'John Smith', email 'john@example.com', and phone '555-1234'.")
                            .build()
                    ))
                    .build()
            ))
            .toolMode(ToolMode.auto)
            .tools(Arrays.asList(toolRegistry.getTools()))
            .build();

        ModelTextResponse response = modelClient.textToText(request);
        
        assertNotNull(response);
        ModelImageResponse.ModelMessage message = response.getChoices().get(0).getMessage();

        boolean toolCalled = CollectionUtils.isNotEmpty(message.getToolCalls());

        Assertions.assertTrue(toolCalled);

        if (toolCalled) {
            for (ToolCall toolCall : message.getToolCalls()) {
                if ("createCustomer".equals(toolCall.getFunction().getName())) {
                    log.info("Customer creation call with args: {}", toolCall.getFunction().getArguments());
                    
                    Object result = toolRegistry.executeToolCall(toolCall);
                    log.info("Customer creation result: {}", result);
                    assertNotNull(result);
                    assertTrue(result.toString().contains("John Smith"));
                }
            }
        }
    }

    /**
     * Test error handling with invalid function calls
     */
    @Test
    void testErrorHandling() {
        log.info("=== Testing Error Handling ===");

        // Test with invalid function name
        ToolCall invalidToolCall = ToolCall.builder()
            .id("test-123")
            .type("function")
            .function(ToolCall.FunctionCall.builder()
                .name("nonExistentFunction")
                .arguments(Map.of("param", BooleanNode.getFalse()))
                .build())
            .build();

        Exception exception = assertThrows(RuntimeException.class, () -> {
            toolRegistry.executeToolCall(invalidToolCall);
        });
        
        assertTrue(exception.getMessage().contains("Function not found"));
        log.info("Error handling test passed: {}", exception.getMessage());
    }

    /**
     * Test tool registry introspection
     */
    @Test
    void testToolRegistryIntrospection() {
        log.info("=== Testing Tool Registry Introspection ===");

        assertTrue(toolRegistry.hasFunction("add"));
        assertTrue(toolRegistry.hasFunction("multiply"));
        assertTrue(toolRegistry.hasFunction("getWeather"));
        assertTrue(toolRegistry.hasFunction("createCustomer"));
        
        ToolInfo addTool = toolRegistry.getToolInfo("add");
        assertNotNull(addTool);
        assertEquals("add", addTool.getFunctionName());
        assertEquals(2, addTool.getParameterNames().size());
        assertTrue(addTool.getParameterNames().contains("a"));
        assertTrue(addTool.getParameterNames().contains("b"));
        
        ModelClient.Tool[] tools = toolRegistry.getTools();
        assertTrue(tools.length >= 4);
        
        for (ModelClient.Tool tool : tools) {
            assertNotNull(tool.getFunction().getName());
            assertNotNull(tool.getFunction().getDescription());
            log.info("Tool: {} - {}", tool.getFunction().getName(), tool.getFunction().getDescription());
        }
    }

    // ============= Test Service Classes =============

    /**
     * Static weather service for testing
     */
    public static class WeatherService {
        
        @Tool(description = "Get current weather information for a city")
        public static String getWeather(@NotNull String city, String unit) {
            String temp = "celsius".equalsIgnoreCase(unit) ? "22°C" : "72°F";
            return String.format("Weather in %s: Sunny, %s, light breeze", city, temp);
        }
        
        @Tool(description = "Get weather forecast for multiple days")
        public static String getWeatherForecast(String city, int days) {
            return String.format("%d-day forecast for %s: Mostly sunny with temperatures ranging 20-25°C", 
                days, city);
        }
    }

    /**
     * Static math service for testing
     */
    public static class MathService {
        
        @Tool(description = "Add two numbers")
        public static int add(int a, int b) {
            return a + b;
        }
        
        @Tool(description = "Multiply two numbers")
        public static int multiply(int a, int b) {
            return a * b;
        }
        
        @Tool(description = "Calculate the power of a number")
        public static double power(double base, double exponent) {
            return Math.pow(base, exponent);
        }
    }

    /**
     * Customer service with instance methods
     */
    public static class CustomerService {
        
        @Tool(description = "Create a new customer record")
        public String createCustomer(CustomerInfo customerInfo) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return String.format("Customer created: %s (ID: CUST-%s) at %s", 
                customerInfo.getName(), 
                customerInfo.getName().hashCode(), 
                timestamp);
        }
        
        @Tool(description = "Search for customers by name")
        public String searchCustomer(String name) {
            return String.format("Found 1 customer matching '%s': John Smith (CUST-12345)", name);
        }
        
        @Tool(description = "Update customer information")
        public String updateCustomer(String customerId, CustomerInfo updates) {
            return String.format("Customer %s updated with new info: %s", customerId, updates.getName());
        }
    }

    // ============= Test Data Classes =============

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
        
        @JsonProperty("age")
        @JsonPropertyDescription("Customer's age")
        private Integer age;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeatherRequest {
        
        @JsonProperty(value = "city", required = true)
        @JsonPropertyDescription("City name for weather information")
        @NotNull
        private String city;
        
        @JsonProperty("unit")
        @JsonPropertyDescription("Temperature unit: celsius or fahrenheit")
        private String unit = "celsius";
        
        @JsonProperty("includeDetails")
        @JsonPropertyDescription("Whether to include detailed weather information")
        private boolean includeDetails = false;
    }
}