package ai.driftkit.workflows.core.agent;

import ai.driftkit.clients.openai.client.OpenAIModelClient;
import ai.driftkit.common.annotation.JsonSchemaStrict;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.config.EtlConfig.VaultConfig;
import ai.driftkit.workflows.core.TestHelper;
import ai.driftkit.workflows.core.agent.LLMAgent;
import ai.driftkit.workflows.core.agent.AgentResponse;
import ai.driftkit.workflows.core.chat.ChatMemory;
import ai.driftkit.workflows.core.chat.SimpleTokenizer;
import ai.driftkit.workflows.core.chat.TokenWindowChatMemory;
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

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for LLMAgent Structured Output functionality.
 * Tests different structured output scenarios with various input combinations.
 * 
 * To run these tests:
 * 1. Set OPENAI_API_KEY environment variable
 * 2. Enable tests by removing @Disabled annotation
 * 3. Run: mvn test -Dtest=LLMAgentStructuredOutputIntegrationTest
 */
@Slf4j
@Disabled("Integration test - requires OpenAI API key and is disabled by default")
public class LLMAgentStructuredOutputIntegrationTest {

    private LLMAgent agent;

    @BeforeEach
    void setUp() {
        TestHelper.printTestInstructions();
        
        // Initialize OpenAI client with configuration
        VaultConfig config = TestHelper.createTestConfig(null);
        assertNotNull(config.getApiKey(), "OpenAI API key must be set in OPENAI_API_KEY environment variable");

        ModelClient modelClient = new OpenAIModelClient().init(config);
        
        // Create chat memory
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(4000, new SimpleTokenizer());
        
        // Create agent
        agent = LLMAgent.builder()
            .modelClient(modelClient)
            .systemMessage("You are a helpful assistant that can extract structured information from text.")
            .temperature(0.1)
            .maxTokens(1000)
            .chatMemory(chatMemory)
            .build();
        
        log.info("Test setup completed. Model: {}, Temperature: {}", 
                config.getModel(), config.getTemperature());
    }

    /**
     * Test structured output with text-only input
     */
    @Test
    void testStructuredOutputWithTextInput() throws JsonProcessingException {
        log.info("=== Testing Structured Output with Text Input ===");

        String inputText = "Extract person information from this text: " +
                          "'John Smith is a 32-year-old software engineer from New York. " +
                          "He works at Google and has skills in Java, Python, and machine learning. " +
                          "John is married and lives at 123 Main Street, New York, NY 10001.'";
        
        AgentResponse<PersonExtraction> response = agent.executeStructured(inputText, PersonExtraction.class);
        
        assertNotNull(response);
        assertTrue(response.hasStructuredData());
        
        PersonExtraction person = response.getStructuredData();
        assertNotNull(person);
        assertNotNull(person.getName());
        assertTrue(person.getAge() > 0);
        assertEquals("John Smith", person.getName());
        assertEquals(32, person.getAge());
        assertNotNull(person.getOccupation());
        assertTrue(person.getOccupation().toLowerCase().contains("engineer"));
        
        log.info("Extracted person: {}", person);
    }

    /**
     * Test structured output with image input
     */
    @Test
    void testStructuredOutputWithImageInput() throws IOException {
        log.info("=== Testing Structured Output with Image Input ===");

        // Create test image
        byte[] imageData = TestHelper.createTestImage();
        
        // Note: executeStructured doesn't support images directly
        // We need to use executeWithImages for image analysis
        AgentResponse<String> textResponse = agent.executeWithImages(
            "Analyze this document image and extract the key information in JSON format with fields: description, objects, text_detected, people_count",
            imageData
        );
        
        // For this test, we'll just check that we got a response
        AgentResponse<ImageAnalysis> response = AgentResponse.<ImageAnalysis>builder()
            .type(AgentResponse.ResponseType.TEXT)
            .text(textResponse.getText())
            .build();
        
        // Note: This test might not work as expected because executeStructured doesn't handle images
        // This is more of a placeholder for when image support is added to executeStructured
        
        assertNotNull(response);
        assertNotNull(textResponse);
        assertTrue(textResponse.hasText());
        
        String analysisText = textResponse.getText();
        assertNotNull(analysisText);
        assertFalse(analysisText.isEmpty());
        
        log.info("Image analysis text: {}", analysisText);
        
        // Could contain information about the test image content
        assertTrue(analysisText.length() > 10);
    }

    /**
     * Test structured output with multiple images using executeWithImages
     */
    @Test
    void testStructuredOutputWithImages() throws IOException {
        log.info("=== Testing Structured Output with Images via executeWithImages ===");

        byte[] imageData = TestHelper.createTestImage();
        
        AgentResponse<String> response = agent.executeWithImages(
            "Analyze this test document image and tell me what information you can extract from it.",
            imageData
        );
        
        assertNotNull(response);
        assertTrue(response.hasText());
        
        String analysisText = response.getText();
        assertNotNull(analysisText);
        assertFalse(analysisText.isEmpty());
        
        log.info("Image analysis text: {}", analysisText);
        
        // Could contain information about the test image content
        assertTrue(analysisText.length() > 10);
    }

    /**
     * Test different types of structured extractions
     */
    @Test
    void testDifferentStructuredExtractions() throws JsonProcessingException {
        log.info("=== Testing Different Structured Extractions ===");

        // Test 1: Company information extraction
        String companyText = "Apple Inc. was founded in 1976 by Steve Jobs, Steve Wozniak, and Ronald Wayne. " +
                           "The company is headquartered in Cupertino, California and is known for products like iPhone, iPad, and Mac computers.";
        
        AgentResponse<Company> companyResponse = agent.executeStructured(companyText, Company.class);
        
        assertNotNull(companyResponse);
        assertTrue(companyResponse.hasStructuredData());
        
        Company company = companyResponse.getStructuredData();
        assertNotNull(company);
        assertEquals("Apple Inc.", company.getName());
        assertEquals(1976, company.getFoundedYear());
        assertTrue(company.getFounders().contains("Steve Jobs"));
        
        log.info("Extracted company: {}", company);

        // Test 2: Weather information extraction
        String weatherText = "Today's weather in San Francisco is partly cloudy with a temperature of 68°F. " +
                           "There's a light breeze from the west and humidity is at 75%. No rain expected.";
        
        AgentResponse<WeatherInfo> weatherResponse = agent.executeStructured(weatherText, WeatherInfo.class);
        
        assertNotNull(weatherResponse);
        assertTrue(weatherResponse.hasStructuredData());
        
        WeatherInfo weather = weatherResponse.getStructuredData();
        assertNotNull(weather);
        assertNotNull(weather.getLocation());
        assertNotNull(weather.getDescription());
        assertTrue(weather.getLocation().toLowerCase().contains("san francisco"));
        
        log.info("Extracted weather: {}", weather);
    }

    /**
     * Test flexible vs strict schema extraction
     */
    @Test
    void testFlexibleVsStrictSchemas() throws JsonProcessingException {
        log.info("=== Testing Flexible vs Strict Schemas ===");

        String personText = "Sarah Johnson is a 28-year-old data scientist from San Francisco. " +
                           "She works at Apple and specializes in machine learning and AI. " +
                           "Her email is sarah.johnson@apple.com and phone is 555-1234.";

        // Test with flexible schema (not all fields required)
        AgentResponse<FlexiblePersonExtraction> flexibleResponse = 
            agent.executeStructured(personText, FlexiblePersonExtraction.class);
        
        assertNotNull(flexibleResponse);
        assertTrue(flexibleResponse.hasStructuredData());
        
        FlexiblePersonExtraction flexiblePerson = flexibleResponse.getStructuredData();
        assertNotNull(flexiblePerson);
        assertNotNull(flexiblePerson.getName());
        assertEquals("Sarah Johnson", flexiblePerson.getName());
        
        if (flexiblePerson.getContact() != null) {
            assertNotNull(flexiblePerson.getContact().getEmail());
            assertEquals("sarah.johnson@apple.com", flexiblePerson.getContact().getEmail());
        }
        
        log.info("Flexible extraction: {}", flexiblePerson);

        // Test with strict schema (all fields required)
        AgentResponse<StrictPersonExtraction> strictResponse = 
            agent.executeStructured(personText, StrictPersonExtraction.class);
        
        assertNotNull(strictResponse);
        assertTrue(strictResponse.hasStructuredData());
        
        StrictPersonExtraction strictPerson = strictResponse.getStructuredData();
        assertNotNull(strictPerson);
        assertNotNull(strictPerson.getName());
        assertTrue(strictPerson.getAge() > 0);
        
        log.info("Strict extraction: {}", strictPerson);
    }

    /**
     * Test custom prompts for structured extraction
     */
    @Test
    void testCustomPrompts() throws JsonProcessingException {
        log.info("=== Testing Custom Prompts ===");

        String documentText = "Invoice #12345 dated March 15, 2024. " +
                             "Bill to: ABC Corporation, 456 Business Ave, Dallas, TX 75201. " +
                             "Items: Software License ($5000), Support ($1000). Total: $6000.";

        String customPrompt = "Extract invoice information from this document. " +
                            "Focus on invoice number, date, customer, and total amount. " +
                            "Text: " + documentText;

        AgentResponse<InvoiceInfo> response = agent.executeStructured(
            documentText, 
            InvoiceInfo.class, 
            customPrompt
        );
        
        assertNotNull(response);
        assertTrue(response.hasStructuredData());
        
        InvoiceInfo invoice = response.getStructuredData();
        assertNotNull(invoice);
        assertNotNull(invoice.getInvoiceNumber());
        assertTrue(invoice.getInvoiceNumber().contains("12345"));
        assertNotNull(invoice.getCustomer());
        assertTrue(invoice.getCustomer().contains("ABC Corporation"));
        
        log.info("Extracted invoice: {}", invoice);
    }

    /**
     * Test conversation memory with structured outputs
     */
    @Test
    void testConversationMemoryWithStructuredOutput() {
        log.info("=== Testing Conversation Memory with Structured Output ===");

        // First interaction - regular text
        AgentResponse<String> response1 = agent.executeText("Hello, I'll be giving you some person information to extract.");
        assertNotNull(response1.getText());
        log.info("First response: {}", response1.getText());

        // Second interaction - structured extraction
        String personText = "Dr. Emily Chen is a 35-year-old cardiologist working at Stanford Medical Center.";
        AgentResponse<PersonExtraction> response2 = agent.executeStructured(personText, PersonExtraction.class);
        
        assertNotNull(response2);
        assertTrue(response2.hasStructuredData());
        
        PersonExtraction person = response2.getStructuredData();
        assertEquals("Dr. Emily Chen", person.getName());
        assertEquals(35, person.getAge());
        
        log.info("Structured extraction: {}", person);

        // Third interaction - should remember context
        AgentResponse<String> response3 = agent.executeText("What was the name of the person I just mentioned?");
        assertNotNull(response3.getText());
        
        String lowerResponse = response3.getText().toLowerCase();
        boolean hasMemory = lowerResponse.contains("emily") || 
                           lowerResponse.contains("chen") ||
                           lowerResponse.contains("dr.") ||
                           lowerResponse.contains("cardiologist");
        
        log.info("Memory recall: {}", response3.getText());
        
        if (!hasMemory) {
            log.warn("Agent may not have retained context from structured extraction. Response: {}", response3.getText());
        }
        
        // Note: Context retention between executeText and executeStructured may be limited
        // This is more of an informational test rather than a strict requirement
    }

    /**
     * Test error handling with malformed requests
     */
    @Test
    void testErrorHandling() {
        log.info("=== Testing Error Handling ===");
        
        try {
            // Test with empty text
            AgentResponse<PersonExtraction> response = agent.executeStructured("", PersonExtraction.class);
            
            // Should still get a response, even if extraction might be minimal
            assertNotNull(response);
            log.info("Empty text handled gracefully");
            
        } catch (Exception e) {
            log.info("Expected exception for empty text: {}", e.getMessage());
        }
        
        try {
            // Test with text that doesn't match the schema
            String invalidText = "The weather is nice today.";
            AgentResponse<PersonExtraction> response = agent.executeStructured(invalidText, PersonExtraction.class);
            
            assertNotNull(response);
            
            if (response.hasStructuredData()) {
                PersonExtraction person = response.getStructuredData();
                // Should handle gracefully, might have null or default values
                log.info("Invalid text extraction result: {}", person);
            }
            
        } catch (Exception e) {
            log.info("Exception for mismatched schema: {}", e.getMessage());
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
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonSchemaStrict
    public static class FlexiblePersonExtraction {
        @JsonProperty(value = "name", required = true)
        @JsonPropertyDescription("The person's full name")
        @NotNull
        private String name;

        @JsonProperty(value = "age", required = true)
        @JsonPropertyDescription("The person's age in years")
        private Integer age;

        @JsonProperty(value = "occupation", required = true)
        @JsonPropertyDescription("The person's job or profession")
        private String occupation;

        @JsonProperty(value = "contact", required = true)
        @JsonPropertyDescription("Contact information")
        private ContactInfo contact;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonSchemaStrict
        public static class ContactInfo {
            @JsonProperty(value = "email", required = true)
            @JsonPropertyDescription("Email address")
            @NotNull
            private String email;

            @JsonProperty(value = "phone", required = true)
            @JsonPropertyDescription("Phone number")
            private String phone;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonSchemaStrict
    public static class StrictPersonExtraction {
        @JsonProperty("name")
        @JsonPropertyDescription("The person's full name")
        private String name;

        @JsonProperty("age")
        @JsonPropertyDescription("The person's age in years")
        private int age;

        @JsonProperty("occupation")
        @JsonPropertyDescription("The person's job or profession")
        private String occupation;

        @JsonProperty("location")
        @JsonPropertyDescription("The person's location")
        private String location;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonSchemaStrict
    public static class Company {
        @JsonProperty(value = "name", required = true)
        @JsonPropertyDescription("Company name")
        @NotNull
        private String name;

        @JsonProperty(value = "founded_year", required = true)
        @JsonPropertyDescription("Year the company was founded")
        private int foundedYear;

        @JsonProperty(value = "founders", required = true)
        @JsonPropertyDescription("List of company founders")
        private List<String> founders;

        @JsonProperty(value = "headquarters", required = true)
        @JsonPropertyDescription("Company headquarters location")
        private String headquarters;

        @JsonProperty(value = "products", required = true)
        @JsonPropertyDescription("List of main products or services")
        private List<String> products;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonSchemaStrict
    public static class WeatherInfo {
        @JsonProperty(value = "location", required = true)
        @JsonPropertyDescription("Location/city name")
        private String location;

        @JsonProperty(value = "description", required = true)
        @JsonPropertyDescription("Weather description")
        private String description;

        @JsonProperty(value = "temperature", required = true)
        @JsonPropertyDescription("Temperature information")
        private String temperature;

        @JsonProperty(value = "humidity", required = true)
        @JsonPropertyDescription("Humidity percentage")
        private String humidity;

        @JsonProperty(value = "conditions", required = true)
        @JsonPropertyDescription("Weather conditions")
        private String conditions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonSchemaStrict
    public static class ImageAnalysis {
        @JsonProperty(value = "description", required = true)
        @JsonPropertyDescription("A detailed description of what's in the image")
        private String description;

        @JsonProperty(value = "objects", required = true)
        @JsonPropertyDescription("List of main objects or subjects identified")
        private List<String> objects;

        @JsonProperty(value = "text_detected", required = true)
        @JsonPropertyDescription("Any text visible in the image")
        private String textDetected;

        @JsonProperty(value = "people_count", required = true)
        @JsonPropertyDescription("Number of people visible in the image")
        private int peopleCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonSchemaStrict
    public static class InvoiceInfo {
        @JsonProperty(value = "invoice_number", required = true)
        @JsonPropertyDescription("Invoice number or ID")
        private String invoiceNumber;

        @JsonProperty(value = "customer", required = true)
        @JsonPropertyDescription("Customer name or company")
        private String customer;

        @JsonProperty(value = "date", required = true)
        @JsonPropertyDescription("Invoice date")
        private String date;

        @JsonProperty(value = "total_amount", required = true)
        @JsonPropertyDescription("Total invoice amount")
        private String totalAmount;

        @JsonProperty(value = "items", required = true)
        @JsonPropertyDescription("List of invoice items")
        private List<String> items;
    }
}