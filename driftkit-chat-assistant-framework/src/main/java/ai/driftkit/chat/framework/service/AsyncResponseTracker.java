package ai.driftkit.chat.framework.service;

import ai.driftkit.chat.framework.model.ChatDomain.ChatResponse;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Service interface for tracking asynchronous responses.
 * Implementation should be provided by the consuming application.
 */
public interface AsyncResponseTracker {
    
    /**
     * Generate a unique response ID
     * @return A unique response ID
     */
    String generateResponseId();
    
    /**
     * Track a response for asynchronous processing
     * @param responseId The response ID
     * @param response The response to track
     */
    void trackResponse(String responseId, ChatResponse response);
    
    /**
     * Update the status of a tracked response
     * @param responseId The response ID
     * @param response The updated response
     */
    void updateResponseStatus(String responseId, ChatResponse response);
    
    /**
     * Get a tracked response by ID
     * @param responseId The response ID
     * @return The response if found, null otherwise
     */
    Optional<ChatResponse> getResponse(String responseId);
    
    /**
     * Remove a tracked response
     * @param responseId The response ID to remove
     */
    void removeResponse(String responseId);
    
    /**
     * Execute a task asynchronously and track its progress
     * @param <T> The type of response (must extend ChatResponse)
     * @param responseId The response ID for tracking
     * @param initialResponse The initial response to return immediately
     * @param task The async task to execute
     * @return A CompletableFuture that completes when the task is done
     */
    <T extends ChatResponse> CompletableFuture<T> executeAsync(
            String responseId, 
            T initialResponse,
            Supplier<T> task);
}