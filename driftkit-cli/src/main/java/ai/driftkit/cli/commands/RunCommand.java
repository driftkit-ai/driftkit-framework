package ai.driftkit.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "run",
    header = "Run Maven or Gradle commands in the project",
    description = {
        "Executes Maven or Gradle commands within your DriftKit project.",
        "Automatically detects the build system and uses the appropriate tool.",
        "Supports all standard Maven/Gradle goals and tasks."
    },
    footerHeading = "%nExamples:%n",
    footer = {
        "  driftkit run clean install           # Build the project",
        "  driftkit run test                    # Run tests",
        "  driftkit run clean install -s        # Build without tests",
        "  driftkit run package -p production   # Package with profile",
        "  driftkit run dependency:tree         # Show dependency tree",
        "  driftkit run spring-boot:run -Dspring.profiles.active=dev",
        "",
        "Common Maven goals:",
        "  clean, compile, test, package, install, deploy",
        "  spring-boot:run, dependency:tree, versions:display-dependency-updates",
        "",
        "Common Gradle tasks:",
        "  clean, build, test, bootRun, dependencies"
    }
)
public class RunCommand implements Callable<Integer> {

    @Parameters(description = "Maven goals or Gradle tasks to execute",
        paramLabel = "<command>")
    private List<String> commandParts = new ArrayList<>();

    @Option(names = {"-d", "--directory"}, 
        description = "Project directory to run from (default: current directory)",
        defaultValue = ".",
        paramLabel = "<dir>")
    private String directory;

    @Option(names = {"-s", "--skip-tests"}, 
        description = "Skip test execution (adds -DskipTests for Maven, -x test for Gradle)")
    private boolean skipTests;

    @Option(names = {"-p", "--profile"}, 
        description = "Maven profile to activate (e.g., production, development)",
        paramLabel = "<profile>")
    private String profile;

    @Option(names = {"-D", "--define"}, 
        description = "Define system properties (e.g., -Dproperty=value)",
        paramLabel = "<property=value>")
    private List<String> systemProperties = new ArrayList<>();

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
        BuildSystem buildSystem = detectBuildSystem(projectPath);
        if (buildSystem == null) {
            System.err.println("Error: Could not detect build system (Maven or Gradle).");
            return 1;
        }
        
        // Build the command
        List<String> command = new ArrayList<>();
        
        if (buildSystem == BuildSystem.MAVEN) {
            // Check if mvnw exists
            Path mvnw = projectPath.resolve("mvnw");
            if (Files.exists(mvnw) && Files.isExecutable(mvnw)) {
                command.add("./mvnw");
            } else {
                command.add("mvn");
            }
            
            // Add command parts
            command.addAll(commandParts);
            
            // Add options
            if (skipTests) {
                command.add("-DskipTests");
            }
            
            if (profile != null) {
                command.add("-P" + profile);
            }
            
            // Add system properties
            for (String prop : systemProperties) {
                command.add("-D" + prop);
            }
        } else {
            // Gradle
            Path gradlew = projectPath.resolve("gradlew");
            if (Files.exists(gradlew) && Files.isExecutable(gradlew)) {
                command.add("./gradlew");
            } else {
                command.add("gradle");
            }
            
            // Add command parts
            command.addAll(commandParts);
            
            // Add options
            if (skipTests) {
                command.add("-x");
                command.add("test");
            }
            
            // Add system properties
            for (String prop : systemProperties) {
                command.add("-D" + prop);
            }
        }
        
        System.out.println("Running: " + String.join(" ", command));
        System.out.println("Working directory: " + projectPath);
        System.out.println();
        
        // Execute the command
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(projectPath.toFile());
        processBuilder.inheritIO();
        
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
            
            if (exitCode == 0) {
                System.out.println("\nCommand completed successfully.");
            } else {
                System.err.println("\nCommand failed with exit code: " + exitCode);
            }
            
            return exitCode;
            
        } catch (Exception e) {
            System.err.println("Error running command: " + e.getMessage());
            return 1;
        }
    }
    
    private boolean isValidProject(Path projectPath) {
        return Files.exists(projectPath.resolve("pom.xml")) || 
               Files.exists(projectPath.resolve("build.gradle")) ||
               Files.exists(projectPath.resolve("build.gradle.kts"));
    }
    
    private BuildSystem detectBuildSystem(Path projectPath) {
        if (Files.exists(projectPath.resolve("pom.xml"))) {
            return BuildSystem.MAVEN;
        }
        
        if (Files.exists(projectPath.resolve("build.gradle")) || 
            Files.exists(projectPath.resolve("build.gradle.kts"))) {
            return BuildSystem.GRADLE;
        }
        
        return null;
    }
    
    private enum BuildSystem {
        MAVEN, GRADLE
    }
}