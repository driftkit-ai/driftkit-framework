package ai.driftkit.cli.utils;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateProcessor {
    
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("/\\*([A-Z_]+)\\*/");
    
    /**
     * Load template from resources and replace placeholders
     */
    public String processTemplate(String templatePath, Map<String, String> replacements) throws IOException {
        // Load template from resources
        String template = loadTemplate(templatePath);
        
        // Replace all placeholders
        return replacePlaceholders(template, replacements);
    }
    
    /**
     * Load template file from resources
     */
    private String loadTemplate(String templatePath) throws IOException {
        String resourcePath = "/templates/" + templatePath;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Template not found: " + resourcePath);
            }
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }
    
    /**
     * Replace placeholders in template with actual values
     */
    private String replacePlaceholders(String template, Map<String, String> replacements) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = replacements.getOrDefault(placeholder, "/*" + placeholder + "*/");
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Create standard replacements map for project generation
     */
    public static Map<String, String> createStandardReplacements(String projectName, String packageName, String className) {
        return Map.of(
            "PROJECT_NAME", projectName,
            "PACKAGE_NAME", packageName,
            "CLASS_NAME", className,
            "JAVA_VERSION", "21",
            "SPRING_BOOT_VERSION", "3.3.1",
            "DRIFTKIT_VERSION", "0.6.0"
        );
    }
}