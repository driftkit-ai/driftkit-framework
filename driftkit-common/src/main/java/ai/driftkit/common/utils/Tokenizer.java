package ai.driftkit.common.utils;

import ai.driftkit.common.domain.Message;

import java.util.List;

/**
 * Tokenizer is responsible for estimating the number of tokens in chat messages.
 */
public interface Tokenizer {
    int estimateTokenCountInMessages(List<Message> messages);
    int estimateTokenCountInMessage(Message message);
}
