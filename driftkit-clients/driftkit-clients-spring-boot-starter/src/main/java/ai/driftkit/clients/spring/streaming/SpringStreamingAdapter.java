package ai.driftkit.clients.spring.streaming;

import ai.driftkit.common.domain.streaming.StreamingResponse;
import ai.driftkit.common.domain.streaming.StreamingCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * Spring adapter for converting between DriftKit's framework-agnostic
 * StreamingResponse and Spring WebFlux's Flux.
 */
public class SpringStreamingAdapter {
    
    /**
     * Convert a StreamingResponse to a Flux.
     * 
     * @param streamingResponse The framework-agnostic streaming response
     * @param <T> Type of items in the stream
     * @return A Flux that emits the same items
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
            
            // Handle cancellation
            sink.onDispose(() -> streamingResponse.cancel());
        }, FluxSink.OverflowStrategy.BUFFER);
    }
    
    /**
     * Convert a Flux to a StreamingResponse.
     * 
     * @param flux The Spring WebFlux Flux
     * @param <T> Type of items in the stream
     * @return A framework-agnostic streaming response
     */
    public static <T> StreamingResponse<T> fromFlux(Flux<T> flux) {
        return new StreamingResponse<T>() {
            private volatile boolean active = false;
            private volatile boolean cancelled = false;
            private reactor.core.Disposable disposable;
            
            @Override
            public void subscribe(StreamingCallback<T> callback) {
                if (active) {
                    callback.onError(new IllegalStateException("Stream already subscribed"));
                    return;
                }
                
                active = true;
                disposable = flux.subscribe(
                    item -> {
                        if (!cancelled) {
                            callback.onNext(item);
                        }
                    },
                    error -> {
                        active = false;
                        if (!cancelled) {
                            callback.onError(error);
                        }
                    },
                    () -> {
                        active = false;
                        if (!cancelled) {
                            callback.onComplete();
                        }
                    }
                );
            }
            
            @Override
            public void cancel() {
                cancelled = true;
                active = false;
                if (disposable != null && !disposable.isDisposed()) {
                    disposable.dispose();
                }
            }
            
            @Override
            public boolean isActive() {
                return active;
            }
        };
    }
}