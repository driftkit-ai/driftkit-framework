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
import java.util.Map;

/**
 * Configuration for evaluations that check specific field values in JSON responses
 */
@Slf4j
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldValueEvalConfig extends EvaluationConfig {
    /**
     * JSON path to the field (e.g., "$.name", "$.user.email")
     */
    private String jsonPath;
    
    /**
     * Expected value of the field
     */
    private String expectedValue;
    
    /**
     * Type of expected value
     */
    private ValueType valueType;
    
    /**
     * Comparison operator to use
     */
    private ComparisonOperator operator;
    
    /**
     * Types of values that can be compared
     */
    public enum ValueType {
        STRING,
        NUMBER,
        BOOLEAN,
        NULL,
        ARRAY,
        OBJECT
    }
    
    /**
     * Comparison operators
     */
    public enum ComparisonOperator {
        EQUALS,               // ==
        NOT_EQUALS,           // !=
        GREATER_THAN,         // >
        LESS_THAN,            // <
        GREATER_THAN_EQUALS,  // >=
        LESS_THAN_EQUALS,     // <=
        CONTAINS,             // contains
        STARTS_WITH,          // starts with
        ENDS_WITH,            // ends with
        MATCHES_REGEX,        // matches regex
        EXISTS                // field exists
    }
    
    /**
     * Result of field value evaluation
     */
    @Data
    @Builder
    public static class FieldValueEvalResult {
        private boolean passed;
        private String message;
        private String jsonPath;
        private Object actualValue;
        private String expectedValue;
        private ValueType valueType;
        private ComparisonOperator operator;
        private String error;
    }
    
    @Override
    public EvaluationResult.EvaluationOutput evaluate(EvaluationContext context) {
        String result = context.getActualResult();
        
        // Check if JSON path is empty
        if (jsonPath == null || jsonPath.isEmpty()) {
            FieldValueEvalResult evalResult = FieldValueEvalResult.builder()
                    .passed(false)
                    .message("No JSON path specified for field value check")
                    .jsonPath(jsonPath)
                    .expectedValue(expectedValue)
                    .valueType(valueType)
                    .operator(operator)
                    .error("Missing JSON path")
                    .build();
            
            return createOutputFromResult(evalResult);
        }
        
        try {
            // First, check if the result is valid JSON
            if (!isValidJson(result)) {
                FieldValueEvalResult evalResult = FieldValueEvalResult.builder()
                        .passed(false)
                        .message("Response is not valid JSON")
                        .jsonPath(jsonPath)
                        .expectedValue(expectedValue)
                        .valueType(valueType)
                        .operator(operator)
                        .error("Invalid JSON")
                        .build();
                
                return createOutputFromResult(evalResult);
            }
            
            // For EXISTS operator, just check if the path exists
            if (operator == ComparisonOperator.EXISTS) {
                try {
                    JsonPath.read(result, jsonPath);
                    
                    FieldValueEvalResult evalResult = FieldValueEvalResult.builder()
                            .passed(true)
                            .message("Field exists: " + jsonPath)
                            .jsonPath(jsonPath)
                            .operator(operator)
                            .build();
                    
                    return createOutputFromResult(evalResult);
                } catch (PathNotFoundException e) {
                    FieldValueEvalResult evalResult = FieldValueEvalResult.builder()
                            .passed(false)
                            .message("Field does not exist: " + jsonPath)
                            .jsonPath(jsonPath)
                            .operator(operator)
                            .error("Path not found")
                            .build();
                    
                    return createOutputFromResult(evalResult);
                }
            }
            
            // For all other operators, we need expected value and value type
            if (expectedValue == null && operator != ComparisonOperator.EXISTS) {
                FieldValueEvalResult evalResult = FieldValueEvalResult.builder()
                        .passed(false)
                        .message("No expected value specified for field value check")
                        .jsonPath(jsonPath)
                        .expectedValue(expectedValue)
                        .valueType(valueType)
                        .operator(operator)
                        .error("Missing expected value")
                        .build();
                
                return createOutputFromResult(evalResult);
            }
            
            if (valueType == null && operator != ComparisonOperator.EXISTS) {
                FieldValueEvalResult evalResult = FieldValueEvalResult.builder()
                        .passed(false)
                        .message("No value type specified for field value check")
                        .jsonPath(jsonPath)
                        .expectedValue(expectedValue)
                        .operator(operator)
                        .error("Missing value type")
                        .build();
                
                return createOutputFromResult(evalResult);
            }
            
            // Extract the field value
            try {
                Object actualValue = JsonPath.read(result, jsonPath);
                
                // Compare values based on type and operator
                boolean passed = compareValues(actualValue, expectedValue, valueType, operator);
                
                String message = passed 
                        ? "Field value matches expectation" 
                        : "Field value does not match expectation";
                
                FieldValueEvalResult evalResult = FieldValueEvalResult.builder()
                        .passed(passed)
                        .message(message)
                        .jsonPath(jsonPath)
                        .actualValue(actualValue)
                        .expectedValue(expectedValue)
                        .valueType(valueType)
                        .operator(operator)
                        .build();
                
                return createOutputFromResult(evalResult);
                
            } catch (PathNotFoundException e) {
                FieldValueEvalResult evalResult = FieldValueEvalResult.builder()
                        .passed(false)
                        .message("JSON path not found: " + jsonPath)
                        .jsonPath(jsonPath)
                        .expectedValue(expectedValue)
                        .valueType(valueType)
                        .operator(operator)
                        .error("Path not found")
                        .build();
                
                return createOutputFromResult(evalResult);
            }
            
        } catch (Exception e) {
            log.error("Error evaluating field value: {}", e.getMessage(), e);
            
            FieldValueEvalResult evalResult = FieldValueEvalResult.builder()
                    .passed(false)
                    .message("Error evaluating field value: " + e.getMessage())
                    .jsonPath(jsonPath)
                    .expectedValue(expectedValue)
                    .valueType(valueType)
                    .operator(operator)
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
    
    /**
     * Compare values based on type and operator
     */
    private boolean compareValues(Object actual, String expected, ValueType valueType, ComparisonOperator operator) {
        if (actual == null) {
            return valueType == ValueType.NULL;
        }
        
        switch (valueType) {
            case STRING:
                return compareStringValues(actual.toString(), expected, operator);
                
            case NUMBER:
                try {
                    double actualNum = Double.parseDouble(actual.toString());
                    double expectedNum = Double.parseDouble(expected);
                    return compareNumberValues(actualNum, expectedNum, operator);
                } catch (NumberFormatException e) {
                    return false;
                }
                
            case BOOLEAN:
                boolean actualBool = Boolean.parseBoolean(actual.toString());
                boolean expectedBool = Boolean.parseBoolean(expected);
                return actualBool == expectedBool;
                
            case NULL:
                return actual == null;
                
            case ARRAY:
                if (!(actual instanceof List)) {
                    return false;
                }
                // For arrays, we only support EQUALS and NOT_EQUALS
                if (operator == ComparisonOperator.EQUALS) {
                    return actual.toString().equals(expected);
                } else if (operator == ComparisonOperator.NOT_EQUALS) {
                    return !actual.toString().equals(expected);
                }
                return false;
                
            case OBJECT:
                if (!(actual instanceof Map)) {
                    return false;
                }
                // For objects, we only support EQUALS and NOT_EQUALS
                if (operator == ComparisonOperator.EQUALS) {
                    return actual.toString().equals(expected);
                } else if (operator == ComparisonOperator.NOT_EQUALS) {
                    return !actual.toString().equals(expected);
                }
                return false;
                
            default:
                return false;
        }
    }
    
    /**
     * Compare string values
     */
    private boolean compareStringValues(String actual, String expected, ComparisonOperator operator) {
        switch (operator) {
            case EQUALS:
                return actual.equals(expected);
            case NOT_EQUALS:
                return !actual.equals(expected);
            case CONTAINS:
                return actual.contains(expected);
            case STARTS_WITH:
                return actual.startsWith(expected);
            case ENDS_WITH:
                return actual.endsWith(expected);
            case MATCHES_REGEX:
                return actual.matches(expected);
            default:
                return false;
        }
    }
    
    /**
     * Compare number values
     */
    private boolean compareNumberValues(double actual, double expected, ComparisonOperator operator) {
        switch (operator) {
            case EQUALS:
                return actual == expected;
            case NOT_EQUALS:
                return actual != expected;
            case GREATER_THAN:
                return actual > expected;
            case LESS_THAN:
                return actual < expected;
            case GREATER_THAN_EQUALS:
                return actual >= expected;
            case LESS_THAN_EQUALS:
                return actual <= expected;
            default:
                return false;
        }
    }
    
    private EvaluationResult.EvaluationOutput createOutputFromResult(FieldValueEvalResult evalResult) {
        EvaluationResult.EvaluationOutput output = EvaluationResult.EvaluationOutput.builder()
                .status(evalResult.isPassed() ? EvaluationResult.EvaluationStatus.PASSED : EvaluationResult.EvaluationStatus.FAILED)
                .message(evalResult.getMessage())
                .details(evalResult)
                .build();
        
        return applyNegation(output);
    }
}