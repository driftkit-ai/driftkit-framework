package ai.driftkit.common.domain.streaming;

/**
 * Callback interface for handling streaming events.
 * 
 * @param <T> Type of items in the stream
 */
public interface StreamingCallback<T> {
    
    /**
     * Called when a new item is available in the stream.
     * 
     * @param item The next item in the stream
     */
    void onNext(T item);
    
    /**
     * Called when an error occurs in the stream.
     * 
     * @param error The error that occurred
     */
    void onError(Throwable error);
    
    /**
     * Called when the stream completes normally.
     */
    void onComplete();
}