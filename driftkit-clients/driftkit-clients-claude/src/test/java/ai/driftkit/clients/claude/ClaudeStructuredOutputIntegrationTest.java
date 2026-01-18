package ai.driftkit.clients.claude;

import ai.driftkit.clients.claude.client.ClaudeModelClient;
import ai.driftkit.common.domain.client.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import ai.driftkit.config.EtlConfig.VaultConfig;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Claude Structured Outputs feature.
 * Requires CLAUDE_API_KEY environment variable to be set.
 */
@EnabledIfEnvironmentVariable(named = "CLAUDE_API_KEY", matches = ".+")
public class ClaudeStructuredOutputIntegrationTest {

    private ClaudeModelClient client;
    private static final String API_KEY = System.getenv("CLAUDE_API_KEY");

    @BeforeEach
    void setUp() {
        assertNotNull(API_KEY, "CLAUDE_API_KEY environment variable must be set");

        VaultConfig config = VaultConfig.builder()
                .apiKey(API_KEY)
                .model("claude-sonnet-4-5-20250929")
                .temperature(0.0) // Deterministic for testing
                .maxTokens(1024)
                .build();

        client = new ClaudeModelClient();
        client.init(config);
    }

    // --- Test POJO classes ---

    @Data
    public static class ContactInfo {
        @JsonProperty("name")
        @JsonPropertyDescription("Full name of the person")
        private String name;

        @JsonProperty("email")
        @JsonPropertyDescription("Email address")
        private String email;

        @JsonProperty("phone")
        @JsonPropertyDescription("Phone number if available")
        private String phone;

        @JsonProperty("company")
        @JsonPropertyDescription("Company name if mentioned")
        private String company;
    }

    @Data
    public static class SentimentAnalysis {
        @JsonProperty("sentiment")
        @JsonPropertyDescription("Overall sentiment: positive, negative, or neutral")
        private String sentiment;

        @JsonProperty("confidence")
        @JsonPropertyDescription("Confidence score from 0.0 to 1.0")
        private Double confidence;

        @JsonProperty("key_phrases")
        @JsonPropertyDescription("Key phrases that influenced the sentiment")
        private List<String> keyPhrases;
    }

    @Data
    public static class ProductInfo {
        @JsonProperty("name")
        private String name;

        @JsonProperty("price")
        private Double price;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("in_stock")
        private Boolean inStock;

        @JsonProperty("categories")
        private List<String> categories;
    }

    // --- Tests ---

    @Test
    void testExtractContactInfo() throws Exception {
        String input = "Please contact John Smith at john.smith@example.com or call 555-123-4567. He works at Acme Corp.";

        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user,
                                "Extract contact information from this text: " + input)
                ))
                .responseFormat(ResponseFormat.jsonSchema(ContactInfo.class))
                .build();

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        assertNotNull(response.getChoices());
        assertFalse(response.getChoices().isEmpty());

        String content = response.getResponse();
        System.out.println("Raw response: " + content);

        // Parse as typed object
        ContactInfo contact = response.getResponseJson(ContactInfo.class);

        assertNotNull(contact);
        assertEquals("John Smith", contact.getName());
        assertEquals("john.smith@example.com", contact.getEmail());
        assertNotNull(contact.getPhone());
        assertTrue(contact.getPhone().contains("555"));
        assertEquals("Acme Corp", contact.getCompany());

        System.out.println("Parsed contact: " + contact);
    }

    @Test
    void testSentimentAnalysis() throws Exception {
        String input = "I absolutely love this product! It exceeded all my expectations and the customer service was fantastic.";

        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user,
                                "Analyze the sentiment of this review: " + input)
                ))
                .responseFormat(ResponseFormat.jsonSchema(SentimentAnalysis.class))
                .build();

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        String content = response.getResponse();
        System.out.println("Raw response: " + content);

        SentimentAnalysis analysis = response.getResponseJson(SentimentAnalysis.class);

        assertNotNull(analysis);
        assertEquals("positive", analysis.getSentiment().toLowerCase());
        assertNotNull(analysis.getConfidence());
        assertTrue(analysis.getConfidence() >= 0.7, "Expected high confidence for clearly positive text");
        assertNotNull(analysis.getKeyPhrases());
        assertFalse(analysis.getKeyPhrases().isEmpty());

        System.out.println("Parsed sentiment: " + analysis);
    }

    @Test
    void testExtractProductInfo() throws Exception {
        String input = "The new iPhone 15 Pro is available for $999 USD. It's currently in stock. Categories: smartphones, electronics, Apple.";

        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user,
                                "Extract product information: " + input)
                ))
                .responseFormat(ResponseFormat.jsonSchema(ProductInfo.class))
                .build();

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        String content = response.getResponse();
        System.out.println("Raw response: " + content);

        ProductInfo product = response.getResponseJson(ProductInfo.class);

        assertNotNull(product);
        assertTrue(product.getName().toLowerCase().contains("iphone"));
        assertEquals(999.0, product.getPrice(), 0.01);
        assertEquals("USD", product.getCurrency());
        assertTrue(product.getInStock());
        assertNotNull(product.getCategories());
        assertTrue(product.getCategories().size() >= 2);

        System.out.println("Parsed product: " + product);
    }

    @Test
    void testNestedObjectStructure() throws Exception {
        @Data
        class Address {
            @JsonProperty("street")
            private String street;
            @JsonProperty("city")
            private String city;
            @JsonProperty("country")
            private String country;
        }

        @Data
        class Person {
            @JsonProperty("name")
            private String name;
            @JsonProperty("age")
            private Integer age;
            @JsonProperty("address")
            private Address address;
        }

        // Note: For nested classes we need to use manual schema
        // or use top-level classes. Using simpler test here.

        String input = "John Doe, 35 years old, lives at 123 Main St, New York, USA";

        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.system,
                                "Extract person info with their address as a nested object."),
                        ModelContentMessage.create(Role.user, input)
                ))
                .responseFormat(ResponseFormat.jsonSchema(ContactInfo.class)) // Using simpler class
                .build();

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        assertNotNull(response.getResponse());
        System.out.println("Response: " + response.getResponse());
    }

    @Test
    void testEmptyInputHandling() throws Exception {
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user,
                                "Extract contact info from this text: 'No contact information here.'")
                ))
                .responseFormat(ResponseFormat.jsonSchema(ContactInfo.class))
                .build();

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        ContactInfo contact = response.getResponseJson(ContactInfo.class);
        assertNotNull(contact);
        // Fields should be null or empty for missing data
        System.out.println("Contact from empty input: " + contact);
    }

    @Test
    void testResponseIsValidJson() throws Exception {
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user,
                                "Analyze: Great product, highly recommend!")
                ))
                .responseFormat(ResponseFormat.jsonSchema(SentimentAnalysis.class))
                .build();

        ModelTextResponse response = client.textToText(request);

        assertNotNull(response);
        String content = response.getResponse();

        // Verify it's valid JSON
        assertNotNull(content);
        assertTrue(content.trim().startsWith("{"), "Response should be JSON object");
        assertTrue(content.trim().endsWith("}"), "Response should be JSON object");

        // Should parse without exception
        assertDoesNotThrow(() -> response.getResponseJson(SentimentAnalysis.class));
    }
}
