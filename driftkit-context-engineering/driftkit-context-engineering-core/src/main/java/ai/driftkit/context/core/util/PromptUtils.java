package ai.driftkit.context.core.util;

import ai.driftkit.context.core.service.TemplateEngine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import ai.driftkit.common.utils.JsonUtils;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.HashMap;
import java.util.Map;

public class PromptUtils {
    
    public static String applyVariables(String message, Map<String, Object> variables) {
        return applyVariables(message, variables, false);
    }

    /**
     * @param escapeXml escape {@code &}, {@code <}, {@code >} in substituted values - use it for
     *                  tag-structured prompts, where a value must never open or close a tag
     */
    public static String applyVariables(String message, Map<String, Object> variables, boolean escapeXml) {
        if (variables != null) {
            message = TemplateEngine.renderTemplate(message, variables, escapeXml);
        }
        return message;
    }

    public static String hashString(String input) {
        return DigestUtils.sha256Hex(input);
    }
    
    public static Map<String, String> convertVariables(Map<String, Object> variables) {
        Map<String, String> result = new HashMap<>();
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof JsonNode) {
                    try {
                        result.put(entry.getKey(), JsonUtils.toJson(value));
                    } catch (JsonProcessingException e) {
                        // In case of exception, fallback to toString()
                        result.put(entry.getKey(), String.valueOf(value));
                    }
                } else {
                    result.put(entry.getKey(), String.valueOf(value));
                }
            }
        }
        return result;
    }
}