package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.domain.client.CachePolicy;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.domain.client.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelMessage;
import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.ModelTextRequest.ToolMode;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.workflow.engine.agent.ToolExecutionResult;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Multi-turn agentic loop (plan, D1+D4): model -> tools -> results back to the
 * model -> repeat, until the model finishes without tool calls or a limit fires.
 *
 * <p>The loop bound is finite by construction — a {@code for} loop over
 * {@link LoopPolicy#getMaxTurns()} (validated against
 * {@link LoopPolicy#HARD_CAP_TURNS}); {@code while(true)} is forbidden by review
 * rule. Every exit path returns an {@link AgentLoopResult} with a
 * {@link StopReason} and the accumulated state — limits are results, not
 * exceptions.</p>
 */
@Slf4j
public class AgentLoop {

    private final ModelClient modelClient;
    private final ToolCallExecutor toolExecutor;
    private final List<AgentHook> hooks;
    private final ContextManager contextManager;
    private final LoopPolicy policy;
    private final RequestConfig requestConfig;
    private final TurnListener turnListener;

    /** Static, per-run request parameters applied to every model call. */
    @Value
    @Builder
    public static class RequestConfig {
        String model;
        Double temperature;
        List<ModelClient.Tool> tools;
        ModelTextRequest.ReasoningEffort reasoningEffort;
        CachePolicy cachePolicy;
    }

    /** Per-turn observer; LLMAgent plugs request tracing in here. */
    public interface TurnListener {
        void onTurn(ModelTextRequest request, ModelTextResponse response, int turn);
    }

    @Builder
    public AgentLoop(ModelClient modelClient, ToolCallExecutor toolExecutor, List<AgentHook> hooks,
                     ContextManager contextManager, LoopPolicy policy, RequestConfig requestConfig,
                     TurnListener turnListener) {
        if (modelClient == null) {
            throw new IllegalArgumentException("modelClient is required");
        }
        if (toolExecutor == null) {
            throw new IllegalArgumentException("toolExecutor is required");
        }
        this.modelClient = modelClient;
        this.toolExecutor = toolExecutor;
        this.hooks = hooks != null ? List.copyOf(hooks) : List.of();
        this.contextManager = contextManager != null ? contextManager : ContextManager.NOOP;
        this.policy = policy != null ? policy : LoopPolicy.defaults();
        this.requestConfig = requestConfig != null ? requestConfig : RequestConfig.builder().build();
        this.turnListener = turnListener;
    }

    /**
     * Run the loop from the given state until completion or a limit.
     */
    public AgentLoopResult run(LoopState state) {
        if (state.isSuspended()) {
            throw new IllegalStateException("State is suspended awaiting approval — call resume(...) instead");
        }
        requireToolMessageSupport();
        return runInternal(state, new ArrayList<>());
    }

    /**
     * Fail fast (plan, 8.1): a client that silently drops tool results would make
     * the model lose every tool outcome — explicit error beats quiet degradation.
     */
    private void requireToolMessageSupport() {
        boolean hasTools = requestConfig.getTools() != null && !requestConfig.getTools().isEmpty();
        if (hasTools && !modelClient.supportsToolMessages()) {
            throw new IllegalStateException(
                    "ModelClient " + modelClient.getClass().getSimpleName()
                    + " does not support tool messages (toolCalls/toolCallId serialization). "
                    + "The agentic loop requires it; supported clients: DeepSeek, OpenAI, Claude, Gemini. "
                    + "Override supportsToolMessages() after implementing the mapping.");
        }
    }

    /**
     * Resume a loop previously suspended with {@link StopReason#PENDING_APPROVAL}:
     * apply the human decision to the pending ASK calls, execute the batch and
     * continue the remaining turns.
     */
    public AgentLoopResult resume(LoopState state, ApprovalDecision decision) {
        if (!state.isSuspended()) {
            throw new IllegalStateException("State is not suspended — nothing to resume");
        }
        List<ToolExecutionResult> collected = new ArrayList<>();

        List<ToolCallExecutor.ResolvedCall> resolved = new ArrayList<>();
        for (PendingToolCall pending : state.getPendingToolCalls()) {
            switch (pending.getBehavior()) {
                case ALLOW -> resolved.add(ToolCallExecutor.ResolvedCall.execute(pending.getCall()));
                case DENY -> resolved.add(ToolCallExecutor.ResolvedCall.deny(pending.getCall(), pending.getReason()));
                case ASK -> {
                    if (decision.approved()) {
                        resolved.add(ToolCallExecutor.ResolvedCall.execute(pending.getCall()));
                    } else {
                        String reason = decision.reason() != null ? decision.reason() : "rejected by user";
                        resolved.add(ToolCallExecutor.ResolvedCall.deny(pending.getCall(), reason));
                    }
                }
            }
        }
        state.setPendingToolCalls(null);
        state.setApprovalReason(null);

        executeBatch(state, resolved, collected);
        return runInternal(state, collected);
    }

    private AgentLoopResult runInternal(LoopState state, List<ToolExecutionResult> collected) {
        Instant deadline = policy.getMaxDuration() != null ? Instant.now().plus(policy.getMaxDuration()) : null;

        // Finite by construction: bounded for-loop, no while(true).
        for (int turn = state.getTurnCount() + 1; turn <= policy.getMaxTurns(); turn++) {
            if (deadline != null && Instant.now().isAfter(deadline)) {
                return finish(state, collected, StopReason.DEADLINE, null);
            }
            if (policy.getMaxTotalTokens() > 0
                    && state.getUsage().getTotalTokens() >= policy.getMaxTotalTokens()) {
                return finish(state, collected, StopReason.BUDGET, null);
            }

            contextManager.manageBeforeTurn(state);

            ModelTextResponse response;
            try {
                response = callModelWithRecovery(state, deadline);
            } catch (CircuitBreakerOpenException e) {
                return finish(state, collected, StopReason.CIRCUIT_BREAKER, e.getMessage());
            } catch (ContextOverflowException e) {
                return finish(state, collected, StopReason.CONTEXT_OVERFLOW, e.getMessage());
            } catch (DeadlineExceededException e) {
                return finish(state, collected, StopReason.DEADLINE, e.getMessage());
            }

            state.setTurnCount(turn);
            state.getUsage().add(response);

            String assistantText = extractText(response);
            List<ToolCall> toolCalls = extractToolCalls(response);

            if (toolCalls.isEmpty()) {
                state.addMessage(ModelMessage.assistant(assistantText));
                return finish(state, collected, StopReason.END_TURN, null);
            }

            // Echo of the assistant turn that requested tools — required by providers.
            state.addMessage(ModelMessage.assistantToolCalls(assistantText, toolCalls));

            // Resolve every call through hooks BEFORE executing anything: a batch
            // with an ASK suspends with zero side effects.
            List<ToolCallExecutor.ResolvedCall> resolved = new ArrayList<>();
            List<PendingToolCall> pending = new ArrayList<>();
            String askReason = null;

            for (ToolCall call : toolCalls) {
                HookDecision decision = evaluateHooks(call, state);
                ToolCall effective = applyUpdatedArguments(call, decision);
                pending.add(new PendingToolCall(effective, decision.behavior(), decision.reason()));
                if (decision.isAsk() && askReason == null) {
                    askReason = decision.reason();
                }
                switch (decision.behavior()) {
                    case ALLOW -> resolved.add(ToolCallExecutor.ResolvedCall.execute(effective));
                    case DENY -> resolved.add(ToolCallExecutor.ResolvedCall.deny(effective, decision.reason()));
                    case ASK -> { /* handled below via suspension */ }
                }
            }

            if (askReason != null) {
                state.setPendingToolCalls(pending);
                state.setApprovalReason(askReason);
                return finish(state, collected, StopReason.PENDING_APPROVAL, null);
            }

            executeBatch(state, resolved, collected);
        }

        return finish(state, collected, StopReason.MAX_TURNS, null);
    }

    private void executeBatch(LoopState state, List<ToolCallExecutor.ResolvedCall> resolved,
                              List<ToolExecutionResult> collected) {
        List<ToolCallExecutor.ExecutedCall> executed = toolExecutor.executeAll(resolved);
        for (ToolCallExecutor.ExecutedCall call : executed) {
            collected.add(call.getResult());
            state.addMessage(ModelMessage.tool(call.getRenderedContent(), call.getCall().getId()));
            for (AgentHook hook : hooks) {
                try {
                    hook.postToolUse(call.getCall(), call.getResult(), state);
                } catch (Exception e) {
                    log.warn("postToolUse hook failed", e);
                }
            }
        }
    }

    private HookDecision evaluateHooks(ToolCall call, LoopState state) {
        HookDecision effective = HookDecision.allow();
        for (AgentHook hook : hooks) {
            HookDecision decision;
            try {
                decision = hook.preToolUse(call, state);
            } catch (Exception e) {
                log.warn("preToolUse hook failed — treating as DENY", e);
                return HookDecision.deny("hook failure: " + e.getMessage());
            }
            if (decision == null || decision.isAllow()) {
                if (decision != null && decision.updatedArguments() != null) {
                    effective = HookDecision.allow(merge(effective.updatedArguments(), decision.updatedArguments()));
                }
                continue;
            }
            return decision; // first non-ALLOW wins
        }
        return effective;
    }

    private static Map<String, JsonNode> merge(Map<String, JsonNode> base, Map<String, JsonNode> overlay) {
        if (base == null) {
            return overlay;
        }
        var merged = new HashMap<>(base);
        merged.putAll(overlay);
        return merged;
    }

    private static ToolCall applyUpdatedArguments(ToolCall call, HookDecision decision) {
        if (decision.updatedArguments() == null || call.getFunction() == null) {
            return call;
        }
        Map<String, JsonNode> arguments = call.getFunction().getArguments() != null
                ? new HashMap<>(call.getFunction().getArguments())
                : new HashMap<>();
        arguments.putAll(decision.updatedArguments());
        return ToolCall.builder()
                .id(call.getId())
                .type(call.getType())
                .function(ToolCall.FunctionCall.builder()
                        .name(call.getFunction().getName())
                        .arguments(arguments)
                        .build())
                .build();
    }

    // ---- Model call with recovery (D4) ----

    private ModelTextResponse callModelWithRecovery(LoopState state, Instant deadline) {
        while (state.getConsecutiveFailures() < policy.getMaxConsecutiveFailures()) {
            // Re-check the deadline on every attempt: sleepBackoff caps the sleep to the
            // remaining time, so without this check a retry could fire past the deadline.
            if (deadline != null && Instant.now().isAfter(deadline)) {
                throw new DeadlineExceededException("Deadline exceeded during model-call retries");
            }
            ModelTextRequest request = buildRequest(state);
            for (AgentHook hook : hooks) {
                try {
                    hook.preModelCall(request, state);
                } catch (Exception e) {
                    log.warn("preModelCall hook failed", e);
                }
            }
            try {
                ModelTextResponse response = modelClient.textToText(request);
                if (turnListener != null) {
                    try {
                        turnListener.onTurn(request, response, state.getTurnCount() + 1);
                    } catch (Exception e) {
                        log.warn("turn listener failed", e);
                    }
                }
                state.setConsecutiveFailures(0);
                return response;
            } catch (Exception e) {
                if (isContextOverflow(e) && policy.isReactiveCompactEnabled()
                        && !state.isAttemptedReactiveCompact()) {
                    state.setAttemptedReactiveCompact(true);
                    // Withholding pattern: a recoverable error is not surfaced
                    // until the automatic recovery attempt has been tried.
                    if (contextManager.reactiveCompact(state)) {
                        log.info("Reactive compaction applied after context overflow; retrying");
                        continue;
                    }
                    throw new ContextOverflowException(e.getMessage());
                }
                if (isContextOverflow(e)) {
                    throw new ContextOverflowException(e.getMessage());
                }

                state.setConsecutiveFailures(state.getConsecutiveFailures() + 1);
                log.warn("Model call failed ({}/{})", state.getConsecutiveFailures(),
                        policy.getMaxConsecutiveFailures(), e);
                if (state.getConsecutiveFailures() >= policy.getMaxConsecutiveFailures()) {
                    break;
                }
                sleepBackoff(state.getConsecutiveFailures(), deadline);
            }
        }
        throw new CircuitBreakerOpenException(
                "Model call failed %d times in a row".formatted(policy.getMaxConsecutiveFailures()));
    }

    private void sleepBackoff(int failureCount, Instant deadline) {
        // Cap the exponent: even with a misconfigured failure limit the delay never
        // exceeds 64x base, and the shift cannot overflow.
        long delayMs = policy.getRetryBaseDelay().toMillis() * (1L << Math.min(failureCount - 1, 6));
        if (deadline != null) {
            long remaining = Duration.between(Instant.now(), deadline).toMillis();
            delayMs = Math.min(delayMs, Math.max(0, remaining));
        }
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isContextOverflow(Exception e) {
        String message = String.valueOf(e.getMessage()).toLowerCase();
        return message.contains("context length") || message.contains("context_length")
                || message.contains("maximum context") || message.contains("prompt is too long")
                || message.contains("too many tokens") || message.contains("413");
    }

    private ModelTextRequest buildRequest(LoopState state) {
        List<ModelContentMessage> messages = state.getMessages().stream()
                .map(AgentLoop::toContentMessage)
                .toList();
        boolean hasTools = requestConfig.getTools() != null && !requestConfig.getTools().isEmpty();
        return ModelTextRequest.builder()
                .model(requestConfig.getModel())
                .temperature(requestConfig.getTemperature())
                .messages(messages)
                .reasoningEffort(requestConfig.getReasoningEffort())
                .cachePolicy(requestConfig.getCachePolicy())
                .tools(hasTools ? requestConfig.getTools() : null)
                .toolMode(hasTools ? ToolMode.auto : null)
                .build();
    }

    public static ModelContentMessage toContentMessage(ModelMessage message) {
        return ModelContentMessage.builder()
                .role(message.getRole())
                .content(List.of(ModelContentMessage.ModelContentElement.create(
                        message.getContent() != null ? message.getContent() : "")))
                .toolCalls(message.getToolCalls())
                .toolCallId(message.getToolCallId())
                .build();
    }

    private static String extractText(ModelTextResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }
        ModelMessage message = response.getChoices().getFirst().getMessage();
        return message != null ? message.getContent() : null;
    }

    private static List<ToolCall> extractToolCalls(ModelTextResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return List.of();
        }
        ModelMessage message = response.getChoices().getFirst().getMessage();
        if (message == null || message.getToolCalls() == null) {
            return List.of();
        }
        return message.getToolCalls();
    }

    private AgentLoopResult finish(LoopState state, List<ToolExecutionResult> collected,
                                   StopReason reason, String error) {
        AgentLoopResult result = AgentLoopResult.builder()
                .stopReason(reason)
                .text(state.lastAssistantText())
                .state(state)
                .toolResults(List.copyOf(collected))
                .turns(state.getTurnCount())
                .error(error)
                .build();
        for (AgentHook hook : hooks) {
            try {
                hook.onLoopFinish(result);
            } catch (Exception e) {
                log.warn("onLoopFinish hook failed", e);
            }
        }
        return result;
    }

    static class CircuitBreakerOpenException extends RuntimeException {
        CircuitBreakerOpenException(String message) {
            super(message);
        }
    }

    static class ContextOverflowException extends RuntimeException {
        ContextOverflowException(String message) {
            super(message);
        }
    }

    static class DeadlineExceededException extends RuntimeException {
        DeadlineExceededException(String message) {
            super(message);
        }
    }
}
