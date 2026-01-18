package ai.driftkit.clients.claude;

import ai.driftkit.clients.claude.domain.ClaudeOutputFormat;
import ai.driftkit.clients.claude.domain.ClaudeSchemaProperty;
import ai.driftkit.clients.claude.utils.ClaudeSchemaConverter;
import ai.driftkit.common.domain.client.ResponseFormat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ClaudeSchemaConverter.
 */
public class ClaudeSchemaConverterTest {

    @Test
    void testConvertNullReturnsNull() {
        assertNull(ClaudeSchemaConverter.convert(null));
    }

    @Test
    void testConvertTextFormatReturnsNull() {
        ResponseFormat format = ResponseFormat.text();
        assertNull(ClaudeSchemaConverter.convert(format));
    }

    @Test
    void testConvertJsonObjectReturnsNull() {
        // JSON_OBJECT is not supported by Claude structured outputs
        ResponseFormat format = ResponseFormat.jsonObject();
        assertNull(ClaudeSchemaConverter.convert(format));
    }

    @Test
    void testConvertSimpleJsonSchema() {
        ResponseFormat.JsonSchema schema = ResponseFormat.JsonSchema.builder()
                .type("object")
                .properties(Map.of(
                        "name", ResponseFormat.SchemaProperty.builder()
                                .type("string")
                                .description("The person's name")
                                .build(),
                        "age", ResponseFormat.SchemaProperty.builder()
                                .type("integer")
                                .build()
                ))
                .required(List.of("name", "age"))
                .build();

        ResponseFormat format = ResponseFormat.builder()
                .type(ResponseFormat.ResponseType.JSON_SCHEMA)
                .jsonSchema(schema)
                .build();

        ClaudeOutputFormat result = ClaudeSchemaConverter.convert(format);

        assertNotNull(result);
        assertEquals("json_schema", result.getType());
        assertNotNull(result.getSchema());
        assertEquals("object", result.getSchema().getType());
        // additionalProperties must be forced to false
        assertEquals(false, result.getSchema().getAdditionalProperties());
        assertNotNull(result.getSchema().getProperties());
        assertEquals(2, result.getSchema().getProperties().size());
        assertTrue(result.getSchema().getProperties().containsKey("name"));
        assertTrue(result.getSchema().getProperties().containsKey("age"));
        assertEquals(List.of("name", "age"), result.getSchema().getRequired());
    }

    @Test
    void testConvertNestedObjectSchema() {
        ResponseFormat.JsonSchema schema = ResponseFormat.JsonSchema.builder()
                .type("object")
                .properties(Map.of(
                        "person", ResponseFormat.SchemaProperty.builder()
                                .type("object")
                                .properties(Map.of(
                                        "firstName", ResponseFormat.SchemaProperty.builder()
                                                .type("string")
                                                .build(),
                                        "lastName", ResponseFormat.SchemaProperty.builder()
                                                .type("string")
                                                .build()
                                ))
                                .required(List.of("firstName", "lastName"))
                                .build()
                ))
                .required(List.of("person"))
                .build();

        ResponseFormat format = ResponseFormat.builder()
                .type(ResponseFormat.ResponseType.JSON_SCHEMA)
                .jsonSchema(schema)
                .build();

        ClaudeOutputFormat result = ClaudeSchemaConverter.convert(format);

        assertNotNull(result);
        // Root level additionalProperties
        assertEquals(false, result.getSchema().getAdditionalProperties());

        // Nested object
        ClaudeSchemaProperty personProp = result.getSchema().getProperties().get("person");
        assertNotNull(personProp);
        assertEquals("object", personProp.getType());
        // Nested object also needs additionalProperties: false
        assertEquals(false, personProp.getAdditionalProperties());
        assertEquals(2, personProp.getProperties().size());
    }

    @Test
    void testConvertArraySchema() {
        ResponseFormat.JsonSchema schema = ResponseFormat.JsonSchema.builder()
                .type("object")
                .properties(Map.of(
                        "tags", ResponseFormat.SchemaProperty.builder()
                                .type("array")
                                .items(ResponseFormat.SchemaProperty.builder()
                                        .type("string")
                                        .build())
                                .build()
                ))
                .required(List.of("tags"))
                .build();

        ResponseFormat format = ResponseFormat.builder()
                .type(ResponseFormat.ResponseType.JSON_SCHEMA)
                .jsonSchema(schema)
                .build();

        ClaudeOutputFormat result = ClaudeSchemaConverter.convert(format);

        assertNotNull(result);
        ClaudeSchemaProperty tagsProp = result.getSchema().getProperties().get("tags");
        assertNotNull(tagsProp);
        assertEquals("array", tagsProp.getType());
        assertNotNull(tagsProp.getItems());
        assertEquals("string", tagsProp.getItems().getType());
    }

    @Test
    void testConvertEnumSchema() {
        ResponseFormat.JsonSchema schema = ResponseFormat.JsonSchema.builder()
                .type("object")
                .properties(Map.of(
                        "status", ResponseFormat.SchemaProperty.builder()
                                .type("string")
                                .enumValues(List.of("pending", "active", "completed"))
                                .build()
                ))
                .required(List.of("status"))
                .build();

        ResponseFormat format = ResponseFormat.builder()
                .type(ResponseFormat.ResponseType.JSON_SCHEMA)
                .jsonSchema(schema)
                .build();

        ClaudeOutputFormat result = ClaudeSchemaConverter.convert(format);

        assertNotNull(result);
        ClaudeSchemaProperty statusProp = result.getSchema().getProperties().get("status");
        assertNotNull(statusProp);
        assertEquals("string", statusProp.getType());
        assertNotNull(statusProp.getEnumValues());
        assertEquals(3, statusProp.getEnumValues().size());
        assertTrue(statusProp.getEnumValues().contains("pending"));
        assertTrue(statusProp.getEnumValues().contains("active"));
        assertTrue(statusProp.getEnumValues().contains("completed"));
    }

    @Test
    void testConvertWithDescription() {
        ResponseFormat.JsonSchema schema = ResponseFormat.JsonSchema.builder()
                .type("object")
                .properties(Map.of(
                        "email", ResponseFormat.SchemaProperty.builder()
                                .type("string")
                                .description("User's email address")
                                .build()
                ))
                .build();

        ResponseFormat format = ResponseFormat.builder()
                .type(ResponseFormat.ResponseType.JSON_SCHEMA)
                .jsonSchema(schema)
                .build();

        ClaudeOutputFormat result = ClaudeSchemaConverter.convert(format);

        assertNotNull(result);
        ClaudeSchemaProperty emailProp = result.getSchema().getProperties().get("email");
        assertNotNull(emailProp);
        assertEquals("User's email address", emailProp.getDescription());
    }

    @Test
    void testAdditionalPropertiesAlwaysForcedToFalse() {
        // Even if DriftKit schema has additionalProperties: true, we force it to false
        ResponseFormat.JsonSchema schema = ResponseFormat.JsonSchema.builder()
                .type("object")
                .properties(Map.of(
                        "data", ResponseFormat.SchemaProperty.builder()
                                .type("string")
                                .build()
                ))
                .additionalProperties(true) // This should be ignored
                .build();

        ResponseFormat format = ResponseFormat.builder()
                .type(ResponseFormat.ResponseType.JSON_SCHEMA)
                .jsonSchema(schema)
                .build();

        ClaudeOutputFormat result = ClaudeSchemaConverter.convert(format);

        assertNotNull(result);
        // Must be false regardless of input
        assertEquals(false, result.getSchema().getAdditionalProperties());
    }

    @Test
    void testValidateSchemaReturnsEmptyForValidSchema() {
        ResponseFormat.JsonSchema schema = ResponseFormat.JsonSchema.builder()
                .type("object")
                .properties(Map.of(
                        "name", ResponseFormat.SchemaProperty.builder()
                                .type("string")
                                .build()
                ))
                .additionalProperties(false)
                .build();

        List<String> errors = ClaudeSchemaConverter.validateSchema(schema);
        assertTrue(errors.isEmpty());
    }

    @Test
    void testValidateNullSchemaReturnsError() {
        List<String> errors = ClaudeSchemaConverter.validateSchema(null);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("null"));
    }
}
