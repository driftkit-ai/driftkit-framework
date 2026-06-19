package ai.driftkit.workflow.engine.agent;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.domain.client.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelContentMessage.ModelContentElement;
import ai.driftkit.common.domain.client.ModelImageRequest;
import ai.driftkit.common.domain.client.ModelImageResponse;
import ai.driftkit.common.domain.client.ModelMessage;
import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.domain.client.ResponseFormat;
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.common.domain.streaming.StreamingCallback;
import ai.driftkit.common.domain.streaming.StreamingResponse;
import ai.driftkit.common.tools.ToolCall;
import ai.driftkit.context.core.registry.PromptServiceRegistry;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.context.core.util.PromptUtils;
import ai.driftkit.workflow.engine.agent.loop.AgentLoop;
import ai.driftkit.workflow.engine.agent.loop.AgentLoopResult;
import ai.driftkit.workflow.engine.agent.loop.ApprovalDecision;
import ai.driftkit.workflow.engine.agent.loop.AgenticOptions;
import ai.driftkit.workflow.engine.agent.loop.LoopState;
import ai.driftkit.workflow.engine.agent.loop.ToolCallExecutor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Single internal execution path of {@link LLMAgent} (plan, D2): every
 * text-family {@code execute*} method goes through the same sequence —
 * resolve prompt → build context → build request → invoke (single shot or
 * agentic loop) → trace → persist history → hand the outcome back for mapping.
 *
 * <p>This removes the per-method drift in history persistence that the legacy
 * 29 overloads accumulated: the pipeline persists the user message exactly once
 * (via {@link ConversationContext}) and the assistant text according to
 * {@link Persistence}.</p>
 */
@Slf4j
class AgentExecutionPipeline {

    private final LLMAgent agent;

    AgentExecutionPipeline(LLMAgent agent) {
        this.agent = agent;
    }

    enum Mode {SINGLE_SHOT, AGENTIC}

    enum Persistence {
        /** Persist non-blank assistant text (default for conversational methods). */
        ASSISTANT_TEXT,
        /** Do not persist an assistant message (tool-call extraction). */
        NONE
    }

    @Value
    @Builder
    static class ExecutionSpec {
        String userMessage;
        Map<String, Object> variables;
        String promptId;
        Language language;
        ResponseFormat responseFormat;
        boolean includeTools;
        Mode mode;
        Persistence persistence;
        String traceSuffix;
        AgenticOptions agenticOptions;
        /** Multimodal attachments appended to the user message of the request. */
        List<ModelContentElement.ImageData> media;
        /** Overrides the agent/prompt temperature (e.g. structured extraction = 0.1). */
        Double temperatureOverride;
        /** Invoke imageToText instead of textToText (multimodal requests). */
        boolean imageToText;
    }

    @Value
    static class Outcome {
        ConversationContext context;
        ModelTextRequest request;
        ModelTextResponse response;     // null in AGENTIC mode
        AgentLoopResult loopResult;     // null in SINGLE_SHOT mode
        String responseText;
        Prompt prompt;                  // resolved prompt, if promptId was given
    }

    /** Prompt-resolution result shared by the sync and streaming paths. */
    private record ResolvedInput(String message, String systemMessage, Prompt prompt) {
    }

    private ResolvedInput resolveInput(ExecutionSpec spec) {
        Prompt prompt = null;
        String message = spec.getUserMessage();
        String effectiveSystem = agent.getSystemMessage();

        if (spec.getPromptId() != null) {
            prompt = resolvePrompt(spec.getPromptId(), spec.getLanguage());
            message = PromptUtils.applyVariables(prompt.getMessage(), spec.getVariables());
            String promptSystem = prompt.getSystemMessage();
            if (StringUtils.isNotBlank(promptSystem)) {
                effectiveSystem = PromptUtils.applyVariables(promptSystem, spec.getVariables());
            }
        } else if (spec.getVariables() != null && !spec.getVariables().isEmpty()) {
            message = PromptUtils.applyVariables(message, spec.getVariables());
        }
        return new ResolvedInput(message, effectiveSystem, prompt);
    }

    private ConversationContext buildContext(ResolvedInput input, ExecutionSpec spec) {
        ConversationContext context = agent.createConversationContext();
        if (StringUtils.isNotBlank(input.systemMessage())) {
            context.addSystemMessage(input.systemMessage());
        }
        agent.addUserMessageToContext(context, input.message(), spec.getVariables());
        return context;
    }

    Outcome run(ExecutionSpec spec) {
        // 1. Resolve prompt / message / system message; 2. build context (persists user message once)
        ResolvedInput input = resolveInput(spec);
        ConversationContext context = buildContext(input, spec);
        Prompt prompt = input.prompt();

        // 3. Invoke
        Outcome outcome;
        if (spec.getMode() == Mode.AGENTIC) {
            outcome = invokeAgentic(spec, context, prompt);
        } else {
            outcome = invokeSingleShot(spec, context, prompt);
        }

        // 4. Persist assistant text per spec
        if (spec.getPersistence() != Persistence.NONE && StringUtils.isNotBlank(outcome.getResponseText())) {
            context.addAssistantMessage(outcome.getResponseText());
        }
        return outcome;
    }

    private Outcome invokeSingleShot(ExecutionSpec spec, ConversationContext context, Prompt prompt) {
        ModelTextRequest request = buildRequest(spec, context.getMessagesForRequest(), prompt);
        ModelTextResponse response = spec.isImageToText()
                ? agent.getModelClient().imageToText(request)
                : agent.getModelClient().textToText(request);
        trace(spec, request, response);
        return new Outcome(context, request, response, null, agent.extractResponseText(response), prompt);
    }

    /**
     * Streaming variant of the single-shot path: shares prompt resolution, context
     * building and request assembly with {@link #run}, persists the assembled
     * assistant text on completion and traces with a synthetic response.
     */
    CompletableFuture<String> runStreaming(ExecutionSpec spec, StreamingCallback<String> callback) {
        if (callback == null) {
            throw new IllegalArgumentException("Callback is required for streaming execution");
        }
        CompletableFuture<String> future = new CompletableFuture<>();

        // Same resolution as run(): prompt templates work for streaming too
        ResolvedInput input = resolveInput(spec);
        ConversationContext context = buildContext(input, spec);

        ModelTextRequest request = buildRequest(spec, context.getMessagesForRequest(), input.prompt());

        StreamingResponse<String> streamingResponse = agent.getModelClient().streamTextToText(request);
        StringBuilder fullResponse = new StringBuilder();

        streamingResponse.subscribe(new StreamingCallback<>() {
            @Override
            public void onNext(String item) {
                fullResponse.append(item);
                callback.onNext(item);
            }

            @Override
            public void onError(Throwable error) {
                log.error("Streaming error in LLMAgent", error);
                callback.onError(error);
                future.completeExceptionally(error);
            }

            @Override
            public void onComplete() {
                String finalResponse = fullResponse.toString();
                if (!finalResponse.isEmpty()) {
                    context.addAssistantMessage(finalResponse);
                }
                ModelTextResponse synthetic = ModelTextResponse.builder()
                        .choices(Collections.singletonList(ModelTextResponse.ResponseMessage.builder()
                                .message(ModelMessage.builder()
                                        .role(Role.assistant)
                                        .content(finalResponse)
                                        .build())
                                .build()))
                        .build();
                trace(spec, request, synthetic);
                callback.onComplete();
                future.complete(finalResponse);
            }
        });

        return future;
    }

    /**
     * Image generation path: shares variable substitution and tracing with the
     * pipeline; uses the agent's imageModel and the image API.
     */
    ModelImageResponse runImageGeneration(ExecutionSpec spec) {
        String prompt = spec.getUserMessage();
        if (spec.getVariables() != null && !spec.getVariables().isEmpty()) {
            prompt = PromptUtils.applyVariables(prompt, spec.getVariables());
        }
        ModelImageRequest request = ModelImageRequest.builder()
                .prompt(prompt)
                .model(agent.getImageModel())
                .build();
        ModelImageResponse response = agent.getModelClient().textToImage(request);

        RequestTracingProvider provider = agent.getTracingProvider();
        if (provider != null) {
            try {
                provider.traceImageRequest(request, response, agent.buildTraceContext(
                        spec.getTraceSuffix() != null ? spec.getTraceSuffix() : "IMAGE_GEN",
                        spec.getVariables(), spec.getPromptId(), null));
            } catch (Exception e) {
                log.warn("Tracing failed", e);
            }
        }
        return response;
    }

    private Outcome invokeAgentic(ExecutionSpec spec, ConversationContext context, Prompt prompt) {
        AgenticOptions options = spec.getAgenticOptions() != null
                ? spec.getAgenticOptions() : AgenticOptions.defaults();

        AgentLoop loop = buildLoop(spec, options);
        LoopState state = LoopState.builder().build();
        for (ModelMessage m : context.getMessagesForRequest()) {
            state.addMessage(m);
        }

        AgentLoopResult result = loop.run(state);
        return new Outcome(context, null, null, result, result.getText(), prompt);
    }

    AgentLoopResult resumeAgentic(LoopState state, ApprovalDecision decision,
                                  AgenticOptions options) {
        AgenticOptions effective = options != null ? options : AgenticOptions.defaults();
        ExecutionSpec spec = ExecutionSpec.builder()
                .mode(Mode.AGENTIC)
                .includeTools(true)
                .traceSuffix("AGENTIC")
                .agenticOptions(effective)
                .build();
        AgentLoopResult result = buildLoop(spec, effective).resume(state, decision);
        // Persist the final assistant text for conversational agents (plan, 8.2).
        // Append-only: no need to read the existing history just to write one message.
        if (StringUtils.isNotBlank(result.getText())) {
            ConversationContext context = ConversationContext.appendOnly(
                    agent.getChatStore(), agent.getWorkflowId(), agent.getChatId(), agent.getMemoryMode());
            if (agent.getMessageProperties() != null && !agent.getMessageProperties().isEmpty()) {
                context.setMessageProperties(agent.getMessageProperties());
            }
            context.addAssistantMessage(result.getText());
        }
        return result;
    }

    private AgentLoop buildLoop(ExecutionSpec spec, AgenticOptions options) {
        List<ModelClient.Tool> tools = agentTools();
        AgentLoop.RequestConfig requestConfig = AgentLoop.RequestConfig.builder()
                .model(agent.getEffectiveModel())
                .temperature(agent.getTemperature(null))
                .tools(tools)
                .reasoningEffort(agent.getReasoningEffort())
                .cachePolicy(agent.getCachePolicy())
                .build();

        ToolCallExecutor executor = new ToolCallExecutor(agent.getToolRegistry(),
                options.effectivePolicy().getMaxToolConcurrency());

        return AgentLoop.builder()
                .modelClient(agent.getModelClient())
                .toolExecutor(executor)
                .hooks(options.effectiveHooks())
                .contextManager(options.effectiveContextManager())
                .policy(options.effectivePolicy())
                .requestConfig(requestConfig)
                .turnListener((request, response, turn) -> trace(
                        ExecutionSpec.builder()
                                .traceSuffix("AGENTIC_TURN_" + turn)
                                .variables(spec.getVariables())
                                .promptId(spec.getPromptId())
                                .build(),
                        request, response))
                .build();
    }

    private ModelTextRequest buildRequest(ExecutionSpec spec, List<ModelMessage> contextMessages, Prompt prompt) {
        List<ModelContentMessage> messages = new ArrayList<>(contextMessages.stream()
                .map(AgentLoop::toContentMessage)
                .toList());

        // Multimodal: attach media to the LAST user message (no duplicate text message —
        // fixes the legacy double-user-message behavior of executeWithImages)
        if (CollectionUtils.isNotEmpty(spec.getMedia())) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                ModelContentMessage candidate = messages.get(i);
                if (candidate.getRole() == Role.user) {
                    List<ModelContentElement> withMedia = new ArrayList<>(candidate.getContent());
                    for (ModelContentElement.ImageData data : spec.getMedia()) {
                        withMedia.add(ModelContentElement.create(data.getImage(), data.getMimeType()));
                    }
                    candidate.setContent(withMedia);
                    break;
                }
            }
        }

        List<ModelClient.Tool> tools = spec.isIncludeTools() ? agentTools() : null;

        return ModelTextRequest.builder()
                .model(agent.getEffectiveModel())
                .temperature(spec.getTemperatureOverride() != null
                        ? spec.getTemperatureOverride() : agent.getTemperature(prompt))
                .messages(messages)
                .reasoningEffort(agent.getReasoningEffort())
                .cachePolicy(agent.getCachePolicy())
                .responseFormat(spec.getResponseFormat())
                .tools(CollectionUtils.isNotEmpty(tools) ? tools : null)
                .build();
    }

    private List<ModelClient.Tool> agentTools() {
        if (agent.getToolRegistry() == null) {
            return Collections.emptyList();
        }
        ModelClient.Tool[] tools = agent.getToolRegistry().getTools();
        return tools.length > 0 ? Arrays.asList(tools) : Collections.emptyList();
    }

    private void trace(ExecutionSpec spec, ModelTextRequest request, ModelTextResponse response) {
        RequestTracingProvider provider = agent.getTracingProvider();
        if (provider == null || request == null) {
            return;
        }
        try {
            RequestTracingProvider.RequestContext traceContext = agent.buildTraceContext(
                    spec.getTraceSuffix() != null ? spec.getTraceSuffix() : "TEXT",
                    spec.getVariables(), spec.getPromptId(), request.getMessages());
            if (spec.isImageToText()) {
                provider.traceImageToTextRequest(request, response, traceContext);
            } else {
                provider.traceTextRequest(request, response, traceContext);
            }
        } catch (Exception e) {
            log.warn("Tracing failed", e);
        }
    }

    private Prompt resolvePrompt(String promptId, Language language) {
        PromptService effective = agent.getPromptService() != null
                ? agent.getPromptService() : PromptServiceRegistry.getInstance();
        if (effective == null) {
            throw new IllegalStateException("PromptService not configured. "
                    + "Please ensure PromptService is available in your application context "
                    + "or register one via PromptServiceRegistry.register()");
        }
        Optional<Prompt> prompt = effective.getCurrentPrompt(promptId,
                language != null ? language : Language.GENERAL);
        if (prompt.isEmpty()) {
            throw new IllegalArgumentException("Prompt not found: " + promptId);
        }
        return prompt.get();
    }

    static List<ToolCall> toolCallsOf(ModelTextResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getChoices())) {
            return Collections.emptyList();
        }
        return response.getChoices().stream()
                .filter(choice -> choice.getMessage() != null)
                .map(choice -> choice.getMessage().getToolCalls())
                .filter(CollectionUtils::isNotEmpty)
                .flatMap(List::stream)
                .toList();
    }
}
