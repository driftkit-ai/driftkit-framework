package ai.driftkit.cli.utils;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DependencyManager {
    
    private static final String DRIFTKIT_GROUP_ID = "ai.driftkit";
    private static final String DRIFTKIT_VERSION = "${driftkit.version}";
    
    public boolean addDependency(Path projectPath, String artifactId) throws IOException {
        // Check for Maven project
        Path pomPath = projectPath.resolve("pom.xml");
        if (Files.exists(pomPath)) {
            return addMavenDependency(pomPath, artifactId);
        }
        
        // Check for Gradle project
        Path buildGradlePath = projectPath.resolve("build.gradle");
        if (Files.exists(buildGradlePath)) {
            return addGradleDependency(buildGradlePath, artifactId);
        }
        
        Path buildGradleKtsPath = projectPath.resolve("build.gradle.kts");
        if (Files.exists(buildGradleKtsPath)) {
            return addGradleKtsDependency(buildGradleKtsPath, artifactId);
        }
        
        return false;
    }
    
    private boolean addMavenDependency(Path pomPath, String artifactId) throws IOException {
        try {
            // Read existing pom.xml
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model;
            try (FileReader fileReader = new FileReader(pomPath.toFile())) {
                model = reader.read(fileReader);
            }
            
            // Check if dependency already exists
            List<Dependency> dependencies = model.getDependencies();
            for (Dependency dep : dependencies) {
                if (DRIFTKIT_GROUP_ID.equals(dep.getGroupId()) && artifactId.equals(dep.getArtifactId())) {
                    System.out.println("Dependency " + artifactId + " already exists in pom.xml");
                    return true;
                }
            }
            
            // Add new dependency
            Dependency newDep = new Dependency();
            newDep.setGroupId(DRIFTKIT_GROUP_ID);
            newDep.setArtifactId(artifactId);
            newDep.setVersion(DRIFTKIT_VERSION);
            model.addDependency(newDep);
            
            // Write updated pom.xml
            MavenXpp3Writer writer = new MavenXpp3Writer();
            try (FileWriter fileWriter = new FileWriter(pomPath.toFile())) {
                writer.write(fileWriter, model);
            }
            
            return true;
            
        } catch (XmlPullParserException e) {
            throw new IOException("Error parsing pom.xml: " + e.getMessage(), e);
        }
    }
    
    private boolean addGradleDependency(Path buildGradlePath, String artifactId) throws IOException {
        String content = Files.readString(buildGradlePath, StandardCharsets.UTF_8);
        
        // Check if dependency already exists
        if (content.contains(artifactId)) {
            System.out.println("Dependency " + artifactId + " already exists in build.gradle");
            return true;
        }
        
        // Find dependencies block
        Pattern pattern = Pattern.compile("dependencies\\s*\\{([^}]+)\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            String dependenciesBlock = matcher.group(1);
            String newDependency = String.format("\n    implementation '%s:%s:%s'", 
                                               DRIFTKIT_GROUP_ID, artifactId, "${driftkit.version}");
            
            // Add the new dependency
            String updatedBlock = dependenciesBlock + newDependency;
            String updatedContent = content.substring(0, matcher.start(1)) + 
                                  updatedBlock + 
                                  content.substring(matcher.end(1));
            
            // Ensure driftkit.version property exists
            if (!content.contains("driftkit.version") && !content.contains("driftkitVersion")) {
                // Add version property to ext block or create one
                Pattern extPattern = Pattern.compile("ext\\s*\\{([^}]+)\\}", Pattern.DOTALL);
                Matcher extMatcher = extPattern.matcher(updatedContent);
                
                if (extMatcher.find()) {
                    String extBlock = extMatcher.group(1);
                    String updatedExtBlock = extBlock + "\n    driftkitVersion = '0.6.0'";
                    updatedContent = updatedContent.substring(0, extMatcher.start(1)) + 
                                   updatedExtBlock + 
                                   updatedContent.substring(extMatcher.end(1));
                } else {
                    // Add ext block before dependencies
                    updatedContent = "ext {\n    driftkitVersion = '0.6.0'\n}\n\n" + updatedContent;
                }
                
                // Update the dependency to use the property
                updatedContent = updatedContent.replace("${driftkit.version}", "$driftkitVersion");
            }
            
            Files.writeString(buildGradlePath, updatedContent, StandardCharsets.UTF_8);
            return true;
        }
        
        return false;
    }
    
    private boolean addGradleKtsDependency(Path buildGradleKtsPath, String artifactId) throws IOException {
        String content = Files.readString(buildGradleKtsPath, StandardCharsets.UTF_8);
        
        // Check if dependency already exists
        if (content.contains(artifactId)) {
            System.out.println("Dependency " + artifactId + " already exists in build.gradle.kts");
            return true;
        }
        
        // Find dependencies block
        Pattern pattern = Pattern.compile("dependencies\\s*\\{([^}]+)\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            String dependenciesBlock = matcher.group(1);
            String newDependency = String.format("\n    implementation(\"%s:%s:${driftkitVersion}\")", 
                                               DRIFTKIT_GROUP_ID, artifactId);
            
            // Add the new dependency
            String updatedBlock = dependenciesBlock + newDependency;
            String updatedContent = content.substring(0, matcher.start(1)) + 
                                  updatedBlock + 
                                  content.substring(matcher.end(1));
            
            // Ensure driftkitVersion property exists
            if (!content.contains("driftkitVersion")) {
                // Add version property
                Pattern valPattern = Pattern.compile("val\\s+\\w+\\s*=");
                Matcher valMatcher = valPattern.matcher(updatedContent);
                
                if (valMatcher.find()) {
                    // Add after first val declaration
                    int insertPos = valMatcher.start();
                    updatedContent = updatedContent.substring(0, insertPos) + 
                                   "val driftkitVersion = \"0.6.0\"\n" + 
                                   updatedContent.substring(insertPos);
                } else {
                    // Add at the beginning
                    updatedContent = "val driftkitVersion = \"0.6.0\"\n\n" + updatedContent;
                }
            }
            
            Files.writeString(buildGradleKtsPath, updatedContent, StandardCharsets.UTF_8);
            return true;
        }
        
        return false;
    }
}