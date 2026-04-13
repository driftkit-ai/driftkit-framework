package ai.driftkit.cli.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ProjectGenerator {
    
    private final TemplateProcessor templateProcessor;
    
    public ProjectGenerator() {
        this.templateProcessor = new TemplateProcessor();
    }
    
    public void generateProject(Path projectPath, String projectName, String packageName, String buildSystem, String template) throws IOException {
        // Ensure projectPath is absolute to avoid relative path issues
        projectPath = projectPath.toAbsolutePath();
        
        // Create project directory
        Files.createDirectories(projectPath);
        
        // Create standard Maven/Gradle directory structure
        createDirectoryStructure(projectPath);
        
        // Generate build file
        if ("maven".equals(buildSystem)) {
            generateMavenProject(projectPath, projectName, packageName, template);
        } else {
            generateGradleProject(projectPath, projectName, packageName, template);
        }
        
        // Generate common files
        generateCommonFiles(projectPath, projectName, packageName, buildSystem, template);
        
        // Generate template-specific files
        generateTemplateSpecificFiles(projectPath, projectName, packageName, template);
    }
    
    private void createDirectoryStructure(Path projectPath) throws IOException {
        Files.createDirectories(projectPath.resolve("src/main/java"));
        Files.createDirectories(projectPath.resolve("src/main/resources"));
        Files.createDirectories(projectPath.resolve("src/test/java"));
        Files.createDirectories(projectPath.resolve("src/test/resources"));
    }
    
    private void generateMavenProject(Path projectPath, String projectName, String packageName, String template) throws IOException {
        Map<String, String> replacements = TemplateProcessor.createStandardReplacements(
            projectName, 
            packageName,
            toCamelCase(projectName) + "Application"
        );
        
        // Use template-specific pom.xml if exists, otherwise use default
        String pomTemplate = templateExists("maven/" + template + "/pom.xml") 
            ? "maven/" + template + "/pom.xml" 
            : "maven/pom.xml";
            
        // Generate pom.xml
        String pomContent = templateProcessor.processTemplate(pomTemplate, replacements);
        Files.write(projectPath.resolve("pom.xml"), pomContent.getBytes(StandardCharsets.UTF_8));
    }
    
    private void generateGradleProject(Path projectPath, String projectName, String packageName, String template) throws IOException {
        Map<String, String> replacements = TemplateProcessor.createStandardReplacements(
            projectName, 
            packageName,
            toCamelCase(projectName) + "Application"
        );
        
        // Use template-specific build.gradle if exists, otherwise use default
        String buildTemplate = templateExists("gradle/" + template + "/build.gradle") 
            ? "gradle/" + template + "/build.gradle" 
            : "gradle/build.gradle";
            
        // Generate build.gradle
        String buildContent = templateProcessor.processTemplate(buildTemplate, replacements);
        Files.write(projectPath.resolve("build.gradle"), buildContent.getBytes(StandardCharsets.UTF_8));
        
        // Generate settings.gradle
        String settingsContent = templateProcessor.processTemplate("gradle/settings.gradle", replacements);
        Files.write(projectPath.resolve("settings.gradle"), settingsContent.getBytes(StandardCharsets.UTF_8));
    }
    
    private void generateCommonFiles(Path projectPath, String projectName, String packageName, String buildSystem, String template) throws IOException {
        String className = toCamelCase(projectName) + "Application";
        
        Map<String, String> replacements = TemplateProcessor.createStandardReplacements(
            projectName, packageName, className
        );
        
        // Create package directory
        Path javaPackagePath = projectPath.resolve("src/main/java/" + packageName.replace('.', '/'));
        Files.createDirectories(javaPackagePath);
        
        // Generate application.yml - use template-specific config if available, otherwise use default
        String appYmlTemplate = switch (template.toLowerCase()) {
            case "chatbot" -> "chatbot/application.yml";
            case "full_stack" -> "full_stack/application.yml";
            case "rag_pipeline" -> "rag_pipeline/application.yml";
            case "spring_ai" -> "spring_ai/application.yml";
            default -> "src/application.yml";
        };
        String appYmlContent = templateProcessor.processTemplate(appYmlTemplate, replacements);
        Files.write(projectPath.resolve("src/main/resources/application.yml"), 
                   appYmlContent.getBytes(StandardCharsets.UTF_8));
        
        // Generate MainApplication.java
        String mainAppContent = templateProcessor.processTemplate("src/MainApplication.java", replacements);
        Files.write(javaPackagePath.resolve(className + ".java"), 
                   mainAppContent.getBytes(StandardCharsets.UTF_8));
        
        // Generate EchoAgent.java
        String echoAgentContent = templateProcessor.processTemplate("src/EchoAgent.java", replacements);
        Files.write(javaPackagePath.resolve("EchoAgent.java"), 
                   echoAgentContent.getBytes(StandardCharsets.UTF_8));
        
        // Generate .gitignore
        generateGitignore(projectPath, buildSystem);
        
        // Generate README.md
        generateReadme(projectPath, projectName, buildSystem);
    }
    
    private void generateGitignore(Path projectPath, String buildSystem) throws IOException {
        StringBuilder gitignore = new StringBuilder();
        gitignore.append("# IDE files\n");
        gitignore.append(".idea/\n");
        gitignore.append("*.iml\n");
        gitignore.append(".vscode/\n");
        gitignore.append("\n");
        
        gitignore.append("# Build output\n");
        gitignore.append("target/\n");
        gitignore.append("build/\n");
        gitignore.append("out/\n");
        gitignore.append("\n");
        
        if ("gradle".equals(buildSystem)) {
            gitignore.append("# Gradle\n");
            gitignore.append(".gradle/\n");
            gitignore.append("gradle/wrapper/\n");
            gitignore.append("\n");
        }
        
        gitignore.append("# Logs\n");
        gitignore.append("*.log\n");
        gitignore.append("\n");
        
        gitignore.append("# OS files\n");
        gitignore.append(".DS_Store\n");
        gitignore.append("Thumbs.db\n");
        
        Files.write(projectPath.resolve(".gitignore"), gitignore.toString().getBytes(StandardCharsets.UTF_8));
    }
    
    private void generateReadme(Path projectPath, String projectName, String buildSystem) throws IOException {
        StringBuilder readme = new StringBuilder();
        readme.append("# ").append(projectName).append("\n\n");
        readme.append("A DriftKit AI application.\n\n");
        
        readme.append("## Getting Started\n\n");
        readme.append("### Prerequisites\n\n");
        readme.append("- Java 21 or higher\n");
        readme.append("- ").append("maven".equals(buildSystem) ? "Maven 3.8+" : "Gradle 8+").append("\n\n");
        
        readme.append("### Running the Application\n\n");
        readme.append("```bash\n");
        readme.append("# Using DriftKit CLI\n");
        readme.append("driftkit dev\n\n");
        
        readme.append("# Or using ").append("maven".equals(buildSystem) ? "Maven" : "Gradle").append("\n");
        if ("maven".equals(buildSystem)) {
            readme.append("mvn spring-boot:run\n");
        } else {
            readme.append("./gradlew bootRun\n");
        }
        readme.append("```\n\n");
        
        readme.append("### Configuration\n\n");
        readme.append("Set your OpenAI API key:\n\n");
        readme.append("```bash\n");
        readme.append("export OPENAI_API_KEY=your-api-key-here\n");
        readme.append("```\n\n");
        
        readme.append("Or configure it in `src/main/resources/application.yml`.\n\n");
        
        readme.append("### Adding Dependencies\n\n");
        readme.append("Use the DriftKit CLI to add modules:\n\n");
        readme.append("```bash\n");
        readme.append("driftkit add embedding-openai\n");
        readme.append("driftkit add vector-pinecone\n");
        readme.append("```\n");
        
        Files.write(projectPath.resolve("README.md"), readme.toString().getBytes(StandardCharsets.UTF_8));
    }
    
    private String toCamelCase(String input) {
        String[] parts = input.split("[-_]");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase());
                }
            }
        }
        return result.toString();
    }
    
    private String toPackageName(String input) {
        return input.toLowerCase().replaceAll("[-_]", "");
    }
    
    private boolean templateExists(String templatePath) {
        return getClass().getResourceAsStream("/templates/" + templatePath) != null;
    }
    
    private void generateTemplateSpecificFiles(Path projectPath, String projectName, String packageName, String template) throws IOException {
        String className = toCamelCase(projectName) + "Application";
        
        Map<String, String> replacements = TemplateProcessor.createStandardReplacements(
            projectName, packageName, className
        );
        
        // Generate template-specific files based on the template type
        switch (template.toLowerCase()) {
            case "full_stack":
                generateFullStackTemplate(projectPath, packageName, replacements);
                break;
            case "chatbot":
                generateChatbotTemplate(projectPath, packageName, replacements);
                break;
            case "rag_pipeline":
                generateRAGTemplate(projectPath, packageName, replacements);
                break;
            case "spring_ai":
                generateSpringAITemplate(projectPath, packageName, replacements);
                break;
            // simple template doesn't need additional files
        }
    }
    
    private void generateFullStackTemplate(Path projectPath, String packageName, Map<String, String> replacements) throws IOException {
        // Create additional directories
        Path javaPackagePath = projectPath.resolve("src/main/java/" + packageName.replace('.', '/'));
        Files.createDirectories(javaPackagePath.resolve("config"));
        Files.createDirectories(javaPackagePath.resolve("controller"));
        Files.createDirectories(javaPackagePath.resolve("workflow"));
        Files.createDirectories(javaPackagePath.resolve("service"));
        Files.createDirectories(projectPath.resolve("src/main/resources/prompts/examples"));
        
        // Generate configuration files
        generateFileFromTemplate("full_stack/MongoConfig.java", 
            javaPackagePath.resolve("config/MongoConfig.java"), replacements);
        generateFileFromTemplate("full_stack/DriftKitConfig.java", 
            javaPackagePath.resolve("config/DriftKitConfig.java"), replacements);
        
        // Generate controllers
        generateFileFromTemplate("full_stack/ChatController.java", 
            javaPackagePath.resolve("controller/ChatController.java"), replacements);
        generateFileFromTemplate("full_stack/PromptController.java", 
            javaPackagePath.resolve("controller/PromptController.java"), replacements);
        
        // Generate workflows
        generateFileFromTemplate("full_stack/ChatWorkflow.java", 
            javaPackagePath.resolve("workflow/ChatWorkflow.java"), replacements);
        
        // Generate docker-compose
        generateFileFromTemplate("full_stack/docker-compose.yml", 
            projectPath.resolve("docker-compose.yml"), replacements);
    }
    
    private void generateChatbotTemplate(Path projectPath, String packageName, Map<String, String> replacements) throws IOException {
        // Create additional directories
        Path javaPackagePath = projectPath.resolve("src/main/java/" + packageName.replace('.', '/'));
        Files.createDirectories(javaPackagePath.resolve("config"));
        Files.createDirectories(javaPackagePath.resolve("workflow"));
        Files.createDirectories(projectPath.resolve("src/main/resources/static"));
        
        // Generate files
        generateFileFromTemplate("chatbot/OnboardingWorkflow.java", 
            javaPackagePath.resolve("workflow/OnboardingWorkflow.java"), replacements);
        generateFileFromTemplate("chatbot/ResourceConfig.java", 
            javaPackagePath.resolve("config/ResourceConfig.java"), replacements);
        
        // Generate frontend files
        generateFileFromTemplate("chatbot/index.html", 
            projectPath.resolve("src/main/resources/static/index.html"), replacements);
        generateFileFromTemplate("chatbot/chat.js", 
            projectPath.resolve("src/main/resources/static/chat.js"), replacements);
        generateFileFromTemplate("chatbot/chat.css", 
            projectPath.resolve("src/main/resources/static/chat.css"), replacements);
    }
    
    private void generateRAGTemplate(Path projectPath, String packageName, Map<String, String> replacements) throws IOException {
        // Create additional directories
        Path javaPackagePath = projectPath.resolve("src/main/java/" + packageName.replace('.', '/'));
        Files.createDirectories(javaPackagePath.resolve("config"));
        Files.createDirectories(javaPackagePath.resolve("controller"));
        Files.createDirectories(javaPackagePath.resolve("service"));
        
        // Generate files
        generateFileFromTemplate("rag_pipeline/RAGConfig.java", 
            javaPackagePath.resolve("config/RAGConfig.java"), replacements);
        generateFileFromTemplate("rag_pipeline/DocumentController.java", 
            javaPackagePath.resolve("controller/DocumentController.java"), replacements);
        generateFileFromTemplate("rag_pipeline/IngestionService.java", 
            javaPackagePath.resolve("service/IngestionService.java"), replacements);
        generateFileFromTemplate("rag_pipeline/SearchService.java", 
            javaPackagePath.resolve("service/SearchService.java"), replacements);
        
        // Generate docker-compose
        generateFileFromTemplate("rag_pipeline/docker-compose.yml", 
            projectPath.resolve("docker-compose.yml"), replacements);
    }
    
    private void generateSpringAITemplate(Path projectPath, String packageName, Map<String, String> replacements) throws IOException {
        // Create additional directories
        Path javaPackagePath = projectPath.resolve("src/main/java/" + packageName.replace('.', '/'));
        Files.createDirectories(javaPackagePath.resolve("config"));
        Files.createDirectories(javaPackagePath.resolve("controller"));
        Files.createDirectories(javaPackagePath.resolve("service"));
        
        // Generate files
        generateFileFromTemplate("spring_ai/SpringAIConfig.java", 
            javaPackagePath.resolve("config/SpringAIConfig.java"), replacements);
        generateFileFromTemplate("spring_ai/AIController.java", 
            javaPackagePath.resolve("controller/AIController.java"), replacements);
        generateFileFromTemplate("spring_ai/AIService.java", 
            javaPackagePath.resolve("service/AIService.java"), replacements);
    }
    
    private void generateFileFromTemplate(String templatePath, Path targetPath, Map<String, String> replacements) throws IOException {
        // Ensure targetPath is absolute
        targetPath = targetPath.toAbsolutePath();
        
        String content = templateProcessor.processTemplate(templatePath, replacements);
        // Ensure parent directories exist
        Files.createDirectories(targetPath.getParent());
        Files.write(targetPath, content.getBytes(StandardCharsets.UTF_8));
    }
}