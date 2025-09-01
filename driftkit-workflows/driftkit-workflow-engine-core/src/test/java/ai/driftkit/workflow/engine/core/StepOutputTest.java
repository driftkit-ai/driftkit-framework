package ai.driftkit.workflow.engine.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

class StepOutputTest {
    
    // Empty command class for testing
    static class EmptyCommand {
        // No fields - similar to StartLearningCommand
    }
    
    @Nested
    @DisplayName("Type Safety and Casting")
    class TypeSafetyTests {
        
        @Test
        @DisplayName("Should safely cast compatible types in inheritance hierarchy")
        void testPolymorphicCasting() {
            // Arrange - Create output with subtype
            Integer value = 42;
            StepOutput output = StepOutput.of(value);
            
            // Act & Assert - Should cast to supertype
            assertEquals(value, output.getValueAs(Number.class));
            assertEquals(value, output.getValueAs(Object.class));
            assertEquals(Integer.class, output.getActualClass());
            
            // Verify actual instance is preserved
            Number num = output.getValueAs(Number.class);
            assertInstanceOf(Integer.class, num);
        }
        
        @Test
        @DisplayName("Should fail fast on incompatible type casting")
        void testIncompatibleTypeCasting() {
            // Arrange - Create output with specific type
            StepOutput output = StepOutput.of("string-value");
            
            // Act & Assert - Should throw for incompatible cast
            ClassCastException exception = assertThrows(ClassCastException.class, 
                () -> output.getValueAs(Integer.class));
            
            // Verify error message is helpful
            assertTrue(exception.getMessage().contains("String") || 
                      exception.getMessage().contains("Integer"));
        }
        
        @Test
        @DisplayName("Should handle null values in type-safe manner")
        void testNullHandling() {
            // Arrange - Create output with null
            StepOutput output = StepOutput.of(null);
            
            // Act & Assert - Null checks
            assertFalse(output.hasValue());
            assertNull(output.getValue());
            assertNull(output.getActualClass());
            
            // Should return null for any type request
            assertNull(output.getValueAs(String.class));
            assertNull(output.getValueAs(Integer.class));
            assertNull(output.getValueAs(Object.class));
        }
    }
    
    @Nested
    @DisplayName("Empty Bean Handling")
    class EmptyBeanTests {
        
        @Test
        @DisplayName("Should handle empty command objects without fields")
        void testEmptyCommandSerialization() {
            // Arrange - Create empty command object (like StartLearningCommand)
            EmptyCommand command = new EmptyCommand();
            
            // Act - Should not throw exception when creating StepOutput
            StepOutput output = assertDoesNotThrow(() -> StepOutput.of(command));
            
            // Assert - Should preserve the empty object
            assertNotNull(output);
            assertTrue(output.hasValue());
            assertEquals(EmptyCommand.class.getName(), output.getClassName());
            
            // Should be able to retrieve the object
            Object retrieved = output.getValue();
            assertNotNull(retrieved);
            assertInstanceOf(EmptyCommand.class, retrieved);
            
            // Should be able to cast properly
            EmptyCommand retrievedCommand = output.getValueAs(EmptyCommand.class);
            assertNotNull(retrievedCommand);
        }
    }
    
    @Nested
    @DisplayName("Complex Object Handling")
    class ComplexObjectTests {
        
        record UserData(String name, int age, boolean active) {}
        record NestedData(UserData user, String metadata) {}
        
        @Test
        @DisplayName("Should preserve complex object integrity")
        void testComplexObjectPreservation() {
            // Arrange - Create nested complex object
            UserData user = new UserData("John", 30, true);
            NestedData nested = new NestedData(user, "test-metadata");
            
            // Act - Wrap in StepOutput
            StepOutput output = StepOutput.of(nested);
            
            // Assert - Verify object integrity
            assertTrue(output.hasValue());
            NestedData retrieved = output.getValueAs(NestedData.class);
            assertEquals(nested, retrieved);
            assertEquals("John", retrieved.user().name());
            assertEquals(30, retrieved.user().age());
            assertTrue(retrieved.user().active());
            assertEquals("test-metadata", retrieved.metadata());
        }
        
        @Test
        @DisplayName("Should handle generic collections")
        void testGenericCollections() {
            // Arrange - Create list of complex objects
            var users = java.util.List.of(
                new UserData("Alice", 25, true),
                new UserData("Bob", 35, false)
            );
            
            // Act - Wrap in StepOutput
            StepOutput output = StepOutput.of(users);
            
            // Assert - Verify collection handling
            assertTrue(output.hasValue());
            var retrieved = output.getValueAs(java.util.List.class);
            assertEquals(2, retrieved.size());
            
            // Type information is preserved at runtime
            assertInstanceOf(UserData.class, retrieved.get(0));
            assertEquals("Alice", ((UserData)retrieved.get(0)).name());
        }
    }
}