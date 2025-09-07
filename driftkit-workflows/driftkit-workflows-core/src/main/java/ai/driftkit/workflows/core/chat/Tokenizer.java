package ai.driftkit.workflows.core.chat;


import java.util.List;

/**
 * Tokenizer is responsible for estimating the number of tokens in chat messages.
 */
public interface Tokenizer {
    int estimateTokenCountInMessages(List<Message> messages);
    int estimateTokenCountInMessage(Message message);
}
