package ai.driftkit.cli.commands;

import ai.driftkit.cli.utils.ProjectGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NewCommandTest {

    private ProjectGenerator projectGenerator;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        projectGenerator = new ProjectGenerator();
    }
    
    @Test
    void testCreateMavenProject() throws Exception {
        String projectName = "test-project";
        Path projectPath = tempDir.resolve(projectName);
        
        // Generate project
        projectGenerator.generateProject(projectPath, projectName, "com.example", "maven", "simple");
        
        // Verify project structure
        assertTrue(Files.exists(projectPath));
        assertTrue(Files.exists(projectPath.resolve("pom.xml")));
        assertTrue(Files.exists(projectPath.resolve("src/main/java")));
        assertTrue(Files.exists(projectPath.resolve("src/main/resources/application.yml")));
        assertTrue(Files.exists(projectPath.resolve("src/test/java")));
        assertTrue(Files.exists(projectPath.resolve(".gitignore")));
        assertTrue(Files.exists(projectPath.resolve("README.md")));
        
        // Verify pom.xml content
        String pomContent = Files.readString(projectPath.resolve("pom.xml"));
        assertTrue(pomContent.contains("<artifactId>" + projectName + "</artifactId>"));
        assertTrue(pomContent.contains("driftkit-common"));
        assertTrue(pomContent.contains("driftkit-clients-spring-boot-starter"));
    }
    
    @Test
    void testCreateGradleProject() throws Exception {
        String projectName = "test-gradle-project";
        Path projectPath = tempDir.resolve(projectName);
        
        // Generate project
        projectGenerator.generateProject(projectPath, projectName, "com.example", "gradle", "simple");
        
        // Verify project structure
        assertTrue(Files.exists(projectPath));
        assertTrue(Files.exists(projectPath.resolve("build.gradle")));
        assertTrue(Files.exists(projectPath.resolve("settings.gradle")));
        assertTrue(Files.exists(projectPath.resolve("src/main/java")));
        assertTrue(Files.exists(projectPath.resolve("src/main/resources/application.yml")));
        
        // Verify build.gradle content
        String buildContent = Files.readString(projectPath.resolve("build.gradle"));
        assertTrue(buildContent.contains("driftkit-common"));
        assertTrue(buildContent.contains("org.springframework.boot"));
        
        // Verify settings.gradle content
        String settingsContent = Files.readString(projectPath.resolve("settings.gradle"));
        assertTrue(settingsContent.contains(projectName));
    }
    
    @Test
    void testProjectFiles() throws Exception {
        String projectName = "test-files-project";
        Path projectPath = tempDir.resolve(projectName);
        
        // Generate project
        projectGenerator.generateProject(projectPath, projectName, "com.example", "maven", "simple");
        
        // Check Java files
        Path mainJavaPath = projectPath.resolve("src/main/java/com/example");
        assertTrue(Files.exists(mainJavaPath.resolve("TestFilesProjectApplication.java")));
        assertTrue(Files.exists(mainJavaPath.resolve("EchoAgent.java")));
        
        // Verify application.yml content
        String appYml = Files.readString(projectPath.resolve("src/main/resources/application.yml"));
        assertTrue(appYml.contains(projectName));
        assertTrue(appYml.contains("openai"));
    }
}