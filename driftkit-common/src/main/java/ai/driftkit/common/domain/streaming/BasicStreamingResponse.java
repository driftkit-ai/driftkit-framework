package ai.driftkit.common.domain.streaming;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Basic implementation of StreamingResponse for synchronous data.
 * Useful for converting non-streaming responses to streaming format.
 * 
 * @param <T> Type of items in the stream
 */
public class BasicStreamingResponse<T> implements StreamingResponse<T> {
    
    private final List<T> items;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    public BasicStreamingResponse(List<T> items) {
        this.items = items;
    }
    
    @Override
    public void subscribe(StreamingCallback<T> callback) {
        if (active.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                try {
                    for (T item : items) {
                        if (cancelled.get()) {
                            break;
                        }
                        callback.onNext(item);
                    }
                    if (!cancelled.get()) {
                        callback.onComplete();
                    }
                } catch (Exception e) {
                    callback.onError(e);
                } finally {
                    active.set(false);
                }
            }, executor);
        } else {
            callback.onError(new IllegalStateException("Stream already subscribed"));
        }
    }
    
    @Override
    public void cancel() {
        cancelled.set(true);
        active.set(false);
        executor.shutdown();
    }
    
    @Override
    public boolean isActive() {
        return active.get();
    }
}