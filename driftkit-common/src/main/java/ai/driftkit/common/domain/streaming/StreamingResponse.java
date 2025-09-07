package ai.driftkit.common.domain.streaming;

/**
 * Framework-agnostic interface for streaming responses.
 * Implementations can be created for different frameworks (Spring WebFlux, RxJava, etc.)
 * 
 * @param <T> Type of items in the stream
 */
public interface StreamingResponse<T> {
    
    /**
     * Subscribe to the stream with a callback.
     * 
     * @param callback Callback to receive stream events
     */
    void subscribe(StreamingCallback<T> callback);
    
    /**
     * Cancel the streaming response.
     */
    void cancel();
    
    /**
     * Check if the stream is active.
     * 
     * @return true if streaming is active, false otherwise
     */
    boolean isActive();
}