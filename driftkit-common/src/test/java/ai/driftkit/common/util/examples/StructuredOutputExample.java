package ai.driftkit.common.util.examples;

import ai.driftkit.common.domain.client.ResponseFormat;
import ai.driftkit.common.utils.JsonSchemaGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Example demonstrating how to use structured output functionality with DriftKit.
 * This class shows how to generate JSON schemas from Java classes and use them
 * for structured outputs with AI models.
 */
@Slf4j
public class StructuredOutputExample {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        demonstrateSchemaGeneration();
        demonstrateResponseFormatCreation();
    }

    /**
     * Demonstrates how to generate JSON schemas from Java classes.
     */
    public static void demonstrateSchemaGeneration() {
        log.info("=== JSON Schema Generation Example ===");

        // Generate schema for Person class
        ResponseFormat.JsonSchema personSchema = JsonSchemaGenerator.generateSchema(Person.class);
        
        try {
            String schemaJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(personSchema);

            log.info("Generated schema for Person class:\n{}", schemaJson);
        } catch (JsonProcessingException e) {
            log.error("Error serializing schema: {}", e.getMessage());
        }

        // Generate schema with custom title
        ResponseFormat.JsonSchema customSchema = JsonSchemaGenerator.generateSchema(
                Person.class, "PersonProfile");
        
        log.info("Schema with custom title: {}", customSchema.getTitle());
    }

    /**
     * Demonstrates how to create ResponseFormat objects for structured outputs.
     */
    public static void demonstrateResponseFormatCreation() {
        log.info("=== Response Format Creation Example ===");

        // Create response format using the new jsonSchema method
        ResponseFormat responseFormat = ResponseFormat.jsonSchema(Person.class);
        
        log.info("Created ResponseFormat:");
        log.info("  Type: {}", responseFormat.getType());
        log.info("  Schema Title: {}", responseFormat.getJsonSchema().getTitle());
        log.info("  Schema Type: {}", responseFormat.getJsonSchema().getType());
        log.info("  Properties Count: {}", responseFormat.getJsonSchema().getProperties().size());
        log.info("  Required Fields: {}", responseFormat.getJsonSchema().getRequired());

        // Example of how you would use this with a model client:
        /*
        ModelTextRequest request = ModelTextRequest.builder()
            .messages(Arrays.asList(
                ModelImageResponse.ModelContentMessage.builder()
                    .role(Role.user)
                    .content(Arrays.asList(
                        ModelContentElement.builder()
                            .type(MessageType.text)
                            .text("Extract person information from: 'John Doe is a 30-year-old software engineer living in New York.'")
                            .build()
                    ))
                    .build()
            ))
            .responseFormat(responseFormat) // <-- Use structured output here
            .build();
        
        ModelTextResponse response = modelClient.textToText(request);
        // The response will be structured according to the Person schema
        */

        log.info("Example shows how to use ResponseFormat.jsonSchema(Person.class) with model requests");
    }

    /**
     * Demonstrates working with nested objects and arrays in schemas.
     */
    public static void demonstrateNestedObjects() {
        log.info("=== Nested Object Schema Example ===");

        ResponseFormat.JsonSchema schema = JsonSchemaGenerator.generateSchema(Person.class);
        
        // Check address property (nested object)
        ResponseFormat.SchemaProperty addressProperty = schema.getProperties().get("address");
        if (addressProperty != null) {
            log.info("Address property type: {}", addressProperty.getType());
            log.info("Address nested properties: {}", addressProperty.getProperties().keySet());
        }

        // Check skills property (array)
        ResponseFormat.SchemaProperty skillsProperty = schema.getProperties().get("skills");
        if (skillsProperty != null) {
            log.info("Skills property type: {}", skillsProperty.getType());
            if (skillsProperty.getItems() != null) {
                log.info("Skills array item type: {}", skillsProperty.getItems().getType());
            }
        }

        // Check marital status property (enum)
        ResponseFormat.SchemaProperty maritalStatusProperty = schema.getProperties().get("marital_status");
        if (maritalStatusProperty != null) {
            log.info("Marital status type: {}", maritalStatusProperty.getType());
            log.info("Marital status enum values: {}", maritalStatusProperty.getEnumValues());
        }
    }
}