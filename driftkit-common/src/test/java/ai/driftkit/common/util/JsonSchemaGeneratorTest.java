package ai.driftkit.common.util;

import ai.driftkit.common.domain.client.ResponseFormat;
import ai.driftkit.common.util.examples.Person;
import ai.driftkit.common.utils.JsonSchemaGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonSchemaGenerator.
 */
class JsonSchemaGeneratorTest {

    @Test
    void testGenerateSchemaForSimpleClass() {
        // Test basic schema generation
        ResponseFormat.JsonSchema schema = JsonSchemaGenerator.generateSchema(Person.class);
        
        assertNotNull(schema);
        assertEquals("Person", schema.getTitle());
        assertEquals("object", schema.getType());
        assertFalse(schema.getAdditionalProperties());
        
        // Check that properties are generated
        assertNotNull(schema.getProperties());
        assertTrue(schema.getProperties().containsKey("name"));
        assertTrue(schema.getProperties().containsKey("age"));
        assertTrue(schema.getProperties().containsKey("email"));
        
        // Check required fields
        assertNotNull(schema.getRequired());
        assertTrue(schema.getRequired().contains("name"));
        assertTrue(schema.getRequired().contains("age"));
    }

    @Test
    void testGenerateSchemaWithCustomTitle() {
        ResponseFormat.JsonSchema schema = JsonSchemaGenerator.generateSchema(Person.class, "CustomPerson");
        
        assertNotNull(schema);
        assertEquals("CustomPerson", schema.getTitle());
    }

    @Test
    void testStringPropertyGeneration() {
        ResponseFormat.JsonSchema schema = JsonSchemaGenerator.generateSchema(Person.class);
        
        ResponseFormat.SchemaProperty nameProperty = schema.getProperties().get("name");
        assertNotNull(nameProperty);
        assertEquals("string", nameProperty.getType());
        assertEquals("The person's full name", nameProperty.getDescription());
    }

    @Test
    void testIntegerPropertyGeneration() {
        ResponseFormat.JsonSchema schema = JsonSchemaGenerator.generateSchema(Person.class);
        
        ResponseFormat.SchemaProperty ageProperty = schema.getProperties().get("age");
        assertNotNull(ageProperty);
        assertEquals("integer", ageProperty.getType());
        assertEquals("The person's age in years", ageProperty.getDescription());
    }

    @Test
    void testArrayPropertyGeneration() {
        ResponseFormat.JsonSchema schema = JsonSchemaGenerator.generateSchema(Person.class);
        
        ResponseFormat.SchemaProperty skillsProperty = schema.getProperties().get("skills");
        assertNotNull(skillsProperty);
        assertEquals("array", skillsProperty.getType());
        assertEquals("List of skills the person has", skillsProperty.getDescription());
        
        // Check array items
        assertNotNull(skillsProperty.getItems());
        assertEquals("string", skillsProperty.getItems().getType());
    }

    @Test
    void testEnumPropertyGeneration() {
        ResponseFormat.JsonSchema schema = JsonSchemaGenerator.generateSchema(Person.class);
        
        ResponseFormat.SchemaProperty maritalStatusProperty = schema.getProperties().get("marital_status");
        assertNotNull(maritalStatusProperty);
        assertEquals("string", maritalStatusProperty.getType());
        assertEquals("The person's marital status", maritalStatusProperty.getDescription());
        
        // Check enum values
        assertNotNull(maritalStatusProperty.getEnumValues());
        assertTrue(maritalStatusProperty.getEnumValues().contains("SINGLE"));
        assertTrue(maritalStatusProperty.getEnumValues().contains("MARRIED"));
        assertTrue(maritalStatusProperty.getEnumValues().contains("DIVORCED"));
        assertTrue(maritalStatusProperty.getEnumValues().contains("WIDOWED"));
    }

    @Test
    void testNestedObjectPropertyGeneration() {
        ResponseFormat.JsonSchema schema = JsonSchemaGenerator.generateSchema(Person.class);
        
        ResponseFormat.SchemaProperty addressProperty = schema.getProperties().get("address");
        assertNotNull(addressProperty);
        assertEquals("object", addressProperty.getType());
        assertEquals("The person's address information", addressProperty.getDescription());
        
        // Check nested properties
        assertNotNull(addressProperty.getProperties());
        assertTrue(addressProperty.getProperties().containsKey("street"));
        assertTrue(addressProperty.getProperties().containsKey("city"));
        assertTrue(addressProperty.getProperties().containsKey("postal_code"));
        
        // Check nested required fields
        assertNotNull(addressProperty.getRequired());
        assertTrue(addressProperty.getRequired().contains("street"));
        assertTrue(addressProperty.getRequired().contains("city"));
        assertTrue(addressProperty.getRequired().contains("postal_code"));
    }

    @Test
    void testResponseFormatJsonSchema() {
        ResponseFormat responseFormat = ResponseFormat.jsonSchema(Person.class);
        
        assertNotNull(responseFormat);
        assertEquals("json_schema", responseFormat.getType());
        
        ResponseFormat.JsonSchema schema = responseFormat.getJsonSchema();
        assertNotNull(schema);
        assertEquals("Person", schema.getTitle());
        assertEquals("object", schema.getType());
    }

    @Test
    void testResponseFormatJsonObjectWithClass() {
        ResponseFormat responseFormat = ResponseFormat.jsonObject(Person.class);
        
        assertNotNull(responseFormat);
        assertEquals("json_schema", responseFormat.getType());
        
        ResponseFormat.JsonSchema schema = responseFormat.getJsonSchema();
        assertNotNull(schema);
        assertEquals("Person", schema.getTitle());
    }

    @Test
    void testResponseFormatJsonObjectWithoutClass() {
        ResponseFormat responseFormat = ResponseFormat.jsonObject();
        
        assertNotNull(responseFormat);
        assertEquals("json_object", responseFormat.getType());
        assertNull(responseFormat.getJsonSchema());
    }
}