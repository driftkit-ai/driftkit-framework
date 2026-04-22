package ai.driftkit.workflow.engine.agent;

import java.util.Map;

/**
 * Extracts the content to store in chat history from the full API message.
 * <p>
 * When an LLM agent sends a message that includes injected context (summaries, memories,
 * RAG results, etc.), the full rendered message should go to the API, but only the raw
 * user input should be persisted in chat history. This extractor defines that separation.
 * <p>
 * If not set on the agent, the full message is stored as-is (default behavior).
 *
 * <pre>{@code
 * // Store only the "userMessage" variable value in history
 * LLMAgent.builder()
 *     .storeContentExtractor((msg, vars) ->
 *         vars != null && vars.containsKey("userMessage")
 *             ? vars.get("userMessage").toString()
 *             : msg)
 *     .build();
 *
 * // Or use the built-in helper for a common case
 * LLMAgent.builder()
 *     .storeContentExtractor(StoreContentExtractor.fromVariable("userMessage"))
 *     .build();
 * }</pre>
 */
@FunctionalInterface
public interface StoreContentExtractor {

    /**
     * Extract the content that should be stored in chat history.
     *
     * @param message   the full rendered message that will be sent to the API
     * @param variables the template variables used to render the message (may be null or empty)
     * @return the content to persist in chat history
     */
    String extract(String message, Map<String, Object> variables);

    /**
     * Creates an extractor that stores only the value of a specific template variable.
     * Falls back to the full message if the variable is not present.
     *
     * @param variableName the variable whose value should be stored
     * @return extractor instance
     */
    static StoreContentExtractor fromVariable(String variableName) {
        return (message, variables) -> {
            if (variables != null && variables.containsKey(variableName)) {
                Object value = variables.get(variableName);
                return value != null ? value.toString() : message;
            }
            return message;
        };
    }
}
