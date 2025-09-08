package ai.driftkit.workflow.engine.schema;

import org.junit.jupiter.api.Test;

public class SchemaUtilsDebugTest {
    
    @Test
    public void debugSchemaName() {
        System.out.println("=== Debug Schema Name Processing ===");
        
        // Test with SystemNotificationSchema
        Class<?> testClass = SchemaUtilsTest.SystemNotificationSchema.class;
        System.out.println("Test class: " + testClass.getName());
        
        // Clear all caches first
        System.out.println("\n--- Clearing all caches ---");
        SchemaUtils.clearCache();
        AIFunctionSchema.clearCache();
        
        // Check annotations
        SchemaName nameAnnotation = testClass.getAnnotation(SchemaName.class);
        System.out.println("@SchemaName present: " + (nameAnnotation != null));
        if (nameAnnotation != null) {
            System.out.println("@SchemaName value: " + nameAnnotation.value());
        }
        
        SchemaSystem systemAnnotation = testClass.getAnnotation(SchemaSystem.class);
        System.out.println("@SchemaSystem present: " + (systemAnnotation != null));
        if (systemAnnotation != null) {
            System.out.println("@SchemaSystem value: " + systemAnnotation.value());
        }
        
        // Get schema using AIFunctionSchema directly
        System.out.println("\n--- Using AIFunctionSchema.fromClass() ---");
        AIFunctionSchema directSchema = AIFunctionSchema.fromClass(testClass);
        System.out.println("Schema name: " + directSchema.getSchemaName());
        System.out.println("Schema system: " + directSchema.isSystem());
        
        // Check if StringUtils is working
        System.out.println("\n--- Checking StringUtils ---");
        String testValue = "test";
        System.out.println("StringUtils.isNotBlank('test'): " + org.apache.commons.lang3.StringUtils.isNotBlank(testValue));
        System.out.println("nameAnnotation.value() is blank: " + org.apache.commons.lang3.StringUtils.isBlank(nameAnnotation.value()));
        
        // Get schema using SchemaUtils
        System.out.println("\n--- Using SchemaUtils.getSchemaFromClass() ---");
        AIFunctionSchema utilsSchema = SchemaUtils.getSchemaFromClass(testClass);
        System.out.println("Schema name: " + utilsSchema.getSchemaName());
        System.out.println("Schema system: " + utilsSchema.isSystem());
        
        // Check if they're the same instance (cached)
        System.out.println("\nSame instance: " + (directSchema == utilsSchema));
        
        // Test clearing cache and getting schema again
        System.out.println("\n--- After clearing cache ---");
        SchemaUtils.clearCache();
        AIFunctionSchema freshSchema = SchemaUtils.getSchemaFromClass(testClass);
        System.out.println("Schema name: " + freshSchema.getSchemaName());
        System.out.println("Schema system: " + freshSchema.isSystem());
        
        // Test manual setSchemaName
        System.out.println("\n--- Testing setSchemaName ---");
        AIFunctionSchema testSchema = new AIFunctionSchema();
        System.out.println("Initial schema name: " + testSchema.getSchemaName());
        testSchema.setSchemaName("test.name");
        System.out.println("After setSchemaName: " + testSchema.getSchemaName());
        
        // Test StringUtils.isNotBlank with annotation value
        System.out.println("\n--- Testing StringUtils with annotation ---");
        System.out.println("Annotation value: '" + nameAnnotation.value() + "'");
        System.out.println("Annotation value length: " + nameAnnotation.value().length());
        System.out.println("isNotBlank result: " + org.apache.commons.lang3.StringUtils.isNotBlank(nameAnnotation.value()));
        
        System.out.println("=== End Debug ===");
    }
}