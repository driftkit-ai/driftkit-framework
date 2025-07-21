package ai.driftkit.context.spring.testsuite.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Configuration for evaluations that count word occurrences
 */
@Slf4j
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WordCountEvalConfig extends EvaluationConfig {
    /**
     * The word or phrase to count
     */
    private String word;
    
    /**
     * Minimum occurrences required
     */
    private Integer minOccurrences;
    
    /**
     * Maximum occurrences allowed
     */
    private Integer maxOccurrences;
    
    /**
     * If true, matching is case-sensitive
     */
    private boolean caseSensitive;
    
    /**
     * If true, matches only whole words (not parts of words)
     */
    private boolean wholeWordsOnly;
    
    /**
     * Result of word count evaluation
     */
    @Data
    @Builder
    public static class WordCountEvalResult {
        private boolean passed;
        private String message;
        private int count;
        private String word;
        private Integer minOccurrences;
        private Integer maxOccurrences;
        private boolean caseSensitive;
        private boolean wholeWordsOnly;
    }
    
    @Override
    public EvaluationResult.EvaluationOutput evaluate(EvaluationContext context) {
        String result = context.getActualResult();
        
        // Check if word is empty
        if (word == null || word.isEmpty()) {
            WordCountEvalResult evalResult = WordCountEvalResult.builder()
                    .passed(false)
                    .message("No word specified for counting")
                    .count(0)
                    .word(word)
                    .minOccurrences(minOccurrences)
                    .maxOccurrences(maxOccurrences)
                    .caseSensitive(caseSensitive)
                    .wholeWordsOnly(wholeWordsOnly)
                    .build();
            
            return createOutputFromResult(evalResult);
        }
        
        // Count occurrences
        int count;
        if (wholeWordsOnly) {
            // Count whole words only
            String regex = caseSensitive ? "\\b" + Pattern.quote(word) + "\\b" : "(?i)\\b" + Pattern.quote(word) + "\\b";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(result);
            
            count = 0;
            while (matcher.find()) {
                count++;
            }
        } else {
            // Count all occurrences
            String searchWord = word;
            String searchResult = result;
            
            // Apply case-sensitivity settings
            if (!caseSensitive) {
                searchWord = searchWord.toLowerCase();
                searchResult = searchResult.toLowerCase();
            }
            
            count = 0;
            int index = 0;
            while ((index = searchResult.indexOf(searchWord, index)) != -1) {
                count++;
                index += searchWord.length();
            }
        }
        
        // Check against min/max
        boolean passedMin = minOccurrences == null || count >= minOccurrences;
        boolean passedMax = maxOccurrences == null || count <= maxOccurrences;
        boolean passed = passedMin && passedMax;
        
        // Build message
        StringBuilder message = new StringBuilder();
        message.append("Found ").append(count).append(" occurrences of '").append(word).append("'");
        
        if (!passed) {
            message.append(" - ");
            if (!passedMin) {
                message.append("expected at least ").append(minOccurrences);
            }
            if (!passedMin && !passedMax) {
                message.append(" and ");
            }
            if (!passedMax) {
                message.append("expected at most ").append(maxOccurrences);
            }
        }
        
        WordCountEvalResult evalResult = WordCountEvalResult.builder()
                .passed(passed)
                .message(message.toString())
                .count(count)
                .word(word)
                .minOccurrences(minOccurrences)
                .maxOccurrences(maxOccurrences)
                .caseSensitive(caseSensitive)
                .wholeWordsOnly(wholeWordsOnly)
                .build();
        
        return createOutputFromResult(evalResult);
    }
    
    private EvaluationResult.EvaluationOutput createOutputFromResult(WordCountEvalResult evalResult) {
        EvaluationResult.EvaluationOutput output = EvaluationResult.EvaluationOutput.builder()
                .status(evalResult.isPassed() ? EvaluationResult.EvaluationStatus.PASSED : EvaluationResult.EvaluationStatus.FAILED)
                .message(evalResult.getMessage())
                .details(evalResult)
                .build();
        
        return applyNegation(output);
    }
}