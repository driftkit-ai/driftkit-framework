package ai.driftkit.workflow.engine.agent.loop;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.domain.client.ModelMessage;
import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.common.tools.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Scripted ModelClient for loop tests: each call consumes the next step.
 * A step either returns a response or throws. All requests are recorded.
 */
class MockModelClient extends ModelClient<Void> {

    final Queue<Function<ModelTextRequest, ModelTextResponse>> steps = new ConcurrentLinkedQueue<>();
    final List<ModelTextRequest> requests = new CopyOnWriteArrayList<>();

    MockModelClient() {
        super("mock-model", null, 0.0, null, null, null, null, null, null,
                null, null, null, null, true, false, null, null);
    }

    MockModelClient enqueue(Function<ModelTextRequest, ModelTextResponse> step) {
        steps.add(step);
        return this;
    }

    MockModelClient enqueueText(String text) {
        return enqueue(req -> textResponse(text));
    }

    MockModelClient enqueueToolCalls(String content, ToolCall... calls) {
        return enqueue(req -> toolCallResponse(content, calls));
    }

    MockModelClient enqueueFailure(String message) {
        return enqueue(req -> {
            throw new RuntimeException(message);
        });
    }

    @Override
    public Set<Capability> getCapabilities() {
        return Set.of();
    }

    @Override
    public boolean supportsToolMessages() {
        return true;
    }

    @Override
    public ModelTextResponse textToText(ModelTextRequest prompt) {
        requests.add(prompt);
        Function<ModelTextRequest, ModelTextResponse> step = steps.poll();
        if (step == null) {
            throw new IllegalStateException("MockModelClient: no scripted step left for call #" + requests.size());
        }
        return step.apply(prompt);
    }

    // ---- response factories ----

    static ModelTextResponse textResponse(String text) {
        return response(ModelMessage.assistant(text), 10, 5);
    }

    static ModelTextResponse toolCallResponse(String content, ToolCall... calls) {
        ModelMessage message = ModelMessage.builder()
                .role(Role.assistant)
                .content(content)
                .toolCalls(List.of(calls))
                .build();
        return response(message, 10, 5);
    }

    static ModelTextResponse response(ModelMessage message, int promptTokens, int completionTokens) {
        return ModelTextResponse.builder()
                .model("mock-model")
                .choices(List.of(ModelTextResponse.ResponseMessage.builder()
                        .index(0)
                        .message(message)
                        .finishReason("stop")
                        .build()))
                .usage(new ModelTextResponse.Usage(promptTokens, completionTokens, promptTokens + completionTokens))
                .build();
    }

    static ToolCall toolCall(String id, String function, Map<String, Object> args) {
        Map<String, JsonNode> nodes = new LinkedHashMap<>();
        args.forEach((k, v) -> {
            if (v instanceof Integer i) {
                nodes.put(k, JsonNodeFactory.instance.numberNode(i));
            } else if (v instanceof Boolean b) {
                nodes.put(k, JsonNodeFactory.instance.booleanNode(b));
            } else {
                nodes.put(k, JsonNodeFactory.instance.textNode(String.valueOf(v)));
            }
        });
        return ToolCall.builder()
                .id(id)
                .type("function")
                .function(ToolCall.FunctionCall.builder().name(function).arguments(nodes).build())
                .build();
    }

    int remainingSteps() {
        return steps.size();
    }

    List<ModelMessage> lastRequestMessagesAsModelMessages() {
        ModelTextRequest last = requests.get(requests.size() - 1);
        List<ModelMessage> result = new ArrayList<>();
        last.getMessages().forEach(m -> result.add(ModelMessage.builder()
                .role(m.getRole())
                .content(m.getContent() != null && !m.getContent().isEmpty()
                        ? m.getContent().get(0).getText() : null)
                .toolCalls(m.getToolCalls())
                .toolCallId(m.getToolCallId())
                .build()));
        return result;
    }
}
