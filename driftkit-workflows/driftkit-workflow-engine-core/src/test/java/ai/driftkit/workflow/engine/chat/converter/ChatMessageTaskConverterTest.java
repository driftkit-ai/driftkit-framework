package ai.driftkit.workflow.engine.chat.converter;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.chat.ChatMessage.DataProperty;
import ai.driftkit.common.domain.chat.ChatMessage.MessageType;
import ai.driftkit.common.domain.chat.ChatRequest;
import ai.driftkit.common.domain.chat.ChatResponse;
import ai.driftkit.common.domain.chat.ChatResponse.NextSchema;
import ai.driftkit.workflow.engine.chat.ChatMessageTask;
import ai.driftkit.workflow.engine.chat.ChatResponseExtensions;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import ai.driftkit.workflow.engine.schema.SchemaName;
import ai.driftkit.workflow.engine.schema.SchemaSystem;
import ai.driftkit.workflow.engine.schema.SchemaUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatMessageTaskConverterTest {

    @BeforeEach
    void setUp() {
        // Clear schema cache before each test
        SchemaUtils.clearCache();
    }

    @Test
    void testConvertResponseWithSystemSchema() {
        // Register test schema with @SchemaSystem
        AIFunctionSchema systemSchema = SchemaUtils.getSchemaFromClass(SystemTestSchema.class);
        assertNotNull(systemSchema);
        assertTrue(systemSchema.isSystem(), "Schema should be marked as system");

        // Create ChatResponse with NextSchema
        ChatResponse response = new ChatResponse(
                UUID.randomUUID().toString(),
                "chat-123",
                "workflow-1",
                Language.ENGLISH,
                false,
                50,
                "user-123",
                Map.of("message", "Please provide system information")
        );

        // Set next schema using the extension method
        ChatResponseExtensions.setNextSchemaAsSchema(response, systemSchema);

        // Convert to ChatMessageTask
        List<ChatMessageTask> tasks = ChatMessageTaskConverter.convert(response);
        assertEquals(1, tasks.size());

        ChatMessageTask task = tasks.get(0);
        assertNotNull(task);
        assertTrue(task.getSystem(), "ChatMessageTask should have system=true");
        assertEquals("SystemTestSchema", response.getNextSchema().getSchemaName());
    }

    @Test
    void testConvertResponseWithNonSystemSchema() {
        // Register test schema without @SchemaSystem
        AIFunctionSchema regularSchema = SchemaUtils.getSchemaFromClass(RegularTestSchema.class);
        assertNotNull(regularSchema);
        assertFalse(regularSchema.isSystem(), "Schema should not be marked as system");

        // Create ChatResponse with NextSchema
        ChatResponse response = new ChatResponse(
                UUID.randomUUID().toString(),
                "chat-456",
                "workflow-2",
                Language.ENGLISH,
                true,
                100,
                "user-456",
                Map.of("result", "Operation completed")
        );

        // Set next schema
        ChatResponseExtensions.setNextSchemaAsSchema(response, regularSchema);

        // Convert to ChatMessageTask
        List<ChatMessageTask> tasks = ChatMessageTaskConverter.convert(response);
        assertEquals(1, tasks.size());

        ChatMessageTask task = tasks.get(0);
        assertNotNull(task);
        assertNull(task.getSystem(), "ChatMessageTask should have system=null (default false)");
    }

    @Test
    void testConvertResponseWithoutNextSchema() {
        // Create ChatResponse without NextSchema
        ChatResponse response = new ChatResponse(
                UUID.randomUUID().toString(),
                "chat-789",
                "workflow-3",
                Language.ENGLISH,
                true,
                100,
                "user-789",
                Map.of("status", "No further action needed")
        );

        // Convert to ChatMessageTask
        List<ChatMessageTask> tasks = ChatMessageTaskConverter.convert(response);
        assertEquals(1, tasks.size());

        ChatMessageTask task = tasks.get(0);
        assertNotNull(task);
        assertNull(task.getSystem(), "ChatMessageTask should have system=null when no schema");
        assertNull(task.getNextSchema());
    }

    @Test
    void testConvertRequestWithComposableSystemSchema() {
        // Create a composable ChatRequest
        ChatRequest request = new ChatRequest();
        request.setId(UUID.randomUUID().toString());
        request.setChatId("chat-comp-123");
        request.setType(MessageType.USER);
        request.setTimestamp(System.currentTimeMillis());
        request.setComposable(true);
        request.setRequestSchemaName("SystemTestSchema");
        
        DataProperty prop1 = new DataProperty();
        prop1.setNameId("field1");
        prop1.setName("Field 1");
        prop1.setValue("Value 1");
        
        DataProperty prop2 = new DataProperty();
        prop2.setNameId("field2");
        prop2.setName("Field 2");
        prop2.setValue("Value 2");
        
        request.setProperties(List.of(prop1, prop2));

        // Convert to ChatMessageTask
        List<ChatMessageTask> tasks = ChatMessageTaskConverter.convert(request);
        
        // Composable request creates 2 tasks per property (AI + USER)
        assertEquals(4, tasks.size());
        
        // Check that all tasks are created properly
        for (int i = 0; i < tasks.size(); i += 2) {
            ChatMessageTask aiTask = tasks.get(i);
            ChatMessageTask userTask = tasks.get(i + 1);
            
            assertEquals(MessageType.AI, aiTask.getType());
            assertEquals(MessageType.USER, userTask.getType());
            assertTrue(aiTask.getRequired());
        }
    }

    @Test
    void testSystemSchemaAnnotation() {
        // Test that @SchemaSystem annotation is properly processed
        Class<?> systemClass = SystemTestSchema.class;
        SchemaSystem annotation = systemClass.getAnnotation(SchemaSystem.class);
        assertNotNull(annotation, "SystemTestSchema should have @SchemaSystem annotation");
        assertTrue(annotation.value(), "@SchemaSystem should have value=true by default");

        // Verify SchemaUtils processes it correctly
        AIFunctionSchema schema = SchemaUtils.getSchemaFromClass(systemClass);
        assertTrue(schema.isSystem(), "Schema should be marked as system");
    }

    @Test
    void testSystemSchemaWithCustomValue() {
        // Test schema with @SchemaSystem(false)
        Class<?> customSystemClass = CustomSystemTestSchema.class;
        SchemaSystem annotation = customSystemClass.getAnnotation(SchemaSystem.class);
        assertNotNull(annotation);
        assertFalse(annotation.value(), "@SchemaSystem should have value=false");

        AIFunctionSchema schema = SchemaUtils.getSchemaFromClass(customSystemClass);
        assertFalse(schema.isSystem(), "Schema should not be marked as system when value=false");
    }

    // Test schema classes
    @SchemaSystem
    @SchemaName("SystemTestSchema")
    public static class SystemTestSchema {
        private String systemMessage;
        private String priority;

        public String getSystemMessage() {
            return systemMessage;
        }

        public void setSystemMessage(String systemMessage) {
            this.systemMessage = systemMessage;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }
    }

    @SchemaName("RegularTestSchema")
    public static class RegularTestSchema {
        private String userInput;
        private String action;

        public String getUserInput() {
            return userInput;
        }

        public void setUserInput(String userInput) {
            this.userInput = userInput;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }
    }

    @SchemaSystem(false)
    @SchemaName("CustomSystemTestSchema")
    public static class CustomSystemTestSchema {
        private String data;

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }
}