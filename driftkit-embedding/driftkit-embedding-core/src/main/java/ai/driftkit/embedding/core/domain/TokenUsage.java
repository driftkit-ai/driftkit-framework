package ai.driftkit.embedding.core.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Token usage information for API calls.
 * Tracks the number of tokens used in input, output, and total.
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsage {
    
    private Integer inputTokenCount;
    private Integer outputTokenCount;
    private Integer totalTokenCount;
    
    /**
     * Creates token usage with input count only.
     * 
     * @param inputTokenCount the input token count
     */
    public TokenUsage(Integer inputTokenCount) {
        this.inputTokenCount = inputTokenCount;
        this.outputTokenCount = null;
        this.totalTokenCount = inputTokenCount;
    }
    
    /**
     * Creates token usage with input and output counts.
     * 
     * @param inputTokenCount the input token count
     * @param outputTokenCount the output token count
     */
    public TokenUsage(Integer inputTokenCount, Integer outputTokenCount) {
        this.inputTokenCount = inputTokenCount;
        this.outputTokenCount = outputTokenCount;
        this.totalTokenCount = (inputTokenCount != null ? inputTokenCount : 0) + 
                              (outputTokenCount != null ? outputTokenCount : 0);
    }
}