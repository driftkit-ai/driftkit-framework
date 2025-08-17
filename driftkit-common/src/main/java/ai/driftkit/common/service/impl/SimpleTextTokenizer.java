package ai.driftkit.common.service.impl;

import ai.driftkit.common.service.TextTokenizer;

/**
 * Simple implementation of TextTokenizer using character count approximation.
 * Estimates ~4 characters per token on average.
 */
public class SimpleTextTokenizer implements TextTokenizer {
    
    private static final int CHARS_PER_TOKEN = 4;
    
    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Simple approximation: ~4 characters per token
        return text.length() / CHARS_PER_TOKEN;
    }
}