package ai.driftkit.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts variables from a template that follows TemplateEngine syntax.
 * Handles regular variables, conditional blocks (if), and list iterations.
 */
@Slf4j
public class VariableExtractor {
    // Pattern for all template tags
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(.*?)\\}\\}");
    
    // Patterns for conditional and list control tags
    private static final Pattern IF_PATTERN = Pattern.compile("^\\s*if\\s+(.+)$");
    private static final Pattern LIST_PATTERN = Pattern.compile("^\\s*list\\s+(.+?)\\s+as\\s+(.+?)\\s*$");
    private static final Pattern END_TAG_PATTERN = Pattern.compile("^\\s*/(if|list)\\s*$");
    
    /**
     * Extracts variable names from the template, filtering out control structures.
     *
     * @param template The template containing variables.
     * @return A set of variable names that would need values for the template.
     */
    public Set<String> extractVariables(String template) {
        Set<String> variables = new HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        
        while (matcher.find()) {
            String tag = matcher.group(1).trim();
            
            // Skip if/endif and list/endlist tags
            Matcher ifMatcher = IF_PATTERN.matcher(tag);
            Matcher listMatcher = LIST_PATTERN.matcher(tag);
            Matcher endTagMatcher = END_TAG_PATTERN.matcher(tag);
            
            if (endTagMatcher.matches()) {
                // Skip end tags like {{/if}} and {{/list}}
                continue;
            } else if (ifMatcher.matches()) {
                // Extract variables from if conditions
                String condition = ifMatcher.group(1).trim();
                extractVariablesFromCondition(condition, variables);
            } else if (listMatcher.matches()) {
                // Extract the collection variable from list tags
                String collection = listMatcher.group(1).trim();
                // We don't add the item variable as it's defined within the loop
                variables.add(collection);
            } else {
                // Regular variable, but make sure it's not a nested property access
                if (!tag.contains(".")) {
                    variables.add(tag);
                } else {
                    // Handle dot notation by extracting the root variable
                    String rootVariable = tag.split("\\.")[0].trim();
                    variables.add(rootVariable);
                }
            }
        }
        
        log.debug("Extracted variables: {}", variables);
        return variables;
    }
    
    /**
     * Extracts variables from conditional expressions.
     *
     * @param condition The condition string from an if tag.
     * @param variables The set to add variables to.
     */
    private void extractVariablesFromCondition(String condition, Set<String> variables) {
        // Handle OR conditions
        String[] orClauses = condition.split("\\|\\|");
        for (String orClause : orClauses) {
            // Handle AND conditions
            String[] andClauses = orClause.split("&&");
            for (String clause : andClauses) {
                clause = clause.trim();
                
                if (clause.contains("==")) {
                    // Extract variable from equality checks (var == value)
                    String[] parts = clause.split("==");
                    if (parts.length >= 1) {
                        String varName = parts[0].trim();
                        if (!varName.isEmpty() && !varName.matches("^\".*\"$")) {
                            variables.add(varName);
                        }
                    }
                } else if (clause.contains(".size")) {
                    // Extract variable from size checks (var.size > 0)
                    String[] parts = clause.split("\\.");
                    if (parts.length >= 1) {
                        String varName = parts[0].trim();
                        if (!varName.isEmpty()) {
                            variables.add(varName);
                        }
                    }
                } else if (!clause.matches("^\\d+$") && !clause.equals("true") && !clause.equals("false")) {
                    // It's likely a variable name used as a boolean check
                    variables.add(clause);
                }
            }
        }
    }
}