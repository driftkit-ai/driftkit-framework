package ai.driftkit.workflow.engine.spring.streaming;

import ai.driftkit.common.domain.streaming.StreamingCallback;
import ai.driftkit.common.domain.streaming.StreamingResponse;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * Adapter to convert between DriftKit streaming abstraction and Spring Reactor Flux.
 * This allows framework-agnostic DriftKit components to be used with Spring WebFlux.
 */
public class StreamingAdapter {
    
    /**
     * Convert a DriftKit StreamingResponse to Spring Reactor Flux.
     * Useful for exposing DriftKit streaming through Spring WebFlux endpoints.
     * 
     * @param streamingResponse The DriftKit streaming response
     * @param <T> Type of items in the stream
     * @return Flux that emits the same items as the StreamingResponse
     */
    public static <T> Flux<T> toFlux(StreamingResponse<T> streamingResponse) {
        return Flux.create(sink -> {
            streamingResponse.subscribe(new StreamingCallback<T>() {
                @Override
                public void onNext(T item) {
                    if (!sink.isCancelled()) {
                        sink.next(item);
                    }
                }
                
                @Override
                public void onError(Throwable error) {
                    if (!sink.isCancelled()) {
                        sink.error(error);
                    }
                }
                
                @Override
                public void onComplete() {
                    if (!sink.isCancelled()) {
                        sink.complete();
                    }
                }
            });
            
            // Handle cancellation from Flux side
            sink.onDispose(() -> streamingResponse.cancel());
        });
    }
    
    /**
     * Convert a Spring Reactor Flux to DriftKit StreamingResponse.
     * Useful for wrapping Spring WebFlux streams for use in DriftKit components.
     * 
     * @param flux The Spring Reactor Flux
     * @param <T> Type of items in the stream
     * @return StreamingResponse that subscribes to the Flux
     */
    public static <T> StreamingResponse<T> fromFlux(Flux<T> flux) {
        return new StreamingResponse<T>() {
            private volatile Disposable subscription;
            private volatile boolean active = false;
            
            @Override
            public void subscribe(StreamingCallback<T> callback) {
                if (active) {
                    callback.onError(new IllegalStateException("Stream already subscribed"));
                    return;
                }
                active = true;
                
                subscription = flux.subscribe(
                    callback::onNext,
                    error -> {
                        active = false;
                        callback.onError(error);
                    },
                    () -> {
                        active = false;
                        callback.onComplete();
                    }
                );
            }
            
            @Override
            public void cancel() {
                if (subscription != null && !subscription.isDisposed()) {
                    subscription.dispose();
                }
                active = false;
            }
            
            @Override
            public boolean isActive() {
                return active && (subscription == null || !subscription.isDisposed());
            }
        };
    }
    
    /**
     * Convert a DriftKit StreamingResponse to Spring Reactor Flux with buffering.
     * Useful for batching stream items before sending to client.
     * 
     * @param streamingResponse The DriftKit streaming response
     * @param bufferSize Number of items to buffer
     * @param <T> Type of items in the stream
     * @return Flux that emits buffered lists
     */
    public static <T> Flux<java.util.List<T>> toBufferedFlux(
            StreamingResponse<T> streamingResponse, 
            int bufferSize) {
        return toFlux(streamingResponse).buffer(bufferSize);
    }
}