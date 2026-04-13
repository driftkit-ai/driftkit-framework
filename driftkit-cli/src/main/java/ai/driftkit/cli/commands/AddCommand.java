package ai.driftkit.cli.commands;

import ai.driftkit.cli.utils.DependencyManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@Command(
    name = "add",
    header = "Add DriftKit module dependencies to your project",
    description = {
        "Adds DriftKit modules or extensions to your existing project.",
        "Automatically updates your pom.xml or build.gradle with the correct dependencies.",
        "Modules are organized by category: vector stores, embeddings, AI clients, etc."
    },
    footerHeading = "%nExamples:%n",
    footer = {
        "  driftkit add vector-pinecone         # Add Pinecone vector store",
        "  driftkit add embedding-openai        # Add OpenAI embeddings",
        "  driftkit add clients-claude          # Add Claude AI client",
        "  driftkit add audio                   # Add audio transcription",
        "",
        "Available modules:",
        "  Vector Stores:",
        "    vector-pinecone, vector-spring-ai",
        "  Embeddings:",
        "    embedding-openai, embedding-cohere, embedding-spring-ai",
        "  AI Clients:",
        "    clients-openai, clients-claude, clients-gemini, clients-spring-ai",
        "  Other:",
        "    context-engineering, audio, chat-assistant, rag"
    }
)
public class AddCommand implements Callable<Integer> {

    @Parameters(index = "0", 
        description = "Module name to add (see footer for available modules)",
        paramLabel = "<module>")
    private String moduleName;

    @Option(names = {"-d", "--directory"}, 
        description = "Project directory to add dependency to (default: current directory)",
        defaultValue = ".",
        paramLabel = "<dir>")
    private String directory;

    @Override
    public Integer call() throws Exception {
        Path projectPath = Paths.get(directory).toAbsolutePath();
        
        DependencyManager dependencyManager = new DependencyManager();
        
        try {
            // Map module name to artifact
            String artifactId = mapModuleToArtifact(moduleName);
            if (artifactId == null) {
                System.err.println("Error: Unknown module '" + moduleName + "'");
                System.err.println("\nAvailable modules:");
                printAvailableModules();
                return 1;
            }
            
            // Add dependency
            boolean success = dependencyManager.addDependency(projectPath, artifactId);
            
            if (success) {
                System.out.println("Successfully added " + artifactId + " to your project.");
                System.out.println("\nNext steps:");
                System.out.println("1. Refresh your IDE to pick up the new dependency");
                System.out.println("2. Check the documentation for " + moduleName + " configuration");
                return 0;
            } else {
                System.err.println("Error: Could not add dependency. Make sure you're in a valid project directory.");
                return 1;
            }
            
        } catch (Exception e) {
            System.err.println("Error adding dependency: " + e.getMessage());
            if (System.getProperty("verbose") != null) {
                e.printStackTrace();
            }
            return 1;
        }
    }
    
    private String mapModuleToArtifact(String moduleName) {
        // Remove "driftkit-" prefix if present
        if (moduleName.startsWith("driftkit-")) {
            moduleName = moduleName.substring(9);
        }
        
        // Map common module names to artifacts
        switch (moduleName.toLowerCase()) {
            // Vector stores
            case "vector-pinecone":
                return "driftkit-vector-pinecone";
            case "vector-spring-ai":
                return "driftkit-vector-spring-ai-starter";
                
            // Embeddings
            case "embedding-openai":
                return "driftkit-embedding-openai";
            case "embedding-cohere":
                return "driftkit-embedding-cohere";
            case "embedding-spring-ai":
                return "driftkit-embedding-spring-ai-starter";
                
            // Clients
            case "clients-openai":
                return "driftkit-clients-openai";
            case "clients-claude":
                return "driftkit-clients-claude";
            case "clients-gemini":
                return "driftkit-clients-gemini";
            case "clients-spring-ai":
                return "driftkit-clients-spring-ai-starter";
                
            // Other modules
            case "context-engineering":
                return "driftkit-context-engineering-spring-boot-starter";
            case "audio":
                return "driftkit-audio-spring-boot-starter";
            case "chat-assistant":
                return "driftkit-chat-assistant-framework";
            case "rag":
                return "driftkit-rag-spring-boot-starter";
                
            default:
                // Try direct mapping
                if (isValidArtifact(moduleName)) {
                    return "driftkit-" + moduleName;
                }
                return null;
        }
    }
    
    private boolean isValidArtifact(String name) {
        // List of valid artifact patterns
        return name.matches("^(vector|embedding|clients|workflows|audio|rag|context)-.*$");
    }
    
    private void printAvailableModules() {
        System.err.println("  Vector Stores:");
        System.err.println("    - vector-pinecone");
        System.err.println("    - vector-spring-ai");
        System.err.println();
        System.err.println("  Embeddings:");
        System.err.println("    - embedding-openai");
        System.err.println("    - embedding-cohere");
        System.err.println("    - embedding-spring-ai");
        System.err.println();
        System.err.println("  AI Clients:");
        System.err.println("    - clients-openai");
        System.err.println("    - clients-claude");
        System.err.println("    - clients-gemini");
        System.err.println("    - clients-spring-ai");
        System.err.println();
        System.err.println("  Other Modules:");
        System.err.println("    - context-engineering");
        System.err.println("    - audio");
        System.err.println("    - chat-assistant");
        System.err.println("    - rag");
    }
}