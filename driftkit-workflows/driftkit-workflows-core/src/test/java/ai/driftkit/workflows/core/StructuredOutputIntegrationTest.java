package ai.driftkit.workflows.core;

import ai.driftkit.clients.openai.client.OpenAIModelClient;
import ai.driftkit.common.annotation.JsonSchemaStrict;
import ai.driftkit.common.domain.client.ResponseFormat;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelContentMessage.ModelContentElement;
import ai.driftkit.common.domain.client.ModelTextRequest.MessageType;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.config.EtlConfig.VaultConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for OpenAI Structured Output functionality.
 * Tests different ResponseFormat types with various input combinations.
 * 
 * To run these tests:
 * 1. Set OPENAI_API_KEY environment variable
 * 2. Enable tests by removing @Disabled annotation
 * 3. Run: mvn test -Dtest=StructuredOutputIntegrationTest
 */
@Slf4j
@Disabled("Integration test - requires OpenAI API key and is disabled by default")
public class StructuredOutputIntegrationTest {

    private ModelClient modelClient;

    @BeforeEach
    void setUp() {
        TestHelper.printTestInstructions();
        
        // Initialize OpenAI client with configuration
        VaultConfig config = TestHelper.createTestConfig(null);
        assertNotNull(config.getApiKey(), "OpenAI API key must be set in OPENAI_API_KEY environment variable");

        modelClient = new OpenAIModelClient().init(config);
        
        log.info("Test setup completed. Model: {}, Temperature: {}", 
                config.getModel(), config.getTemperature());
    }

    /**
     * Test structured output with text-only input
     */
    @Test
    void testStructuredOutputWithTextInput() throws JsonProcessingException {
        log.info("=== Testing Structured Output with Text Input ===");

        // Create structured response format
        ResponseFormat responseFormat = ResponseFormat.jsonSchema(PersonExtraction.class);
        
        ModelTextRequest request = ModelTextRequest.builder()
            .messages(Arrays.asList(
                ModelContentMessage.builder()
                    .role(Role.user)
                    .content(Arrays.asList(
                        ModelContentElement.builder()
                            .type(MessageType.text)
                            .text("Extract person information from this text: " +
                                  "'John Smith is a 32-year-old software engineer from New York. " +
                                  "He works at Google and has skills in Java, Python, and machine learning. " +
                                  "John is married and lives at 123 Main Street, New York, NY 10001.'")
                            .build()
                    ))
                    .build()
            ))
            .responseFormat(responseFormat)
            .build();

        ModelTextResponse response = modelClient.textToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getChoices());
        assertFalse(response.getChoices().isEmpty());
        
        String content = response.getChoices().get(0).getMessage().getContent();
        log.info("Text input response: {}", content);
        
        // Verify the response can be parsed as valid JSON matching our schema
        PersonExtraction person = JsonUtils.fromJson(content, PersonExtraction.class);
        assertNotNull(person);
        assertNotNull(person.getName());
        assertTrue(person.getAge() > 0);
        
        log.info("Extracted person: {}", person);
    }

    /**
     * Test structured output with image-only input
     */
    @Test
    void testStructuredOutputWithImageInput() throws IOException {
        log.info("=== Testing Structured Output with Image Input ===");

        // Load test image (you'll need to provide this)
        byte[] imageData = loadTestImage();
        
        if (imageData == null) {
            log.warn("Skipping image test - no test image available");
            return;
        }

        ResponseFormat responseFormat = ResponseFormat.jsonSchema(ImageAnalysis.class);
        
        ModelTextRequest request = ModelTextRequest.builder()
            .messages(Arrays.asList(
                ModelContentMessage.builder()
                    .role(Role.user)
                    .content(Arrays.asList(
                        ModelContentElement.builder()
                            .type(MessageType.text)
                            .text("Analyze this image and extract the key information in the specified format.")
                            .build(),
                        ModelContentElement.builder()
                            .type(MessageType.image)
                            .image(ModelContentElement.ImageData.builder()
                                .image(imageData)
                                .build())
                            .build()
                    ))
                    .build()
            ))
            .responseFormat(responseFormat)
            .build();

        ModelTextResponse response = modelClient.imageToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getChoices());
        assertFalse(response.getChoices().isEmpty());
        
        String content = response.getChoices().get(0).getMessage().getContent();
        log.info("Image input response: {}", content);
        
        ImageAnalysis analysis = JsonUtils.fromJson(content, ImageAnalysis.class);
        assertNotNull(analysis);
        assertNotNull(analysis.getDescription());
        
        log.info("Image analysis: {}", analysis);
    }

    /**
     * Test structured output with combined text and image input
     */
    @Test
    void testStructuredOutputWithTextAndImageInput() throws IOException {
        log.info("=== Testing Structured Output with Text and Image Input ===");

        byte[] imageData = loadTestImage();
        
        if (imageData == null) {
            log.warn("Skipping text+image test - no test image available");
            return;
        }

        ResponseFormat responseFormat = ResponseFormat.jsonSchema(DocumentAnalysis.class);
        
        ModelTextRequest request = ModelTextRequest.builder()
            .messages(Arrays.asList(
                ModelContentMessage.builder()
                    .role(Role.user)
                    .content(Arrays.asList(
                        ModelContentElement.builder()
                            .type(MessageType.text)
                            .text("Please analyze this document image and extract the information. " +
                                  "Focus on identifying any people, dates, and key content.")
                            .build(),
                        ModelContentElement.builder()
                            .type(MessageType.image)
                            .image(ModelContentElement.ImageData.builder()
                                .image(imageData)
                                .build())
                            .build()
                    ))
                    .build()
            ))
            .responseFormat(responseFormat)
            .build();

        ModelTextResponse response = modelClient.imageToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getChoices());
        assertFalse(response.getChoices().isEmpty());
        
        String content = response.getChoices().get(0).getMessage().getContent();
        log.info("Text+Image input response: {}", content);
        
        DocumentAnalysis analysis = JsonUtils.fromJson(content, DocumentAnalysis.class);
        assertNotNull(analysis);
        
        log.info("Document analysis: {}", analysis);
    }

    /**
     * Test different ResponseFormat types
     */
    @Test
    void testDifferentResponseFormats() throws JsonProcessingException {
        log.info("=== Testing Different Response Format Types ===");

        String prompt = "Describe the weather today in a brief sentence.";

        // Test 1: No structured output (text format)
        log.info("Testing text format...");
        ModelTextRequest textRequest = ModelTextRequest.builder()
            .messages(Arrays.asList(
                ModelContentMessage.builder()
                    .role(Role.user)
                    .content(Arrays.asList(
                        ModelContentElement.builder()
                            .type(MessageType.text)
                            .text(prompt)
                            .build()
                    ))
                    .build()
            ))
            .responseFormat(ResponseFormat.text())
            .build();

        ModelTextResponse textResponse = modelClient.textToText(textRequest);
        log.info("Text format response: {}", textResponse.getChoices().get(0).getMessage().getContent());

        // Test 2: JSON object format (unstructured JSON)
        log.info("Testing json_object format...");
        ModelTextRequest jsonRequest = ModelTextRequest.builder()
            .messages(Arrays.asList(
                ModelContentMessage.builder()
                    .role(Role.user)
                    .content(Arrays.asList(
                        ModelContentElement.builder()
                            .type(MessageType.text)
                            .text(prompt + " Respond in JSON format with 'weather' and 'temperature' fields.")
                            .build()
                    ))
                    .build()
            ))
            .responseFormat(ResponseFormat.jsonObject())
            .build();

        ModelTextResponse jsonResponse = modelClient.textToText(jsonRequest);
        String jsonContent = jsonResponse.getChoices().get(0).getMessage().getContent();
        log.info("JSON object format response: {}", jsonContent);
        
        // Verify it's valid JSON
        assertTrue(JsonUtils.isValidJSON(jsonContent));

        // Test 3: Structured JSON schema
        log.info("Testing json_schema format...");
        ModelTextRequest schemaRequest = ModelTextRequest.builder()
            .messages(Arrays.asList(
                ModelContentMessage.builder()
                    .role(Role.user)
                    .content(Arrays.asList(
                        ModelContentElement.builder()
                            .type(MessageType.text)
                            .text(prompt)
                            .build()
                    ))
                    .build()
            ))
            .responseFormat(ResponseFormat.jsonSchema(WeatherInfo.class))
            .build();

        ModelTextResponse schemaResponse = modelClient.textToText(schemaRequest);
        String schemaContent = schemaResponse.getChoices().get(0).getMessage().getContent();
        log.info("JSON schema format response: {}", schemaContent);
        
        // Verify it matches our schema
        WeatherInfo weather = JsonUtils.fromJson(schemaContent, WeatherInfo.class);
        assertNotNull(weather);
        assertNotNull(weather.getDescription());
    }

    /**
     * Load test image from resources or create a synthetic one
     */
    private byte[] loadTestImage() {
        try {
            // Try to load from test resources first
            Path imagePath = Paths.get("src/test/resources/test-image.jpg");
            if (Files.exists(imagePath)) {
                log.info("Using real test image from: {}", imagePath);
                return Files.readAllBytes(imagePath);
            }
            
            // Try alternative locations
            imagePath = Paths.get("test-image.jpg");
            if (Files.exists(imagePath)) {
                log.info("Using real test image from: {}", imagePath);
                return Files.readAllBytes(imagePath);
            }
            
            // Create synthetic test image
            log.info("No real test image found, creating synthetic test image");
            return TestHelper.createTestImage();
            
        } catch (IOException e) {
            log.warn("Could not load or create test image: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Test to verify schema generation works correctly
     */
    @Test
    void testSchemaGeneration() throws JsonProcessingException {
        log.info("=== Testing Schema Generation ===");
        
        // Test PersonExtraction schema
        ResponseFormat personFormat = ResponseFormat.jsonSchema(PersonExtraction.class);
        assertNotNull(personFormat);
        assertEquals("json_schema", personFormat.getType());
        assertNotNull(personFormat.getJsonSchema());
        assertEquals("PersonExtraction", personFormat.getJsonSchema().getTitle());
        
        log.info("PersonExtraction schema: {}", 
                JsonUtils.toJson(personFormat.getJsonSchema()));
        
        // Test ImageAnalysis schema
        ResponseFormat imageFormat = ResponseFormat.jsonSchema(ImageAnalysis.class);
        assertNotNull(imageFormat.getJsonSchema());
        assertEquals("ImageAnalysis", imageFormat.getJsonSchema().getTitle());
        
        log.info("ImageAnalysis schema generated successfully");
        
        // Verify schema has required fields
        assertTrue(personFormat.getJsonSchema().getRequired().contains("name"));
        assertTrue(personFormat.getJsonSchema().getRequired().contains("age"));
        assertTrue(imageFormat.getJsonSchema().getRequired().contains("description"));
    }

    /**
     * Test FlexiblePersonExtraction without structured output (text response)
     */
    @Test
    void testFlexiblePersonExtractionWithoutStructuredOutput() throws JsonProcessingException {
        log.info("=== Testing FlexiblePersonExtraction without Structured Output ===");

        // Test without structured output - just regular text response
        ModelTextRequest request = ModelTextRequest.builder()
            .messages(Arrays.asList(
                ModelContentMessage.builder()
                    .role(Role.user)
                    .content(Arrays.asList(
                        ModelContentElement.builder()
                            .type(MessageType.text)
                            .text("Extract person information from this text in a natural way: " +
                                  "'Sarah Johnson is a 28-year-old data scientist from San Francisco. " +
                                  "She works at Apple and specializes in machine learning and AI. " +
                                  "Her email is sarah.johnson@apple.com and phone is 555-1234. " +
                                  "Sarah has skills in Python, TensorFlow, and data visualization.'")
                            .build()
                    ))
                    .build()
            ))
            // No responseFormat specified - should return plain text
            .build();

        ModelTextResponse response = modelClient.textToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getChoices());
        assertFalse(response.getChoices().isEmpty());
        
        String content = response.getChoices().get(0).getMessage().getContent();
        log.info("Text response (no structured output): {}", content);
        
        // Verify we got a text response (not JSON)
        assertNotNull(content);
        assertFalse(content.trim().isEmpty());
        
        // Should not be valid JSON since no structured output was requested
        assertFalse(JsonUtils.isValidJSON(content.trim()));
        
        // Now test with structured output for comparison
        log.info("=== Now testing same input WITH structured output ===");
        
        ResponseFormat responseFormat = ResponseFormat.jsonSchema(FlexiblePersonExtraction.class);
        
        ModelTextRequest structuredRequest = ModelTextRequest.builder()
            .messages(Arrays.asList(
                ModelContentMessage.builder()
                    .role(Role.user)
                    .content(Arrays.asList(
                        ModelContentElement.builder()
                            .type(MessageType.text)
                            .text("Extract person information from this text: " +
                                  "'Sarah Johnson is a 28-year-old data scientist from San Francisco. " +
                                  "She works at Apple and specializes in machine learning and AI. " +
                                  "Her email is sarah.johnson@apple.com and phone is 555-1234. " +
                                  "Sarah has skills in Python, TensorFlow, and data visualization.'")
                            .build()
                    ))
                    .build()
            ))
            .responseFormat(responseFormat)
            .build();

        ModelTextResponse structuredResponse = modelClient.textToText(structuredRequest);
        
        assertNotNull(structuredResponse);
        String structuredContent = structuredResponse.getChoices().get(0).getMessage().getContent();
        log.info("Structured response (with schema): {}", structuredContent);
        
        // Should be valid JSON matching our FlexiblePersonExtraction schema
        assertTrue(JsonUtils.isValidJSON(structuredContent));
        
        FlexiblePersonExtraction person = JsonUtils.fromJson(structuredContent, FlexiblePersonExtraction.class);
        assertNotNull(person);
        assertNotNull(person.getName());
        assertNotNull(person.getContact());
        assertNotNull(person.getContact().getEmail());
        
        log.info("Extracted flexible person: {}", person);
        
        // Verify flexible schema - only name and contact.email should be required
        assertEquals("Sarah Johnson", person.getName());
        assertEquals("sarah.johnson@apple.com", person.getContact().getEmail());
    }

    /**
     * Test error handling with malformed requests
     */
    @Test
    void testErrorHandling() {
        log.info("=== Testing Error Handling ===");
        
        try {
            // Test with empty message
            ModelTextRequest request = ModelTextRequest.builder()
                .messages(Arrays.asList(
                    ModelContentMessage.builder()
                        .role(Role.user)
                        .content(Arrays.asList(
                            ModelContentElement.builder()
                                .type(MessageType.text)
                                .text("")
                                .build()
                        ))
                        .build()
                ))
                .responseFormat(ResponseFormat.jsonSchema(PersonExtraction.class))
                .build();

            ModelTextResponse response = modelClient.textToText(request);
            
            // Should still get a response, even if it might be empty or an error
            assertNotNull(response);
            log.info("Empty message handled gracefully");
            
        } catch (Exception e) {
            log.info("Expected exception for empty message: {}", e.getMessage());
        }
    }

    // ============= Test Data Classes =============

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonSchemaStrict
    public static class PersonExtraction {
        @JsonProperty(value = "name", required = true)
        @JsonPropertyDescription("The person's full name")
        private String name;

        @JsonProperty(value = "age", required = true)
        @JsonPropertyDescription("The person's age in years")
        private int age;

        @JsonProperty("occupation")
        @JsonPropertyDescription("The person's job or profession")
        private String occupation;

        @JsonProperty("company")
        @JsonPropertyDescription("The company they work for")
        private String company;

        @JsonProperty("skills")
        @JsonPropertyDescription("List of their professional skills")
        private List<String> skills;

        @JsonProperty("marital_status")
        @JsonPropertyDescription("Their marital status")
        private String maritalStatus;

        @JsonProperty("address")
        @JsonPropertyDescription("Their address information")
        private Address address;

        private PersonType type;

        public enum PersonType {
            CUSTOMER,
            DOCTOR,
            MANAGER,
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonSchemaStrict
        public static class Address {
            @JsonProperty("street")
            @JsonPropertyDescription("Street address")
            private String street;

            @JsonProperty("city")
            @JsonPropertyDescription("City name")
            private String city;

            @JsonProperty("state")
            @JsonPropertyDescription("State abbreviation")
            private String state;

            @JsonProperty("postal_code")
            @JsonPropertyDescription("ZIP or postal code")
            private String postalCode;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageAnalysis {
        @JsonProperty(value = "description", required = true)
        @JsonPropertyDescription("A detailed description of what's in the image")
        private String description;

        @JsonProperty("objects")
        @JsonPropertyDescription("List of main objects or subjects identified")
        private List<String> objects;

        @JsonProperty("colors")
        @JsonPropertyDescription("Dominant colors in the image")
        private List<String> colors;

        @JsonProperty("scene_type")
        @JsonPropertyDescription("Type of scene (indoor, outdoor, portrait, etc.)")
        private String sceneType;

        @JsonProperty("people_count")
        @JsonPropertyDescription("Number of people visible in the image")
        private int peopleCount;

        @JsonProperty("text_detected")
        @JsonPropertyDescription("Any text visible in the image")
        private String textDetected;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentAnalysis {
        @JsonProperty(value = "document_type", required = true)
        @JsonPropertyDescription("Type of document (letter, form, invoice, etc.)")
        private String documentType;

        @JsonProperty("key_information")
        @JsonPropertyDescription("Main information extracted from the document")
        private String keyInformation;

        @JsonProperty("people_mentioned")
        @JsonPropertyDescription("Names of people mentioned in the document")
        private List<String> peopleMentioned;

        @JsonProperty("dates")
        @JsonPropertyDescription("Important dates found in the document")
        private List<String> dates;

        @JsonProperty("companies")
        @JsonPropertyDescription("Company names mentioned")
        private List<String> companies;

        @JsonProperty("summary")
        @JsonPropertyDescription("Brief summary of the document content")
        private String summary;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeatherInfo {
        @JsonProperty(value = "description", required = true)
        @JsonPropertyDescription("Description of the weather")
        private String description;

        @JsonProperty("temperature")
        @JsonPropertyDescription("Temperature information")
        private String temperature;

        @JsonProperty("conditions")
        @JsonPropertyDescription("Weather conditions (sunny, cloudy, rainy, etc.)")
        private String conditions;
    }
}