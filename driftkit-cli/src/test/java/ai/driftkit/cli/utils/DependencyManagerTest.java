package ai.driftkit.cli.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DependencyManagerTest {

    private DependencyManager dependencyManager;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        dependencyManager = new DependencyManager();
    }
    
    @Test
    void testAddMavenDependency() throws IOException {
        // Create a sample pom.xml
        String pomContent = """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>test-project</artifactId>
    <version>1.0.0</version>
    
    <properties>
        <driftkit.version>0.6.0</driftkit.version>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
    </dependencies>
</project>
""";
        
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent, StandardCharsets.UTF_8);
        
        // Add dependency
        boolean result = dependencyManager.addDependency(tempDir, "driftkit-vector-pinecone");
        assertTrue(result);
        
        // Verify dependency was added
        String updatedPom = Files.readString(pomPath, StandardCharsets.UTF_8);
        assertTrue(updatedPom.contains("driftkit-vector-pinecone"));
        assertTrue(updatedPom.contains("ai.driftkit"));
    }
    
    @Test
    void testAddGradleDependency() throws IOException {
        // Create a sample build.gradle
        String buildContent = """
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.1'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
}
""";
        
        Path buildPath = tempDir.resolve("build.gradle");
        Files.writeString(buildPath, buildContent, StandardCharsets.UTF_8);
        
        // Add dependency
        boolean result = dependencyManager.addDependency(tempDir, "driftkit-embedding-openai");
        assertTrue(result);
        
        // Verify dependency was added
        String updatedBuild = Files.readString(buildPath, StandardCharsets.UTF_8);
        assertTrue(updatedBuild.contains("driftkit-embedding-openai"));
        assertTrue(updatedBuild.contains("ai.driftkit"));
        assertTrue(updatedBuild.contains("driftkitVersion"));
    }
    
    @Test
    void testAddDuplicateDependency() throws IOException {
        // Create pom.xml with existing driftkit dependency
        String pomContent = """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>test-project</artifactId>
    <version>1.0.0</version>
    
    <dependencies>
        <dependency>
            <groupId>ai.driftkit</groupId>
            <artifactId>driftkit-common</artifactId>
            <version>${driftkit.version}</version>
        </dependency>
    </dependencies>
</project>
""";
        
        Path pomPath = tempDir.resolve("pom.xml");
        Files.writeString(pomPath, pomContent, StandardCharsets.UTF_8);
        
        // Try to add same dependency
        boolean result = dependencyManager.addDependency(tempDir, "driftkit-common");
        assertTrue(result); // Should return true but not duplicate
        
        // Verify no duplicate
        String updatedPom = Files.readString(pomPath, StandardCharsets.UTF_8);
        int count = updatedPom.split("driftkit-common").length - 1;
        assertEquals(1, count); // Should appear only once
    }
    
    @Test
    void testInvalidProjectDirectory() throws IOException {
        // Directory with no build files
        boolean result = dependencyManager.addDependency(tempDir, "driftkit-common");
        assertFalse(result);
    }
}