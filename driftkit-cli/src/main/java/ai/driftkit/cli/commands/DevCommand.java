package ai.driftkit.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@Command(
    name = "dev",
    header = "Start the DriftKit development server",
    description = {
        "Starts a development server with hot-reload for your DriftKit application.",
        "Automatically detects Maven or Gradle and uses the appropriate command.",
        "The server will restart automatically when you make changes to your code."
    },
    footerHeading = "%nExamples:%n",
    footer = {
        "  driftkit dev                    # Start on default port 4111",
        "  driftkit dev --port 8080        # Start on custom port",
        "  driftkit dev -d ./my-project    # Start from specific directory",
        "",
        "The dev server will:",
        "  - Auto-detect Maven (mvn/mvnw) or Gradle (gradle/gradlew)",
        "  - Set SERVER_PORT environment variable",
        "  - Enable hot-reload for rapid development",
        "  - Forward all logs to console"
    }
)
public class DevCommand implements Callable<Integer> {

    @Option(names = {"-p", "--port"}, 
        description = "Port to run the development server on (default: ${DEFAULT-VALUE})",
        defaultValue = "4111",
        paramLabel = "<port>")
    private int port;

    @Option(names = {"-d", "--directory"}, 
        description = "Project directory to run from (default: current directory)",
        defaultValue = ".",
        paramLabel = "<dir>")
    private String directory;

    @Override
    public Integer call() throws Exception {
        Path projectPath = Paths.get(directory).toAbsolutePath();
        
        // Check if we're in a valid project directory
        if (!isValidProject(projectPath)) {
            System.err.println("Error: Not a valid DriftKit project directory.");
            System.err.println("Please run this command from a project created with 'driftkit new'.");
            return 1;
        }
        
        // Detect build system
        String buildCommand = detectBuildCommand(projectPath);
        if (buildCommand == null) {
            System.err.println("Error: Could not detect build system (Maven or Gradle).");
            return 1;
        }
        
        System.out.println("Starting DriftKit development server on port " + port + "...");
        System.out.println("Using build command: " + buildCommand);
        
        // Set the server port as environment variable
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.environment().put("SERVER_PORT", String.valueOf(port));
        processBuilder.directory(projectPath.toFile());
        processBuilder.inheritIO();
        
        // Parse the command
        if (buildCommand.contains(" ")) {
            processBuilder.command(buildCommand.split(" "));
        } else {
            processBuilder.command(buildCommand);
        }
        
        try {
            Process process = processBuilder.start();
            
            // Add shutdown hook to terminate the process
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (process.isAlive()) {
                    process.destroy();
                }
            }));
            
            // Wait for the process to complete
            int exitCode = process.waitFor();
            return exitCode;
            
        } catch (Exception e) {
            System.err.println("Error starting development server: " + e.getMessage());
            return 1;
        }
    }
    
    private boolean isValidProject(Path projectPath) {
        // Check for pom.xml or build.gradle
        return Files.exists(projectPath.resolve("pom.xml")) || 
               Files.exists(projectPath.resolve("build.gradle")) ||
               Files.exists(projectPath.resolve("build.gradle.kts"));
    }
    
    private String detectBuildCommand(Path projectPath) {
        // Check for Maven
        if (Files.exists(projectPath.resolve("pom.xml"))) {
            // Check if mvnw exists
            Path mvnw = projectPath.resolve("mvnw");
            if (Files.exists(mvnw) && Files.isExecutable(mvnw)) {
                return "./mvnw spring-boot:run";
            }
            return "mvn spring-boot:run";
        }
        
        // Check for Gradle
        if (Files.exists(projectPath.resolve("build.gradle")) || 
            Files.exists(projectPath.resolve("build.gradle.kts"))) {
            // Check if gradlew exists
            Path gradlew = projectPath.resolve("gradlew");
            if (Files.exists(gradlew) && Files.isExecutable(gradlew)) {
                return "./gradlew bootRun";
            }
            return "gradle bootRun";
        }
        
        return null;
    }
}