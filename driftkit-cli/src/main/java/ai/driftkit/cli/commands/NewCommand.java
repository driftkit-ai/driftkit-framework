package ai.driftkit.cli.commands;

import ai.driftkit.cli.converters.BuildSystemConverter;
import ai.driftkit.cli.converters.TemplateConverter;
import ai.driftkit.cli.utils.ProjectGenerator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@Command(
    name = "new",
    header = "Create a new DriftKit project",
    description = {
        "Creates a new DriftKit AI project with the specified template and configuration.",
        "Choose from various templates optimized for different AI use cases."
    },
    footerHeading = "%nExamples:%n",
    footer = {
        "  driftkit new my-app",
        "  driftkit new my-rag --template rag_pipeline --package com.mycompany.ai",
        "  driftkit new chatbot --template chatbot --build gradle",
        "",
        "Available templates:",
        "  simple       - Basic AI agent project",
        "  full_stack   - Full-stack with Context Engineering UI",
        "  chatbot      - Chat bot with human-in-loop",
        "  rag_pipeline - RAG pipeline with document ingestion",
        "  spring_ai    - Spring AI integration"
    }
)
public class NewCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Project name (will be used as directory name)")
    private String projectName;

    @Option(names = {"--build"}, 
        description = "Build system to use: maven, gradle (default: maven)", 
        defaultValue = "MAVEN",
        converter = BuildSystemConverter.class)
    private BuildSystem buildSystem = BuildSystem.MAVEN;

    @Option(names = {"--template"}, 
        description = "Project template to use. See footer for available templates (default: ${DEFAULT-VALUE})",
        defaultValue = "simple",
        paramLabel = "<template>",
        converter = TemplateConverter.class)
    private ProjectTemplate template = ProjectTemplate.SIMPLE;

    @Option(names = {"--package"}, 
        description = "Java package name for the project (default: ${DEFAULT-VALUE})",
        defaultValue = "com.example",
        paramLabel = "<package>")
    private String packageName;

    public enum BuildSystem {
        MAVEN, GRADLE
    }
    
    public enum ProjectTemplate {
        SIMPLE("Simple project with basic agent"),
        FULL_STACK("Full-stack with context engineering UI"),
        CHATBOT("Chat bot with human-in-loop"),
        RAG_PIPELINE("RAG pipeline with document ingestion"),
        SPRING_AI("Spring AI integration");
        
        private final String description;
        
        ProjectTemplate(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }

    @Override
    public Integer call() throws Exception {
        System.out.println("Creating new DriftKit project: " + projectName);
        
        Path projectPath = Paths.get(projectName).toAbsolutePath();
        ProjectGenerator generator = new ProjectGenerator();
        
        try {
            generator.generateProject(projectPath, projectName, packageName, buildSystem.toString().toLowerCase(), template.name().toLowerCase());
            
            System.out.println("\nProject created successfully!");
            System.out.println("Template: " + template.getDescription());
            System.out.println("\nTo get started:");
            System.out.println("  cd " + projectName);
            
            // Template-specific instructions
            switch (template) {
                case FULL_STACK:
                    System.out.println("  docker-compose up -d  # Start MongoDB");
                    System.out.println("  driftkit dev");
                    System.out.println("\nContext Engineering UI will be available at http://localhost:8080/context-engineering");
                    break;
                case CHATBOT:
                    System.out.println("  driftkit dev");
                    System.out.println("\nChat interface will be available at http://localhost:8080");
                    break;
                case RAG_PIPELINE:
                    System.out.println("  docker-compose up -d  # Start MongoDB");
                    System.out.println("  driftkit dev");
                    System.out.println("\nUpload documents via POST /api/documents/upload");
                    break;
                case SPRING_AI:
                    System.out.println("  export OPENAI_API_KEY=your-key-here");
                    System.out.println("  driftkit dev");
                    break;
                default:
                    System.out.println("  driftkit dev");
            }
            
            return 0;
        } catch (Exception e) {
            System.err.println("Error creating project: " + e.getMessage());
            if (System.getProperty("verbose") != null) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}