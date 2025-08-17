package ai.driftkit.common.service;

/**
 * Simple text tokenizer for estimating token counts.
 */
public interface TextTokenizer {
    
    /**
     * Estimate token count for a text string.
     */
    int estimateTokens(String text);
}