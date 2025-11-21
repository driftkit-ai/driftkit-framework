package ai.driftkit.clients.gemini.client;

import ai.driftkit.clients.gemini.utils.GeminiUtils;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ModelContentMessage;
import ai.driftkit.config.EtlConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
public class GeminiModelClientTest {
    
    private GeminiModelClient client;
    private String apiKey;
    
    @BeforeEach
    void setUp() {
        apiKey = System.getenv("GEMINI_API_KEY");
        assertNotNull(apiKey, "GEMINI_API_KEY environment variable must be set");
        
        EtlConfig.VaultConfig config = new EtlConfig.VaultConfig();
        config.setName("gemini-test");
        config.setApiKey(apiKey);
        config.setModel(GeminiUtils.GEMINI_FLASH_2_5);
        config.setTemperature(0.7);
        config.setJsonObject(true);
        
        client = new GeminiModelClient();
        client.init(config);
    }
    
    @Test
    void testTextToText() {
        // Simple text generation
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, "What is 2+2? Reply with just the number.")
                ))
                .model(GeminiUtils.GEMINI_FLASH_2_5)
                .temperature(0.0)
                .build();
        
        ModelTextResponse response = client.textToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getResponse());
        assertTrue(response.getResponse().contains("4"));
        assertNotNull(response.getUsage());
        assertTrue(response.getUsage().getTotalTokens() > 0);
    }
    
    @Test
    void testTextToTextWithSystemMessage() {
        // Test with system message
        client.setSystemMessages(List.of("You are a helpful math tutor. Always explain your reasoning step by step."));
        
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, "What is 15% of 80?")
                ))
                .model(GeminiUtils.GEMINI_FLASH_2_5)
                .build();
        
        ModelTextResponse response = client.textToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getResponse());
        assertTrue(response.getResponse().contains("12"));
        // Should contain explanation due to system message
        assertTrue(response.getResponse().length() > 10);
    }
    
    @Test
    void testJsonObjectOutput() {
        // Test structured JSON output
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, 
                                "Extract the following information from this text and return as JSON: " +
                                "'John Smith is 28 years old and lives in New York.' " +
                                "Format: {\"name\": string, \"age\": number, \"city\": string}")
                ))
                .model(GeminiUtils.GEMINI_FLASH_2_5)
                .responseFormat(ResponseFormat.jsonObject())
                .build();
        
        ModelTextResponse response = client.textToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getResponse());
        
        // Verify it's valid JSON
        assertDoesNotThrow(() -> response.getResponseJson());
        
        var json = assertDoesNotThrow(() -> response.getResponseJson());
        assertEquals("John Smith", json.get("name").asText());
        assertEquals(28, json.get("age").asInt());
        assertEquals("New York", json.get("city").asText());
    }
    
    @Test
    void testFunctionCalling() {
        // Test function calling
        ModelClient.Tool weatherTool = ModelClient.Tool.builder()
                .type(ModelClient.ResponseFormatType.function)
                .function(ModelClient.ToolFunction.builder()
                        .name("get_weather")
                        .description("Get the current weather in a given location")
                        .parameters(new ModelClient.ToolFunction.FunctionParameters(
                                ModelClient.ResponseFormatType.Object,
                                Map.of(
                                        "location", new ModelClient.Property(ModelClient.ResponseFormatType.String),
                                        "unit", new ModelClient.Property(ModelClient.ResponseFormatType.String)
                                ),
                                List.of("location")
                        ))
                        .build())
                .build();
        
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, "What's the weather like in Paris?")
                ))
                .model(GeminiUtils.GEMINI_FLASH_2_5)
                .tools(List.of(weatherTool))
                .toolMode(ModelTextRequest.ToolMode.auto)
                .build();
        
        ModelTextResponse response = client.textToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getChoices());
        assertFalse(response.getChoices().isEmpty());
        
        var message = response.getChoices().get(0).getMessage();
        // Should either call the function or provide a response
        assertTrue(message.getToolCalls() != null || message.getContent() != null);
    }
    
    @Test
    void testMultiTurnConversation() {
        // Test multi-turn conversation
        ModelTextRequest request1 = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, "My name is Alice. Remember it.")
                ))
                .model(GeminiUtils.GEMINI_FLASH_2_5)
                .build();
        
        ModelTextResponse response1 = client.textToText(request1);
        assertNotNull(response1);
        
        // Second turn
        ModelTextRequest request2 = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, "My name is Alice. Remember it."),
                        ModelContentMessage.create(Role.assistant, response1.getResponse()),
                        ModelContentMessage.create(Role.user, "What's my name?")
                ))
                .model(GeminiUtils.GEMINI_FLASH_2_5)
                .build();
        
        ModelTextResponse response2 = client.textToText(request2);
        
        assertNotNull(response2);
        assertNotNull(response2.getResponse());
        assertTrue(response2.getResponse().toLowerCase().contains("alice"));
    }
    
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_EXPENSIVE_TESTS", matches = "true")
    void testReasoningWithGemini25Pro() {
        // Test reasoning with Gemini 2.5 Pro
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, 
                                "Solve this step by step: If a train travels 120 km in 1.5 hours, " +
                                "how long will it take to travel 200 km at the same speed?")
                ))
                .model(GeminiUtils.GEMINI_PRO_2_5)
                .reasoningEffort(ModelTextRequest.ReasoningEffort.high)
                .build();
        
        ModelTextResponse response = client.textToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getResponse());
        // Should contain detailed reasoning
        assertTrue(response.getResponse().length() > 50);
        assertTrue(response.getResponse().contains("2.5") || response.getResponse().contains("2 hours and 30 minutes"));
    }
    
    @Test
    void testDifferentModels() {
        // Test different Gemini models
        String[] models = {
                GeminiUtils.GEMINI_FLASH_2_5,
                GeminiUtils.GEMINI_FLASH_LITE_2_5
        };
        
        for (String model : models) {
            ModelTextRequest request = ModelTextRequest.builder()
                    .messages(List.of(
                            ModelContentMessage.create(Role.user, "Say 'Hello from " + model + "'")
                    ))
                    .model(model)
                    .build();
            
            ModelTextResponse response = client.textToText(request);
            
            assertNotNull(response, "Response should not be null for model: " + model);
            assertNotNull(response.getResponse(), "Response content should not be null for model: " + model);
            assertTrue(response.getResponse().toLowerCase().contains("hello"), 
                    "Response should contain 'hello' for model: " + model);
        }
    }
    
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_EXPENSIVE_TESTS", matches = "true")
    void testLogprobs() {
        // Note: Logprobs are not supported by all Gemini models
        // Test logprobs functionality
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, "Complete this: The capital of France is")
                ))
                .model(GeminiUtils.GEMINI_FLASH_2_5)
                .logprobs(true)
                .topLogprobs(3)
                .build();

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        assertNotNull(response.getResponse());
        assertTrue(response.getResponse().toLowerCase().contains("paris"));

        // Check if logprobs are included (if supported by the model)
        if (response.getChoices() != null && !response.getChoices().isEmpty()) {
            var logprobs = response.getChoices().get(0).getLogprobs();
            // Logprobs might not be supported by all models
            if (logprobs != null && logprobs.getContent() != null) {
                assertFalse(logprobs.getContent().isEmpty());
            }
        }
    }

    @Test
    void testStructuredOutputJsonSchema() {
        // Test structured output with JSON schema
        ResponseFormat.JsonSchema schema = ResponseFormat.JsonSchema.builder()
                .title("PersonInfo")
                .type("object")
                .properties(Map.of(
                        "name", ResponseFormat.SchemaProperty.builder()
                                .type("string")
                                .description("The person's full name")
                                .build(),
                        "age", ResponseFormat.SchemaProperty.builder()
                                .type("integer")
                                .description("The person's age in years")
                                .build(),
                        "city", ResponseFormat.SchemaProperty.builder()
                                .type("string")
                                .description("The person's city of residence")
                                .build()
                ))
                .required(List.of("name", "age", "city"))
                .additionalProperties(false)
                .build();

        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user,
                                "Extract information from: Sarah Connor is 35 years old and lives in Los Angeles")
                ))
                .model(GeminiUtils.GEMINI_FLASH_2_5)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.ResponseType.JSON_SCHEMA)
                        .jsonSchema(schema)
                        .build())
                .temperature(0.1)
                .build();

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        assertNotNull(response.getResponse());

        String content = response.getResponse();
        // Verify JSON structure
        assertTrue(content.contains("Sarah Connor") || content.contains("Sarah"));
        assertTrue(content.contains("35"));
        assertTrue(content.contains("Los Angeles"));
    }

    @Test
    void testStructuredOutputWithNestedObjects() {
        // Test structured output with nested objects
        ResponseFormat.JsonSchema schema = ResponseFormat.JsonSchema.builder()
                .title("Product")
                .type("object")
                .properties(Map.of(
                        "name", ResponseFormat.SchemaProperty.builder()
                                .type("string")
                                .description("Product name")
                                .build(),
                        "price", ResponseFormat.SchemaProperty.builder()
                                .type("number")
                                .description("Price in USD")
                                .build(),
                        "dimensions", ResponseFormat.SchemaProperty.builder()
                                .type("object")
                                .description("Product dimensions")
                                .properties(Map.of(
                                        "width", ResponseFormat.SchemaProperty.builder()
                                                .type("number")
                                                .build(),
                                        "height", ResponseFormat.SchemaProperty.builder()
                                                .type("number")
                                                .build(),
                                        "unit", ResponseFormat.SchemaProperty.builder()
                                                .type("string")
                                                .build()
                                ))
                                .build()
                ))
                .required(List.of("name", "price", "dimensions"))
                .additionalProperties(false)
                .build();

        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user,
                                "Create a product listing for: Laptop X1 priced at 999.99 USD with dimensions 35x24 centimeters")
                ))
                .model(GeminiUtils.GEMINI_FLASH_2_5)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.ResponseType.JSON_SCHEMA)
                        .jsonSchema(schema)
                        .build())
                .temperature(0.1)
                .build();

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        String content = response.getResponse();

        // Verify nested structure
        assertTrue(content.contains("Laptop") || content.contains("X1"));
        assertTrue(content.contains("999") || content.contains("1000"));
        assertTrue(content.contains("35") || content.contains("24"));
    }

    @Test
    void testStructuredOutputWithEnums() {
        // Test structured output with enum values
        ResponseFormat.JsonSchema schema = ResponseFormat.JsonSchema.builder()
                .title("WeatherReport")
                .type("object")
                .properties(Map.of(
                        "location", ResponseFormat.SchemaProperty.builder()
                                .type("string")
                                .description("City name")
                                .build(),
                        "condition", ResponseFormat.SchemaProperty.builder()
                                .type("string")
                                .description("Weather condition")
                                .enumValues(List.of("sunny", "cloudy", "rainy", "snowy", "windy"))
                                .build(),
                        "temperature", ResponseFormat.SchemaProperty.builder()
                                .type("number")
                                .description("Temperature in Celsius")
                                .build()
                ))
                .required(List.of("location", "condition", "temperature"))
                .build();

        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user,
                                "Generate a weather report for Tokyo with sunny weather and 25 degrees")
                ))
                .model(GeminiUtils.GEMINI_FLASH_2_5)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.ResponseType.JSON_SCHEMA)
                        .jsonSchema(schema)
                        .build())
                .temperature(0.1)
                .build();

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        String content = response.getResponse();

        // Condition should be one of the enum values
        assertTrue(content.contains("sunny") || content.contains("cloudy") ||
                   content.contains("rainy") || content.contains("snowy") || content.contains("windy"));
        assertTrue(content.contains("Tokyo"));
        assertTrue(content.contains("25"));
    }

    @Test
    void testStructuredOutputWithArray() {
        // Test structured output with arrays
        ResponseFormat.JsonSchema schema = ResponseFormat.JsonSchema.builder()
                .title("BookList")
                .type("object")
                .properties(Map.of(
                        "genre", ResponseFormat.SchemaProperty.builder()
                                .type("string")
                                .description("Book genre")
                                .build(),
                        "books", ResponseFormat.SchemaProperty.builder()
                                .type("array")
                                .description("List of book titles")
                                .items(ResponseFormat.SchemaProperty.builder()
                                        .type("string")
                                        .build())
                                .build()
                ))
                .required(List.of("genre", "books"))
                .build();

        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user,
                                "List 3 science fiction books: Dune, Ender's Game, and Foundation")
                ))
                .model(GeminiUtils.GEMINI_FLASH_2_5)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.ResponseType.JSON_SCHEMA)
                        .jsonSchema(schema)
                        .build())
                .temperature(0.1)
                .build();

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        String content = response.getResponse();

        // Should contain array with book titles
        assertTrue(content.contains("Dune"));
        assertTrue(content.contains("Ender") || content.contains("Foundation"));
    }
}