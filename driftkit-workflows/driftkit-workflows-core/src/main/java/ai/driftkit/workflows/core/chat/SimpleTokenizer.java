package ai.driftkit.workflows.core.chat;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * A simple Tokenizer implementation for demonstration purposes.
 */
public class SimpleTokenizer implements Tokenizer {

    public static final double DEFAULT_TOKEN_COST = 0.7;

    @Override
    public int estimateTokenCountInMessages(List<Message> messages) {
        return messages.stream()
                    .mapToInt(this::estimateTokenCountInMessage)
                    .sum();
    }

    @Override
    public int estimateTokenCountInMessage(Message message) {
        if (StringUtils.isBlank(message.text())) {
            return 0;
        }
        return (int) (message.text().length() * DEFAULT_TOKEN_COST);
    }
}
