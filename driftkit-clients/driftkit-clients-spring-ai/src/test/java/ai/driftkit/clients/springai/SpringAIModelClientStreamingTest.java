package ai.driftkit.clients.springai;

import ai.driftkit.common.domain.client.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.common.domain.streaming.StreamingCallback;
import ai.driftkit.common.domain.streaming.StreamingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAIModelClientStreamingTest {

    private static ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static ModelTextRequest request() {
        return ModelTextRequest.builder()
                .messages(List.of(ModelContentMessage.create(Role.user, "hi")))
                .build();
    }

    @Test
    void deliversEveryChunkInOrderAndCompletes() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chunk("Hel"), chunk(""), chunk("lo"), chunk("!")));

        StreamingResponse<String> stream = new SpringAIModelClient(chatModel).streamTextToText(request());

        List<String> received = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        stream.subscribe(new StreamingCallback<>() {
            @Override public void onNext(String item) { received.add(item); }
            @Override public void onError(Throwable error) { done.countDown(); }
            @Override public void onComplete() { done.countDown(); }
        });

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("Hel", "lo", "!"), received);
        assertFalse(stream.isActive());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void propagatesStreamErrors() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.concat(Flux.just(chunk("a")), Flux.error(new IllegalStateException("boom"))));

        StreamingResponse<String> stream = new SpringAIModelClient(chatModel).streamTextToText(request());

        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        stream.subscribe(new StreamingCallback<>() {
            @Override public void onNext(String item) { }
            @Override public void onError(Throwable e) { error.set(e); done.countDown(); }
            @Override public void onComplete() { done.countDown(); }
        });

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertNotNull(error.get());
        assertEquals("boom", error.get().getMessage());
        assertFalse(stream.isActive());
    }

    @Test
    void secondSubscriptionIsRejected() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.never());

        StreamingResponse<String> stream = new SpringAIModelClient(chatModel).streamTextToText(request());
        StreamingCallback<String> noop = new StreamingCallback<>() {
            @Override public void onNext(String item) { }
            @Override public void onError(Throwable error) { }
            @Override public void onComplete() { }
        };
        stream.subscribe(noop);
        assertTrue(stream.isActive());

        AtomicReference<Throwable> rejected = new AtomicReference<>();
        stream.subscribe(new StreamingCallback<>() {
            @Override public void onNext(String item) { }
            @Override public void onError(Throwable error) { rejected.set(error); }
            @Override public void onComplete() { }
        });
        assertNotNull(rejected.get());

        stream.cancel();
        assertFalse(stream.isActive());
    }
}
