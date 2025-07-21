package ai.driftkit.common.service;

import ai.driftkit.common.domain.ChatMessageType;
import ai.driftkit.common.domain.Message;
import ai.driftkit.common.utils.Tokenizer;
import ai.driftkit.common.utils.ValidationUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * TokenWindowChatMemory is a custom chat memory implementation that retains the most recent messages
 * within a fixed token window.
 */
@Slf4j
public class TokenWindowChatMemory implements ChatMemory {

    public static final int MESSAGES_LIMIT = 200;
    private final String id;
    private final Integer maxTokens;
    private final Tokenizer tokenizer;
    private final ChatMemoryStore store;

    private TokenWindowChatMemory(String id, int maxTokens, Tokenizer tokenizer, ChatMemoryStore memoryStore) {
        this.id = ValidationUtils.ensureNotNull(id, "id");
        this.maxTokens = ValidationUtils.ensureGreaterThanZero(maxTokens, "maxTokens");
        this.tokenizer = ValidationUtils.ensureNotNull(tokenizer, "tokenizer");
        this.store = ValidationUtils.ensureNotNull(memoryStore, "store");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void add(Message message) {
        List<Message> messages = messages();

        if (message.type() == ChatMessageType.SYSTEM) {
            Optional<Message> maybeSystemMessage = findSystemMessage(messages);
            if (maybeSystemMessage.isPresent()) {
                if (maybeSystemMessage.get().equals(message)) {
                    return; // Do not add the same system message twice.
                } else {
                    messages.remove(maybeSystemMessage.get());
                }
            }
        }
        messages.add(message);
        ensureCapacity(messages, maxTokens, tokenizer);
        store.updateMessages(id, messages);
    }

    private static Optional<Message> findSystemMessage(List<Message> messages) {
        return messages.stream()
                .filter(msg -> msg.type() == ChatMessageType.SYSTEM)
                .findAny();
    }

    @Override
    //TODO: remove constant
    public List<Message> messages() {
        List<Message> messages = new LinkedList<>(store.getMessages(id, MESSAGES_LIMIT));
        ensureCapacity(messages, maxTokens, tokenizer);
        return messages;
    }

    private static void ensureCapacity(List<Message> messages, int maxTokens, Tokenizer tokenizer) {
        int currentTokenCount = tokenizer.estimateTokenCountInMessages(messages);
        while (currentTokenCount > maxTokens) {

            int messageToEvictIndex = 0;
            if (messages.get(0).type() == ChatMessageType.SYSTEM) {
                messageToEvictIndex = 1;
            }

            Message evictedMessage = messages.remove(messageToEvictIndex);
            int tokenCountOfEvictedMessage = tokenizer.estimateTokenCountInMessage(evictedMessage);
            log.trace("Evicting message ({} tokens) to meet capacity: {}", tokenCountOfEvictedMessage, evictedMessage);
            currentTokenCount -= tokenCountOfEvictedMessage;

            //TODO: tools support
//            if (evictedMessage.getType() == ChatMessageType.AI && evictedMessage.hasToolExecutionRequests()) {
//                while (messages.size() > messageToEvictIndex
//                        && messages.get(messageToEvictIndex) instanceof ToolExecutionResultMessage) {
//                    ChatMessage orphanToolExecutionResultMessage = messages.remove(messageToEvictIndex);
//                    log.trace("Evicting orphan message: {}", orphanToolExecutionResultMessage);
//                    currentTokenCount -= tokenizer.estimateTokenCountInMessage(orphanToolExecutionResultMessage);
//                }
//            }
        }
    }

    @Override
    public void clear() {
        store.deleteMessages(id);
    }

    public static TokenWindowChatMemory withMaxTokens(int maxTokens, Tokenizer tokenizer) {
        return new TokenWindowChatMemory(
                UUID.randomUUID().toString(),
                maxTokens,
                tokenizer,
                new InMemoryChatMemoryStore()
        );
    }

    public static TokenWindowChatMemory withMaxTokens(String chatId, int maxTokens, Tokenizer tokenizer, ChatMemoryStore memoryStore) {
        return new TokenWindowChatMemory(
                chatId,
                maxTokens,
                tokenizer,
                memoryStore
        );
    }
}
