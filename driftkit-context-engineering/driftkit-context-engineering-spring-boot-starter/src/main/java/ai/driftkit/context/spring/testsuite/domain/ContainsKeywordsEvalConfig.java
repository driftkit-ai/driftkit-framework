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

/**
 * Configuration for evaluations that check for keywords in responses
 */
@Slf4j
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContainsKeywordsEvalConfig extends EvaluationConfig {
    /**
     * List of keywords to check for in the response
     */
    private List<String> keywords;
    
    /**
     * Match type for keywords
     */
    private MatchType matchType;
    
    /**
     * If true, matching is case-sensitive
     */
    private boolean caseSensitive;
    
    /**
     * Different types of keyword matching
     */
    public enum MatchType {
        ALL,           // All keywords must be present
        ANY,           // At least one keyword must be present
        EXACTLY,       // Exactly all these keywords must be present, no more, no less
        NONE,          // None of the keywords should be present
        MAJORITY       // More than half of the keywords must be present
    }
    
    /**
     * Result of keywords evaluation
     */
    @Data
    @Builder
    public static class KeywordsEvalResult {
        private boolean passed;
        private String message;
        private List<String> foundKeywords;
        private List<String> missingKeywords;
        private MatchType matchType;
    }
    
    @Override
    public EvaluationResult.EvaluationOutput evaluate(EvaluationContext context) {
        String result = context.getActualResult();
        List<String> foundKeywords = new ArrayList<>();
        List<String> missingKeywords = new ArrayList<>();
        
        // Check if keywords list is empty
        if (keywords == null || keywords.isEmpty()) {
            KeywordsEvalResult evalResult = KeywordsEvalResult.builder()
                    .passed(false)
                    .message("No keywords specified for evaluation")
                    .foundKeywords(foundKeywords)
                    .missingKeywords(missingKeywords)
                    .matchType(matchType)
                    .build();
            
            return createOutputFromResult(evalResult);
        }
        
        // Search for each keyword
        for (String keyword : keywords) {
            String searchKeyword = keyword;
            String searchResult = result;
            
            // Apply case-sensitivity settings
            if (!isCaseSensitive()) {
                searchKeyword = searchKeyword.toLowerCase();
                searchResult = searchResult.toLowerCase();
            }
            
            if (searchResult.contains(searchKeyword)) {
                foundKeywords.add(keyword);
            } else {
                missingKeywords.add(keyword);
            }
        }
        
        // Apply match type logic
        boolean passed = false;
        String message;
        
        switch (matchType) {
            case ALL:
                passed = missingKeywords.isEmpty();
                message = passed 
                        ? "All keywords found" 
                        : "Missing keywords: " + String.join(", ", missingKeywords);
                break;
                
            case ANY:
                passed = !foundKeywords.isEmpty();
                message = passed 
                        ? "Found keywords: " + String.join(", ", foundKeywords) 
                        : "No keywords found";
                break;
                
            case EXACTLY:
                passed = missingKeywords.isEmpty() && foundKeywords.size() == keywords.size();
                message = passed 
                        ? "Found exactly all keywords" 
                        : missingKeywords.isEmpty() 
                            ? "Found additional keywords" 
                            : "Missing keywords: " + String.join(", ", missingKeywords);
                break;
                
            case NONE:
                passed = foundKeywords.isEmpty();
                message = passed 
                        ? "No keywords found (as expected)" 
                        : "Found unexpected keywords: " + String.join(", ", foundKeywords);
                break;
                
            case MAJORITY:
                passed = foundKeywords.size() > keywords.size() / 2;
                message = passed 
                        ? "Found majority of keywords (" + foundKeywords.size() + "/" + keywords.size() + ")" 
                        : "Found only " + foundKeywords.size() + " of " + keywords.size() + " keywords";
                break;
                
            default:
                message = "Unknown match type: " + matchType;
                passed = false;
        }
        
        KeywordsEvalResult evalResult = KeywordsEvalResult.builder()
                .passed(passed)
                .message(message)
                .foundKeywords(foundKeywords)
                .missingKeywords(missingKeywords)
                .matchType(matchType)
                .build();
        
        return createOutputFromResult(evalResult);
    }
    
    private EvaluationResult.EvaluationOutput createOutputFromResult(KeywordsEvalResult evalResult) {
        EvaluationResult.EvaluationOutput output = EvaluationResult.EvaluationOutput.builder()
                .status(evalResult.isPassed() ? EvaluationResult.EvaluationStatus.PASSED : EvaluationResult.EvaluationStatus.FAILED)
                .message(evalResult.getMessage())
                .details(evalResult)
                .build();
        
        return applyNegation(output);
    }
}