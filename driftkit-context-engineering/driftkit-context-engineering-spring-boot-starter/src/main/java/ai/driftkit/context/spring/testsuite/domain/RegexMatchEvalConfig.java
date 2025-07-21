package ai.driftkit.context.spring.testsuite.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Configuration for evaluations that use regex pattern matching
 */
@Slf4j
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegexMatchEvalConfig extends EvaluationConfig {
    /**
     * Regular expression pattern to match against
     */
    private String pattern;
    
    /**
     * If true, the regex should NOT match for the test to pass
     */
    private boolean shouldNotMatch;
    
    /**
     * Minimum number of matches required
     */
    private Integer minMatches;
    
    /**
     * Maximum number of matches allowed
     */
    private Integer maxMatches;
    
    /**
     * Result of regex match evaluation
     */
    @Data
    @Builder
    public static class RegexMatchEvalResult {
        private boolean passed;
        private String message;
        private String pattern;
        private int matchCount;
        private boolean shouldNotMatch;
        private Integer minMatches;
        private Integer maxMatches;
        private List<String> matches;
        private String error;
    }
    
    @Override
    public EvaluationResult.EvaluationOutput evaluate(EvaluationContext context) {
        String result = context.getActualResult();
        
        // Check if pattern is empty
        if (pattern == null || pattern.isEmpty()) {
            RegexMatchEvalResult evalResult = RegexMatchEvalResult.builder()
                    .passed(false)
                    .message("No regex pattern specified for matching")
                    .pattern(pattern)
                    .matchCount(0)
                    .shouldNotMatch(shouldNotMatch)
                    .minMatches(minMatches)
                    .maxMatches(maxMatches)
                    .matches(new ArrayList<>())
                    .error("Missing pattern")
                    .build();
            
            return createOutputFromResult(evalResult);
        }
        
        try {
            // Compile and use the regex pattern
            Pattern regex = Pattern.compile(pattern);
            Matcher matcher = regex.matcher(result);
            
            // Count all matches and collect them
            int count = 0;
            List<String> matches = new ArrayList<>();
            while (matcher.find()) {
                count++;
                matches.add(matcher.group());
            }
            
            // If shouldNotMatch is true, we expect no matches
            if (shouldNotMatch) {
                boolean passed = count == 0;
                String message = passed 
                        ? "No regex matches found (as expected)" 
                        : "Found " + count + " regex matches (expected none)";
                
                RegexMatchEvalResult evalResult = RegexMatchEvalResult.builder()
                        .passed(passed)
                        .message(message)
                        .pattern(pattern)
                        .matchCount(count)
                        .shouldNotMatch(shouldNotMatch)
                        .minMatches(minMatches)
                        .maxMatches(maxMatches)
                        .matches(matches)
                        .build();
                
                return createOutputFromResult(evalResult);
            }
            
            // Otherwise check against min/max
            boolean passedMin = minMatches == null || count >= minMatches;
            boolean passedMax = maxMatches == null || count <= maxMatches;
            boolean passed = passedMin && passedMax;
            
            // Build message
            StringBuilder message = new StringBuilder();
            message.append("Found ").append(count).append(" regex matches");
            
            if (!passed) {
                message.append(" - ");
                if (!passedMin) {
                    message.append("expected at least ").append(minMatches);
                }
                if (!passedMin && !passedMax) {
                    message.append(" and ");
                }
                if (!passedMax) {
                    message.append("expected at most ").append(maxMatches);
                }
            }
            
            RegexMatchEvalResult evalResult = RegexMatchEvalResult.builder()
                    .passed(passed)
                    .message(message.toString())
                    .pattern(pattern)
                    .matchCount(count)
                    .shouldNotMatch(shouldNotMatch)
                    .minMatches(minMatches)
                    .maxMatches(maxMatches)
                    .matches(matches)
                    .build();
            
            return createOutputFromResult(evalResult);
            
        } catch (PatternSyntaxException e) {
            log.error("Invalid regex pattern: {}", e.getMessage(), e);
            
            RegexMatchEvalResult evalResult = RegexMatchEvalResult.builder()
                    .passed(false)
                    .message("Invalid regex pattern: " + e.getMessage())
                    .pattern(pattern)
                    .matchCount(0)
                    .shouldNotMatch(shouldNotMatch)
                    .minMatches(minMatches)
                    .maxMatches(maxMatches)
                    .matches(new ArrayList<>())
                    .error("Invalid pattern: " + e.getMessage())
                    .build();
            
            return createOutputFromResult(evalResult);
        } catch (Exception e) {
            log.error("Error evaluating regex match: {}", e.getMessage(), e);
            
            RegexMatchEvalResult evalResult = RegexMatchEvalResult.builder()
                    .passed(false)
                    .message("Error evaluating regex match: " + e.getMessage())
                    .pattern(pattern)
                    .matchCount(0)
                    .shouldNotMatch(shouldNotMatch)
                    .minMatches(minMatches)
                    .maxMatches(maxMatches)
                    .matches(new ArrayList<>())
                    .error(e.getMessage())
                    .build();
            
            return createOutputFromResult(evalResult);
        }
    }
    
    private EvaluationResult.EvaluationOutput createOutputFromResult(RegexMatchEvalResult evalResult) {
        EvaluationResult.EvaluationOutput output = EvaluationResult.EvaluationOutput.builder()
                .status(evalResult.isPassed() ? EvaluationResult.EvaluationStatus.PASSED : EvaluationResult.EvaluationStatus.FAILED)
                .message(evalResult.getMessage())
                .details(evalResult)
                .build();
        
        return applyNegation(output);
    }
}