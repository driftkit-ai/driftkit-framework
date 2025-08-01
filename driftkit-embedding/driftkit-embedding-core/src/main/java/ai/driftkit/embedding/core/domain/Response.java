package ai.driftkit.embedding.core.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Response wrapper for embedding operations.
 * Contains the result and optional token usage information.
 * 
 * @param <T> the type of content in the response
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public class Response<T> {
    
    private T content;
    private TokenUsage tokenUsage;
    private String finishReason;
    
    /**
     * Creates a response with content only.
     * 
     * @param content the content
     * @param <T> the type of content
     * @return the response
     */
    public static <T> Response<T> from(T content) {
        return new Response<>(content, null, null);
    }
    
    /**
     * Creates a response with content and token usage.
     * 
     * @param content the content
     * @param tokenUsage the token usage information
     * @param <T> the type of content
     * @return the response
     */
    public static <T> Response<T> from(T content, TokenUsage tokenUsage) {
        return new Response<>(content, tokenUsage, null);
    }
    
    /**
     * Creates a response with content, token usage, and finish reason.
     * 
     * @param content the content
     * @param tokenUsage the token usage information
     * @param finishReason the reason why the operation finished
     * @param <T> the type of content
     * @return the response
     */
    public static <T> Response<T> from(T content, TokenUsage tokenUsage, String finishReason) {
        return new Response<>(content, tokenUsage, finishReason);
    }
}