package ai.driftkit.clients.claude;

import ai.driftkit.clients.claude.client.ClaudeModelClient;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ModelClient.Capability;
import ai.driftkit.common.domain.client.ModelClient.ResponseFormatType;
import ai.driftkit.common.domain.client.ModelClient.UnsupportedCapabilityException;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.config.EtlConfig.VaultConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "RUN_EXPENSIVE_TESTS", matches = "true")
public class ClaudeModelClientTest {
    
    private ClaudeModelClient client;
    private static final String API_KEY = System.getenv("CLAUDE_API_KEY");
    
    @BeforeEach
    void setUp() {
        assertNotNull(API_KEY, "CLAUDE_API_KEY environment variable must be set");
        
        VaultConfig config = VaultConfig.builder()
                .apiKey(API_KEY)
                .model(ClaudeModelClient.CLAUDE_DEFAULT)
                .temperature(0.7)
                .maxTokens(1000)
                .build();
        
        client = new ClaudeModelClient();
        client.init(config);
    }
    
    @Test
    void testBasicTextCompletion() {
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, "What is the capital of France?")
                ))
                .temperature(0.3)
                .build();
        
        ModelTextResponse response = client.textToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getChoices());
        assertFalse(response.getChoices().isEmpty());
        
        String content = response.getChoices().get(0).getMessage().getContent();
        assertNotNull(content);
        assertTrue(content.toLowerCase().contains("paris"));
    }
    
    @Test
    void testConversation() {
        List<ModelContentMessage> messages = List.of(
                ModelContentMessage.create(Role.system, "You are a helpful math tutor."),
                ModelContentMessage.create(Role.user, "What is 2 + 2?"),
                ModelContentMessage.create(Role.assistant, "2 + 2 equals 4."),
                ModelContentMessage.create(Role.user, "What about 3 + 3?")
        );
        
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(messages)
                .temperature(0.3)
                .build();
        
        ModelTextResponse response = client.textToText(request);
        
        assertNotNull(response);
        String content = response.getChoices().get(0).getMessage().getContent();
        assertTrue(content.contains("6"));
    }
    
    @Test
    void testFunctionCalling() {
        ModelClient.Tool weatherTool = ModelClient.Tool.builder()
                .type(ModelClient.ResponseFormatType.function)
                .function(ModelClient.ToolFunction.builder()
                        .name("get_weather")
                        .description("Get the current weather in a given location")
                        .parameters(new ModelClient.ToolFunction.FunctionParameters(
                                ResponseFormatType.Object,
                                Map.of(
                                        "location", ModelClient.Property.builder()
                                                .type(ModelClient.ResponseFormatType.String)
                                                .description("The city and state, e.g. San Francisco, CA")
                                                .build(),
                                        "unit", ModelClient.Property.builder()
                                                .type(ModelClient.ResponseFormatType.String)
                                                .enumValues(List.of("celsius", "fahrenheit"))
                                                .build()
                                ),
                                List.of("location")
                        ))
                        .build())
                .build();
        
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, "What's the weather like in Tokyo?")
                ))
                .tools(List.of(weatherTool))
                .toolMode(ModelTextRequest.ToolMode.auto)
                .temperature(0.3)
                .build();
        
        ModelTextResponse response = client.textToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getChoices());
        assertFalse(response.getChoices().isEmpty());
        
        ModelImageResponse.ModelMessage message = response.getChoices().get(0).getMessage();
        assertNotNull(message);
        
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            ToolCall toolCall = message.getToolCalls().get(0);
            assertEquals("get_weather", toolCall.getFunction().getName());
            assertNotNull(toolCall.getFunction().getArguments());
            assertTrue(toolCall.getFunction().getArguments().containsKey("location"));
        }
    }
    
    @Test
    void testJsonMode() {
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, 
                                "Extract the following information and return as JSON: " +
                                "Name: John Doe, Age: 30, City: New York")
                ))
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.ResponseType.JSON_OBJECT)
                        .build())
                .temperature(0.1)
                .build();
        
        ModelTextResponse response = client.textToText(request);
        
        assertNotNull(response);
        String content = response.getChoices().get(0).getMessage().getContent();
        assertNotNull(content);
        assertTrue(content.contains("John Doe"));
        assertTrue(content.contains("30"));
        assertTrue(content.contains("New York"));
    }
    
    @Test
    void testDifferentModels() {
        // Test with Haiku model
        client.setModel(ClaudeModelClient.CLAUDE_MINI_DEFAULT);
        
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, "Say 'Hello, World!' in Python")
                ))
                .temperature(0.1)
                .build();
        
        ModelTextResponse response = client.textToText(request);
        
        assertNotNull(response);
        String content = response.getChoices().get(0).getMessage().getContent();
        assertTrue(content.contains("print"));
        assertTrue(content.contains("Hello, World!"));
    }
    
    @Test
    void testCapabilities() {
        Set<Capability> capabilities = client.getCapabilities();
        
        assertTrue(capabilities.contains(ModelClient.Capability.TEXT_TO_TEXT));
        assertTrue(capabilities.contains(ModelClient.Capability.IMAGE_TO_TEXT));
        assertTrue(capabilities.contains(ModelClient.Capability.FUNCTION_CALLING));
        assertTrue(capabilities.contains(ModelClient.Capability.JSON_OBJECT));
        assertTrue(capabilities.contains(ModelClient.Capability.JSON_SCHEMA));
        assertTrue(capabilities.contains(ModelClient.Capability.TOOLS));
        assertFalse(capabilities.contains(ModelClient.Capability.TEXT_TO_IMAGE));
    }
    
    @Test
    void testImageGenerationNotSupported() {
        ModelImageRequest request = ModelImageRequest.builder()
                .prompt("A beautiful sunset")
                .build();
        
        assertThrows(UnsupportedCapabilityException.class, () -> {
            client.textToImage(request);
        });
    }
}