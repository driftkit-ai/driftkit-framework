package ai.driftkit.context.spring.testsuite.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Configuration for evaluations that check array length in JSON responses
 */
@Slf4j
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArrayLengthEvalConfig extends EvaluationConfig {
    /**
     * JSON path to the array (e.g., "$.results", "$.data.items")
     */
    private String jsonPath;
    
    /**
     * Minimum length required
     */
    private Integer minLength;
    
    /**
     * Maximum length allowed
     */
    private Integer maxLength;
    
    /**
     * Exact length required (overrides min/max if specified)
     */
    private Integer exactLength;
    
    /**
     * Result of array length evaluation
     */
    @Data
    @Builder
    public static class ArrayLengthEvalResult {
        private boolean passed;
        private String message;
        private Integer length;
        private String jsonPath;
        private Integer minLength;
        private Integer maxLength;
        private Integer exactLength;
        private String error;
    }
    
    @Override
    public EvaluationResult.EvaluationOutput evaluate(EvaluationContext context) {
        String result = context.getActualResult();
        
        // Check if JSON path is empty
        if (jsonPath == null || jsonPath.isEmpty()) {
            ArrayLengthEvalResult evalResult = ArrayLengthEvalResult.builder()
                    .passed(false)
                    .message("No JSON path specified for array length check")
                    .jsonPath(jsonPath)
                    .minLength(minLength)
                    .maxLength(maxLength)
                    .exactLength(exactLength)
                    .build();
            
            return createOutputFromResult(evalResult);
        }
        
        try {
            // First, check if the result is valid JSON
            if (!isValidJson(result)) {
                ArrayLengthEvalResult evalResult = ArrayLengthEvalResult.builder()
                        .passed(false)
                        .message("Response is not valid JSON")
                        .jsonPath(jsonPath)
                        .minLength(minLength)
                        .maxLength(maxLength)
                        .exactLength(exactLength)
                        .error("Invalid JSON")
                        .build();
                
                return createOutputFromResult(evalResult);
            }
            
            // Extract the array
            try {
                Object array = JsonPath.read(result, jsonPath);
                
                if (!(array instanceof List)) {
                    ArrayLengthEvalResult evalResult = ArrayLengthEvalResult.builder()
                            .passed(false)
                            .message("JSON path does not point to an array")
                            .jsonPath(jsonPath)
                            .minLength(minLength)
                            .maxLength(maxLength)
                            .exactLength(exactLength)
                            .error("Not an array")
                            .build();
                    
                    return createOutputFromResult(evalResult);
                }
                
                List<?> list = (List<?>) array;
                int length = list.size();
                
                // Check against exact length first
                if (exactLength != null) {
                    boolean passed = length == exactLength;
                    String message = passed 
                            ? "Array length is exactly " + length 
                            : "Array length is " + length + ", expected exactly " + exactLength;
                    
                    ArrayLengthEvalResult evalResult = ArrayLengthEvalResult.builder()
                            .passed(passed)
                            .message(message)
                            .length(length)
                            .jsonPath(jsonPath)
                            .minLength(minLength)
                            .maxLength(maxLength)
                            .exactLength(exactLength)
                            .build();
                    
                    return createOutputFromResult(evalResult);
                }
                
                // Otherwise check against min/max
                boolean passedMin = minLength == null || length >= minLength;
                boolean passedMax = maxLength == null || length <= maxLength;
                boolean passed = passedMin && passedMax;
                
                // Build message
                StringBuilder message = new StringBuilder();
                message.append("Array length is ").append(length);
                
                if (!passed) {
                    message.append(" - ");
                    if (!passedMin) {
                        message.append("expected at least ").append(minLength);
                    }
                    if (!passedMin && !passedMax) {
                        message.append(" and ");
                    }
                    if (!passedMax) {
                        message.append("expected at most ").append(maxLength);
                    }
                }
                
                ArrayLengthEvalResult evalResult = ArrayLengthEvalResult.builder()
                        .passed(passed)
                        .message(message.toString())
                        .length(length)
                        .jsonPath(jsonPath)
                        .minLength(minLength)
                        .maxLength(maxLength)
                        .exactLength(exactLength)
                        .build();
                
                return createOutputFromResult(evalResult);
                
            } catch (PathNotFoundException e) {
                ArrayLengthEvalResult evalResult = ArrayLengthEvalResult.builder()
                        .passed(false)
                        .message("JSON path not found: " + jsonPath)
                        .jsonPath(jsonPath)
                        .minLength(minLength)
                        .maxLength(maxLength)
                        .exactLength(exactLength)
                        .error("Path not found")
                        .build();
                
                return createOutputFromResult(evalResult);
            }
            
        } catch (Exception e) {
            log.error("Error evaluating array length: {}", e.getMessage(), e);
            
            ArrayLengthEvalResult evalResult = ArrayLengthEvalResult.builder()
                    .passed(false)
                    .message("Error evaluating array length: " + e.getMessage())
                    .jsonPath(jsonPath)
                    .minLength(minLength)
                    .maxLength(maxLength)
                    .exactLength(exactLength)
                    .error(e.getMessage())
                    .build();
            
            return createOutputFromResult(evalResult);
        }
    }
    
    private boolean isValidJson(String json) {
        try {
            new ObjectMapper().readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
    
    private EvaluationResult.EvaluationOutput createOutputFromResult(ArrayLengthEvalResult evalResult) {
        EvaluationResult.EvaluationOutput output = EvaluationResult.EvaluationOutput.builder()
                .status(evalResult.isPassed() ? EvaluationResult.EvaluationStatus.PASSED : EvaluationResult.EvaluationStatus.FAILED)
                .message(evalResult.getMessage())
                .details(evalResult)
                .build();
        
        return applyNegation(output);
    }
}