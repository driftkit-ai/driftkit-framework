package ai.driftkit.context.spring.testsuite.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuration for JSON Schema validation evaluations
 */
@Slf4j
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonSchemaEvalConfig extends EvaluationConfig {
    /**
     * The JSON schema to validate against, in standard JSON Schema format.
     * Example:
     * {
     *   "$schema": "http://json-schema.org/draft-07/schema#",
     *   "type": "object",
     *   "properties": {
     *     "name": { "type": "string" },
     *     "age": { "type": "integer", "minimum": 0 }
     *   },
     *   "required": ["name"]
     * }
     */
    private String jsonSchema;
    
    /**
     * If true, only validates that the response is valid JSON, ignoring the schema
     */
    private boolean validateJsonOnly;
    
    /**
     * Result of JSON Schema validation
     */
    @Data
    @Builder
    public static class JsonSchemaEvalResult {
        private boolean passed;
        private String message;
        private Set<String> validationErrors;
        private boolean isValidJson;
    }
    
    @Override
    public EvaluationResult.EvaluationOutput evaluate(EvaluationContext context) {
        String result = context.getActualResult();
        JsonSchemaEvalResult evalResult;
        
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            
            // Check if result is valid JSON
            boolean isValidJson = isValidJson(result, objectMapper);
            
            if (!isValidJson) {
                evalResult = JsonSchemaEvalResult.builder()
                        .passed(false)
                        .isValidJson(false)
                        .message("Response is not valid JSON")
                        .build();
            }
            // If we only need to validate JSON format
            else if (isValidateJsonOnly() || getJsonSchema() == null || getJsonSchema().trim().isEmpty()) {
                evalResult = JsonSchemaEvalResult.builder()
                        .passed(true)
                        .isValidJson(true)
                        .message("Response is valid JSON")
                        .build();
            }
            // Validate against schema
            else {
                // Parse the actual JSON and schema
                JsonNode jsonNode = objectMapper.readTree(result);
                JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
                
                // Use a minimal schema if the provided one is empty or invalid
                String schemaToUse = getJsonSchema();
                if (schemaToUse == null || schemaToUse.trim().isEmpty()) {
                    schemaToUse = "{\"type\": \"object\"}";
                }
                
                JsonSchema schema = factory.getSchema(schemaToUse);
                
                // Validate against the schema
                Set<ValidationMessage> validationMessages = schema.validate(jsonNode);
                
                boolean passed = validationMessages.isEmpty();
                Set<String> errorMessages = validationMessages.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.toSet());
                
                evalResult = JsonSchemaEvalResult.builder()
                        .passed(passed)
                        .isValidJson(true)
                        .message(passed ? "JSON schema validation passed" : "JSON schema validation failed")
                        .validationErrors(errorMessages)
                        .build();
            }
            
        } catch (Exception e) {
            log.error("Error in JSON schema validation: {}", e.getMessage(), e);
            evalResult = JsonSchemaEvalResult.builder()
                    .passed(false)
                    .message("JSON schema validation error: " + e.getMessage())
                    .build();
        }
        
        // Create result output and apply negation if configured
        EvaluationResult.EvaluationOutput output = EvaluationResult.EvaluationOutput.builder()
                .status(evalResult.isPassed() ? EvaluationResult.EvaluationStatus.PASSED : EvaluationResult.EvaluationStatus.FAILED)
                .message(evalResult.getMessage())
                .details(evalResult)
                .build();
        
        return applyNegation(output);
    }
    
    /**
     * Check if a string is valid JSON
     */
    private boolean isValidJson(String json, ObjectMapper objectMapper) {
        try {
            JsonNode jsonNode = objectMapper.readTree(json);
            return jsonNode.isArray() || jsonNode.isObject();
        } catch (Exception e) {
            return false;
        }
    }
}