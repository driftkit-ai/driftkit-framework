package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.domain.client.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelMessage;
import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.domain.client.Role;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Two-level context manager (plan, D5):
 *
 * <ol>
 *   <li><b>Micro-compaction</b> (cheap, no LLM): when the estimated token count
 *       crosses the soft threshold, contents of old tool-result messages are
 *       replaced with a short placeholder. Message structure (and tool pairing)
 *       is preserved — only the content shrinks.</li>
 *   <li><b>Summary compaction</b> (LLM call): when the hard threshold is crossed,
 *       the older part of the conversation is replaced by a single summary
 *       message produced with the 8-section compact prompt.</li>
 * </ol>
 *
 * <p>Both levels keep the invariants of plan section 7: the system message and
 * the most recent messages are never dropped, and a compaction boundary never
 * separates an assistant tool-call message from its tool results.</p>
 */
@Slf4j
public class DefaultContextManager implements ContextManager {

    private static final int MAX_CONSECUTIVE_SUMMARY_FAILURES = 3;

    private final ModelClient modelClient;
    private final String summaryModel;
    private final long contextWindowTokens;
    private final long reservedOutputTokens;
    private final int keepRecentMessages;
    private final boolean summaryEnabled;
    private final int minElisionChars;

    private int consecutiveSummaryFailures = 0;

    @Builder
    public DefaultContextManager(ModelClient modelClient, String summaryModel,
                                 Long contextWindowTokens, Long reservedOutputTokens,
                                 Integer keepRecentMessages, Boolean summaryEnabled,
                                 Integer minElisionChars) {
        this.modelClient = modelClient;
        this.summaryModel = summaryModel;
        this.contextWindowTokens = contextWindowTokens != null ? contextWindowTokens : 60_000;
        this.reservedOutputTokens = reservedOutputTokens != null ? reservedOutputTokens : 8_000;
        this.keepRecentMessages = keepRecentMessages != null ? keepRecentMessages : 6;
        this.summaryEnabled = summaryEnabled == null ? modelClient != null : summaryEnabled;
        this.minElisionChars = minElisionChars != null ? minElisionChars : 2_000;

        if (this.contextWindowTokens <= this.reservedOutputTokens) {
            throw new IllegalArgumentException("contextWindowTokens must exceed reservedOutputTokens");
        }
        if (this.keepRecentMessages < 2) {
            throw new IllegalArgumentException("keepRecentMessages must be >= 2");
        }
    }

    long softThreshold() {
        // Micro-compaction fires at 70% of the usable window
        return (contextWindowTokens - reservedOutputTokens) * 7 / 10;
    }

    long hardThreshold() {
        return contextWindowTokens - reservedOutputTokens;
    }

    @Override
    public void manageBeforeTurn(LoopState state) {
        long estimated = TokenEstimator.estimateTokens(state.getMessages());
        if (estimated <= softThreshold()) {
            return;
        }
        microCompact(state);

        estimated = TokenEstimator.estimateTokens(state.getMessages());
        if (estimated > hardThreshold()) {
            summaryCompact(state);
        }
    }

    @Override
    public boolean reactiveCompact(LoopState state) {
        int before = contentSize(state);
        microCompact(state);
        if (contentSize(state) < before) {
            return true;
        }
        return summaryCompact(state);
    }

    /**
     * Replace contents of tool-result messages outside the keep-window with a
     * placeholder. Pairing stays intact: the message (role=tool, toolCallId)
     * remains in place.
     */
    void microCompact(LoopState state) {
        List<ModelMessage> messages = state.getMessages();
        int keepFrom = Math.max(0, messages.size() - keepRecentMessages);

        // Cache-awareness (plan, D5): editing ANY old message invalidates the provider's
        // prefix cache for everything after it. A one-time invalidation is accepted by
        // design, but only when the savings amortize it — measure first, then apply.
        long potentialSavings = 0;
        for (int i = 0; i < keepFrom; i++) {
            ModelMessage message = messages.get(i);
            if (isElidableToolResult(message)) {
                potentialSavings += message.getContent().length();
            }
        }
        if (potentialSavings < minElisionChars) {
            log.debug("Micro-compaction skipped: {} chars of savings below threshold {} — "
                    + "not worth a prefix-cache invalidation", potentialSavings, minElisionChars);
            return;
        }

        int elided = 0;
        for (int i = 0; i < keepFrom; i++) {
            ModelMessage message = messages.get(i);
            if (isElidableToolResult(message)) {
                message.setContent("[tool result elided: " + message.getContent().length()
                        + " chars, tool_call_id=" + message.getToolCallId() + "]");
                elided++;
            }
        }
        if (elided > 0) {
            log.debug("Micro-compaction elided {} old tool results ({} chars)", elided, potentialSavings);
        }
    }

    private static boolean isElidableToolResult(ModelMessage message) {
        return message.getRole() == Role.tool && message.getContent() != null
                && message.getContent().length() > 80 && !isElided(message.getContent());
    }

    private static boolean isElided(String content) {
        return content.startsWith("[tool result elided:");
    }

    /**
     * Summarize everything except the system head and the keep-window into one
     * summary message via the compact prompt. On repeated failures the manager
     * trips its own circuit breaker and degrades to micro-compaction only.
     */
    boolean summaryCompact(LoopState state) {
        if (!summaryEnabled || modelClient == null
                || consecutiveSummaryFailures >= MAX_CONSECUTIVE_SUMMARY_FAILURES) {
            return false;
        }

        List<ModelMessage> messages = state.getMessages();

        // Head: leading system messages are always preserved as-is.
        int head = 0;
        while (head < messages.size() && messages.get(head).getRole() == Role.system) {
            head++;
        }

        int cut = messages.size() - keepRecentMessages;
        // Never cut inside a tool batch: move left past tool results so the kept
        // suffix starts at a user / assistant boundary.
        while (cut > head && messages.get(cut).getRole() == Role.tool) {
            cut--;
        }
        if (cut <= head + 1) {
            return false; // nothing meaningful to summarize
        }

        List<ModelMessage> toSummarize = messages.subList(head, cut);
        String conversation = renderConversation(toSummarize);
        String promptBody = AgentPrompts.load(AgentPrompts.COMPACT_PROMPT_ID,
                Map.of("conversation", conversation));

        try {
            ModelTextRequest request = ModelTextRequest.builder()
                    .model(summaryModel)
                    .temperature(0.1)
                    .messages(List.of(ModelContentMessage.create(Role.user, promptBody)))
                    .build();
            ModelTextResponse response = modelClient.textToText(request);
            String summary = response != null ? response.getResponse() : null;
            if (summary == null || summary.isBlank()) {
                throw new IllegalStateException("empty summary");
            }

            List<ModelMessage> compacted = new ArrayList<>(messages.subList(0, head));
            compacted.add(ModelMessage.user("[CONTEXT SUMMARY — earlier conversation was compacted]\n" + summary));
            compacted.addAll(new ArrayList<>(messages.subList(cut, messages.size())));
            state.setMessages(compacted);

            consecutiveSummaryFailures = 0;
            log.info("Summary compaction: {} messages -> {} (summary {} chars)",
                    messages.size(), compacted.size(), summary.length());
            return true;
        } catch (Exception e) {
            consecutiveSummaryFailures++;
            log.warn("Summary compaction failed ({}/{})", consecutiveSummaryFailures,
                    MAX_CONSECUTIVE_SUMMARY_FAILURES, e);
            return false;
        }
    }

    private static String renderConversation(List<ModelMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ModelMessage message : messages) {
            sb.append(message.getRole()).append(": ");
            if (message.getContent() != null) {
                sb.append(message.getContent());
            }
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                sb.append(" [requested tools: ");
                message.getToolCalls().forEach(tc -> {
                    if (tc.getFunction() != null) {
                        sb.append(tc.getFunction().getName()).append(' ');
                    }
                });
                sb.append(']');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static int contentSize(LoopState state) {
        int size = 0;
        for (ModelMessage message : state.getMessages()) {
            if (message.getContent() != null) {
                size += message.getContent().length();
            }
        }
        return size;
    }
}
