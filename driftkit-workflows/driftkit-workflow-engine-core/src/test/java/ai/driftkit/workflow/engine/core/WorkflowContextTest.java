package ai.driftkit.workflow.engine.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowContextTest {
    
    private WorkflowContext context;
    
    @BeforeEach
    void setUp() {
        context = WorkflowContext.newRun("initial-input");
    }
    
    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {
        
        @Test
        @DisplayName("Should create context with trigger data")
        void testCreateWithTriggerData() {
            String triggerData = "test-trigger";
            WorkflowContext ctx = WorkflowContext.newRun(triggerData);
            
            assertNotNull(ctx.getRunId());
            assertEquals(triggerData, ctx.getTriggerData());
            assertEquals(triggerData, ctx.getTriggerData(String.class));
            assertEquals(0, ctx.getStepCount());
            assertEquals(0, ctx.getCustomDataCount());
        }
        
        @Test
        @DisplayName("Should create context with instance ID")
        void testCreateWithInstanceId() {
            String triggerData = "test-trigger";
            String instanceId = "instance-123";
            WorkflowContext ctx = WorkflowContext.newRun(triggerData, instanceId);
            
            assertEquals(instanceId, ctx.getInstanceId());
            assertNotEquals(instanceId, ctx.getRunId());
        }
        
        @Test
        @DisplayName("Should handle null trigger data")
        void testNullTriggerData() {
            WorkflowContext ctx = WorkflowContext.newRun(null);
            
            assertNull(ctx.getTriggerData());
            assertNull(ctx.getTriggerData(String.class));
        }
    }
    
    @Nested
    @DisplayName("Step Output Operations")
    class StepOutputOperations {
        
        @Test
        @DisplayName("Should store and retrieve step output")
        void testStepOutput() {
            String stepId = "step1";
            String output = "step-result";
            
            context.setStepOutput(stepId, output);
            
            assertTrue(context.hasStepResult(stepId));
            assertEquals(output, context.getStepResult(stepId, String.class));
            assertEquals(1, context.getStepCount());
        }
        
        @Test
        @DisplayName("Should overwrite existing step output")
        void testOverwriteStepOutput() {
            String stepId = "step1";
            
            context.setStepOutput(stepId, "first");
            context.setStepOutput(stepId, "second");
            
            assertEquals("second", context.getStepResult(stepId, String.class));
            assertEquals(1, context.getStepCount());
        }
        
        @Test
        @DisplayName("Should remove step output when setting null")
        void testRemoveStepOutput() {
            String stepId = "step1";
            
            context.setStepOutput(stepId, "value");
            assertTrue(context.hasStepResult(stepId));
            
            context.setStepOutput(stepId, null);
            assertFalse(context.hasStepResult(stepId));
        }
        
        @Test
        @DisplayName("Should throw when step output not found")
        void testStepOutputNotFound() {
            assertThrows(NoSuchElementException.class,
                () -> context.getStepResult("unknown", String.class));
        }
        
        @Test
        @DisplayName("Should return default when step output not found")
        void testStepOutputWithDefault() {
            String defaultValue = "default";
            String result = context.getStepResultOrDefault("unknown", String.class, defaultValue);
            assertEquals(defaultValue, result);
        }
        
        @Test
        @DisplayName("Should handle type conversion for step outputs")
        void testStepOutputTypeConversion() {
            context.setStepOutput("number", 42);
            
            assertEquals(42, context.getStepResult("number", Integer.class));
            assertEquals(42, context.getStepResult("number", Number.class));
            
            assertThrows(ClassCastException.class,
                () -> context.getStepResult("number", String.class));
        }
    }
    
    @Nested
    @DisplayName("Custom Data Operations")
    class CustomDataOperations {
        
        @Test
        @DisplayName("Should store and retrieve custom data")
        void testCustomData() {
            String key = "user-preference";
            String value = "dark-mode";
            
            context.setContextValue(key, value);
            
            assertEquals(value, context.getContextValue(key, String.class));
            assertEquals(value, context.getString(key));
            assertEquals(1, context.getCustomDataCount());
        }
        
        @Test
        @DisplayName("Should handle different types in custom data")
        void testCustomDataTypes() {
            context.setContextValue("string", "text");
            context.setContextValue("integer", 42);
            context.setContextValue("double", 3.14);
            context.setContextValue("boolean", true);
            context.setContextValue("long", 123L);
            
            assertEquals("text", context.getString("string"));
            assertEquals(42, context.getInt("integer"));
            assertEquals(3.14, context.getDouble("double"));
            assertEquals(true, context.getBoolean("boolean"));
            assertEquals(123L, context.getLong("long"));
        }
        
        @Test
        @DisplayName("Should handle collections in custom data")
        void testCustomDataCollections() {
            List<String> list = List.of("a", "b", "c");
            Map<String, Integer> map = Map.of("x", 1, "y", 2);
            
            context.setContextValue("list", list);
            context.setContextValue("map", map);
            
            assertEquals(list, context.getList("list", String.class));
            assertEquals(map, context.getMap("map", String.class, Integer.class));
        }
        
        @Test
        @DisplayName("Should return null for non-existent custom data")
        void testCustomDataNotFound() {
            assertNull(context.getContextValue("unknown", String.class));
            assertNull(context.getString("unknown"));
        }
        
        @Test
        @DisplayName("Should return default for non-existent custom data")
        void testCustomDataWithDefaults() {
            assertEquals("default", context.getStringOrDefault("unknown", "default"));
            assertEquals(42, context.getIntOrDefault("unknown", 42));
            assertEquals(3.14, context.getDoubleOrDefault("unknown", 3.14));
            assertEquals(true, context.getBooleanOrDefault("unknown", true));
            assertEquals(123L, context.getLongOrDefault("unknown", 123L));
        }
    }
    
    @Nested
    @DisplayName("Fluent API Tests")
    class FluentApiTests {
        
        @Test
        @DisplayName("Should access step output fluently")
        void testFluentStepAccess() {
            context.setStepOutput("step1", "value1");
            
            assertTrue(context.step("step1").exists());
            assertTrue(context.step("step1").succeeded());
            assertEquals("value1", context.step("step1").output(String.class).orElse(null));
            assertEquals("value1", context.step("step1").outputOrThrow(String.class));
        }
        
        @Test
        @DisplayName("Should handle missing step fluently")
        void testFluentMissingStep() {
            assertFalse(context.step("unknown").exists());
            assertFalse(context.step("unknown").succeeded());
            assertTrue(context.step("unknown").output(String.class).isEmpty());
            
            assertThrows(NoSuchElementException.class,
                () -> context.step("unknown").outputOrThrow(String.class));
        }
        
        @Test
        @DisplayName("Should track last output")
        void testLastOutput() {
            assertTrue(context.lastOutput(String.class).isEmpty());
            
            context.setStepOutput("step1", "first");
            assertEquals("first", context.lastOutput(String.class).orElse(null));
            
            context.setStepOutput("step2", "second");
            assertEquals("second", context.lastOutput(String.class).orElse(null));
            
            // System keys shouldn't update last output
            context.setStepOutput("__system__", "system");
            assertEquals("second", context.lastOutput(String.class).orElse(null));
        }
    }
    
    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {
        
        @Test
        @DisplayName("Should handle concurrent step outputs")
        void testConcurrentStepOutputs() throws InterruptedException {
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];
            
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    String stepId = "step" + index;
                    context.setStepOutput(stepId, "value" + index);
                });
            }
            
            for (Thread thread : threads) {
                thread.start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            assertEquals(threadCount, context.getStepCount());
            
            for (int i = 0; i < threadCount; i++) {
                String stepId = "step" + i;
                assertEquals("value" + i, context.getStepResult(stepId, String.class));
            }
        }
    }
}