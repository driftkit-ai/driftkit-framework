package ai.driftkit.common.domain;

import ai.driftkit.common.domain.client.ResponseFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Unified Prompt domain object used across all DriftKit modules.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Prompt {

    private String id;
    private String method;
    private int version;
    private String message;
    private String systemMessage;
    private State state;
    private String modelId;
    private ResolveStrategy resolveStrategy = ResolveStrategy.LAST_VERSION;
    private String workflow;
    private Language language = Language.GENERAL;
    private Double temperature;
    private boolean jsonRequest;
    private boolean jsonResponse;
    private ResponseFormat responseFormat;
    private List<VariableSchema> variableSchemas;
    private long createdTime;
    private long updatedTime;
    private long approvedTime;

    /**
     * Schema definition for a template variable.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableSchema {
        private String name;
        private String type;        // string, number, json, text
        private boolean required;
        private String defaultValue;
        private String description;
        private String example;
    }

    public Language getLanguage() {
        if (language == null) {
            return Language.GENERAL;
        }
        return language;
    }

    /**
     * Apply variables to the message template.
     * Note: Full template engine functionality requires context-engineering module.
     */
    public String applyVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return getMessage();
        }
        
        String result = getMessage();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    public enum ResolveStrategy {
        LAST_VERSION,
        CURRENT,
    }

    public enum State {
        DRAFT,
        AUTO_TESTING,
        MANUAL_TESTING,
        CURRENT,
        REPLACED
    }
}