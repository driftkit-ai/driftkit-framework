package ai.driftkit.context.spring.testsuite.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for exact text matching evaluations
 */
@Slf4j
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExactMatchEvalConfig extends EvaluationConfig {
    /**
     * The exact text to match against
     */
    private String expectedText;
    
    /**
     * If true, matching is case-sensitive
     */
    private boolean caseSensitive;
    
    /**
     * If true, ignores whitespace differences
     */
    private boolean ignoreWhitespace;
    
    /**
     * Result of exact match evaluation
     */
    @Data
    @Builder
    public static class ExactMatchEvalResult {
        private boolean passed;
        private String message;
        private String expectedText;
        private String actualText;
        private boolean caseSensitive;
        private boolean ignoreWhitespace;
    }
    
    @Override
    public EvaluationResult.EvaluationOutput evaluate(EvaluationContext context) {
        String result = context.getActualResult();
        
        // Check if expected text is empty
        if (expectedText == null || expectedText.isEmpty()) {
            ExactMatchEvalResult evalResult = ExactMatchEvalResult.builder()
                    .passed(false)
                    .message("No expected text specified for evaluation")
                    .expectedText(expectedText)
                    .actualText(result)
                    .caseSensitive(caseSensitive)
                    .ignoreWhitespace(ignoreWhitespace)
                    .build();
            
            return createOutputFromResult(evalResult);
        }
        
        String processedExpected = expectedText;
        String processedResult = result;
        
        // Apply case sensitivity
        if (!caseSensitive) {
            processedExpected = processedExpected.toLowerCase();
            processedResult = processedResult.toLowerCase();
        }
        
        // Apply whitespace handling
        if (ignoreWhitespace) {
            processedExpected = processedExpected.replaceAll("\\s+", " ").trim();
            processedResult = processedResult.replaceAll("\\s+", " ").trim();
        }
        
        boolean matches = processedResult.equals(processedExpected);
        String message = matches 
                ? "Result matches expected text exactly" 
                : "Result does not match expected text";
        
        ExactMatchEvalResult evalResult = ExactMatchEvalResult.builder()
                .passed(matches)
                .message(message)
                .expectedText(expectedText)
                .actualText(result)
                .caseSensitive(caseSensitive)
                .ignoreWhitespace(ignoreWhitespace)
                .build();
        
        return createOutputFromResult(evalResult);
    }
    
    private EvaluationResult.EvaluationOutput createOutputFromResult(ExactMatchEvalResult evalResult) {
        EvaluationResult.EvaluationOutput output = EvaluationResult.EvaluationOutput.builder()
                .status(evalResult.isPassed() ? EvaluationResult.EvaluationStatus.PASSED : EvaluationResult.EvaluationStatus.FAILED)
                .message(evalResult.getMessage())
                .details(evalResult)
                .build();
        
        return applyNegation(output);
    }
}