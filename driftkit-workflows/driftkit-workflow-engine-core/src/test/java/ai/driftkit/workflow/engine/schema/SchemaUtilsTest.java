package ai.driftkit.workflow.engine.schema;

import ai.driftkit.workflow.engine.schema.AIFunctionSchema.AIFunctionProperty;
import ai.driftkit.workflow.engine.schema.annotations.SchemaClass;
import com.fasterxml.jackson.annotation.JsonAlias;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaUtilsTest {

    @BeforeEach
    void setUp() {
        SchemaUtils.clearCache();
    }

    @Test
    void testSystemSchemaGeneration() {
        // Test that @SchemaSystem annotation is properly read
        AIFunctionSchema schema = SchemaUtils.getSchemaFromClass(SystemNotificationSchema.class);
        
        assertNotNull(schema);
        assertEquals("system.notification", schema.getSchemaName());
        assertTrue(schema.isSystem(), "Schema should be marked as system");
        assertEquals("System notification message", schema.getDescription());
        assertFalse(schema.isComposable());
    }

    @Test
    void testNonSystemSchemaGeneration() {
        // Test regular schema without @SchemaSystem
        AIFunctionSchema schema = SchemaUtils.getSchemaFromClass(UserInputSchema.class);
        
        assertNotNull(schema);
        assertEquals("user.input", schema.getSchemaName());
        assertFalse(schema.isSystem(), "Schema should not be marked as system");
        assertEquals("User input form", schema.getDescription());
    }

    @Test
    void testSystemSchemaWithCustomValue() {
        // Test @SchemaSystem(false)
        AIFunctionSchema schema = SchemaUtils.getSchemaFromClass(OptionalSystemSchema.class);
        
        assertNotNull(schema);
        assertFalse(schema.isSystem(), "Schema should not be system when @SchemaSystem(false)");
    }

    @Test
    void testComposableSystemSchema() {
        // Test composable schema with @SchemaSystem
        List<AIFunctionSchema> schemas = SchemaUtils.getAllSchemasFromClass(ComposableSystemSchema.class);
        
        assertNotNull(schemas);
        assertEquals(2, schemas.size(), "Should create 2 schemas for 2 fields");
        
        for (AIFunctionSchema schema : schemas) {
            assertTrue(schema.isComposable());
            // Composable schemas don't inherit the system flag from parent in current implementation
            // This could be enhanced if needed
        }
    }

    @Test
    void testSchemaRegistration() {
        // Test that schemas are properly registered
        AIFunctionSchema schema = SchemaUtils.getSchemaFromClass(SystemNotificationSchema.class);
        
        // Verify schema is registered by name
        Class<?> registeredClass = SchemaUtils.getSchemaClass("system.notification");
        assertNotNull(registeredClass);
        assertEquals(SystemNotificationSchema.class, registeredClass);
    }

    @Test
    void testCreateInstanceFromSystemSchema() {
        // Test creating instance with properties
        Map<String, String> properties = Map.of(
            "message", "System maintenance scheduled",
            "severity", "HIGH",
            "timestamp", "2024-01-15T10:00:00Z"
        );
        
        SystemNotificationSchema instance = SchemaUtils.createInstance(
            SystemNotificationSchema.class, properties
        );
        
        assertNotNull(instance);
        assertEquals("System maintenance scheduled", instance.getMessage());
        assertEquals("HIGH", instance.getSeverity());
        assertEquals("2024-01-15T10:00:00Z", instance.getTimestamp());
    }

    @Test
    void testExtractPropertiesFromSystemSchema() {
        SystemNotificationSchema notification = new SystemNotificationSchema();
        notification.setMessage("Database backup completed");
        notification.setSeverity("INFO");
        notification.setTimestamp("2024-01-15T12:30:00Z");
        
        Map<String, String> properties = SchemaUtils.extractProperties(notification);
        
        assertNotNull(properties);
        assertEquals("Database backup completed", properties.get("message"));
        assertEquals("INFO", properties.get("severity"));
        assertEquals("2024-01-15T12:30:00Z", properties.get("timestamp"));
    }

    @Test
    void testSchemaIdPriority() {
        // Test that @SchemaName takes priority over @SchemaClass id
        String schemaId = SchemaUtils.getSchemaId(PriorityTestSchema.class);
        assertEquals("priority.test.name", schemaId);
        
        // Test fallback to @SchemaClass id
        String schemaId2 = SchemaUtils.getSchemaId(SchemaClassIdTest.class);
        assertEquals("schema.class.id", schemaId2);
        
        // Test fallback to simple class name
        String schemaId3 = SchemaUtils.getSchemaId(NoAnnotationSchema.class);
        assertEquals("NoAnnotationSchema", schemaId3);
    }

    // Test schema classes
    @SchemaSystem
    @SchemaName("system.notification")
    @SchemaDescription("System notification message")
    public static class SystemNotificationSchema {
        private String message;
        private String severity;
        private String timestamp;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }

    @SchemaName("user.input")
    @SchemaDescription("User input form")
    public static class UserInputSchema {
        private String input;
        private String context;

        public String getInput() {
            return input;
        }

        public void setInput(String input) {
            this.input = input;
        }

        public String getContext() {
            return context;
        }

        public void setContext(String context) {
            this.context = context;
        }
    }

    @SchemaSystem(false)
    @SchemaName("optional.system")
    public static class OptionalSystemSchema {
        private String data;

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    @SchemaSystem
    @SchemaClass(id = "composable.system", composable = true)
    public static class ComposableSystemSchema {
        private String field1;
        private String field2;

        public String getField1() {
            return field1;
        }

        public void setField1(String field1) {
            this.field1 = field1;
        }

        public String getField2() {
            return field2;
        }

        public void setField2(String field2) {
            this.field2 = field2;
        }
    }

    @SchemaName("priority.test.name")
    @SchemaClass(id = "priority.test.class")
    public static class PriorityTestSchema {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @SchemaClass(id = "schema.class.id")
    public static class SchemaClassIdTest {
        private String data;

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    public static class NoAnnotationSchema {
        private String content;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}