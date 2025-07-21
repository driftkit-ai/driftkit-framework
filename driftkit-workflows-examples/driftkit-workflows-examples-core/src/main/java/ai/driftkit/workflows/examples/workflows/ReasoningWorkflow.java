package ai.driftkit.workflows.examples.workflows;

import ai.driftkit.clients.openai.client.OpenAIModelClient;
import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.MessageTask;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.client.ModelImageResponse;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.clients.core.ModelClientFactory;
import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.VaultConfig;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.workflows.core.domain.*;
import ai.driftkit.workflows.spring.ModelWorkflow;
import ai.driftkit.workflows.spring.ModelRequestParams;
import ai.driftkit.workflows.spring.service.ModelRequestService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

@Slf4j
public class ReasoningWorkflow extends ModelWorkflow<StartEvent, JsonNode> {
    public static final String REASONING_METHOD = "reasoning";
    public static final String CHECK_RESULT_METHOD = "check_result";
    public static final String REPLAY_RESPONSE_AFTER_CHECK_METHOD = "replay_response_after_check";

    public static final String NEXT_ACTION = "next_action";
    public static final String RESULT = "result";
    public static final String CONTEXT = "context";
    public static final String FINAL_STEP = "finalStep";
    public static final String CHECK_STEP = "checkStep";
    public static final String CONTENT = "content";
    public static final String NEXT_STEP = "nextStep";
    public static final String FINAL_ANSWER = "final_answer";
    public static final String CONTINUE = "continue";
    public static final String ERRORS = "errors";


    private final VaultConfig modelConfig;

    public ReasoningWorkflow(EtlConfig config, PromptService promptService, ModelRequestService modelRequestService) throws IOException {
        super(ModelClientFactory.fromConfig(config.getModelConfig(OpenAIModelClient.OPENAI_PREFIX).orElseThrow()),
             modelRequestService, 
             promptService);
        this.modelConfig = config.getModelConfig(OpenAIModelClient.OPENAI_PREFIX).orElseThrow();

        for (String method : new String[]{
                REASONING_METHOD,
                CHECK_RESULT_METHOD,
                REPLAY_RESPONSE_AFTER_CHECK_METHOD
        }) {
            promptService.createIfNotExists(
                    method,
                    IOUtils.resourceToString("/prompts/dictionary/reasoning/%s.prompt".formatted(method), Charset.defaultCharset()),
                    null,
                    true,
                    Language.GENERAL
            );
        }

        promptService.createIfNotExists(
                PromptService.DEFAULT_STARTING_PROMPT,
                PromptService.STARTING_PROMPT,
                null,
                false,
                Language.GENERAL
        );
    }

    @Override
    public Class<JsonNode> getOutputType() {
        return JsonNode.class;
    }

    @Step(name = "start")
    @StepInfo(description = "Starting step of the workflow")
    public DataEvent<JsonNode> start(StartEvent startEvent, WorkflowContext workflowContext) throws Exception {
        String query;
        Map<String, Object> variables = Collections.emptyMap();
        Language language = Language.GENERAL;

        if (startEvent instanceof StartQueryEvent) {
            query = ((StartQueryEvent) startEvent).getQuery();
        } else if (startEvent instanceof LLMRequestEvent llmRequestEvent) {
            MessageTask task = llmRequestEvent.getTask();
            query = task.getMessage();

            if (task.getVariables() != null) {
                variables = task.getVariables();
            }
            language = Optional.ofNullable(task.getLanguage()).orElse(Language.GENERAL);
            
            // Store the current MessageTask in the workflow context to access logprobs later
            workflowContext.put("currentMessage", task);
        } else {
            throw new IllegalArgumentException("Unexpected event type: " + startEvent.getClass());
        }

        log.info("Executing start step with query: {} {}", query, variables);

        if (language != Language.GENERAL) {
            Prompt check = promptService.getCurrentPromptOrThrow(PromptService.DEFAULT_STARTING_PROMPT, Language.GENERAL);
            query = check.applyVariables(Map.of(
                    "query", query,
                    "language", language.name()
            ));
        }

        Prompt prompt = promptService.getCurrentPromptOrThrow(REASONING_METHOD, language);

        JsonNode response = processReasoningQuery(query + " " + prompt.getMessage(), variables, workflowContext);

        return new DataEvent<>(response, NEXT_STEP);
    }

    @Step(name = NEXT_STEP, invocationLimit = 5)
    @StepInfo(description = "Processes the next step based on the 'next_action'")
    public DataEvent<JsonNode> nextStep(DataEvent<JsonNode> event, WorkflowContext workflowContext) throws Exception {
        JsonNode result = event.getResult();
        log.info("Executing nextStep with event: {}", result);

        workflowContext.add(CONTEXT, result);

        if (isResponseBeforeFinal(result)) {
            return new DataEvent<>(result, CHECK_STEP);
        } else if (isContinue(result)) {
            // Proceed to the next reasoning step. Do not repeat the previous response.
            JsonNode resultContinue = processReasoningQuery(CONTINUE, Collections.emptyMap(), workflowContext);

            return new DataEvent<>(resultContinue, NEXT_STEP);
        } else if (!result.has(NEXT_ACTION)) {
            if (isUnformattedResult(result)) {
                return new DataEvent<>(result, CHECK_STEP);
            }

            JsonNode resultContinue = processReasoningQuery(CONTINUE, Collections.emptyMap(), workflowContext);

            return new DataEvent<>(resultContinue, NEXT_STEP);
        } else if (isFinal(result)) {
            JsonNode resultFinal = processReasoningQuery(FINAL_ANSWER, Collections.emptyMap(), workflowContext);

            workflowContext.add(CONTEXT, resultFinal);

            return new DataEvent<>(resultFinal, CHECK_STEP);
        } else if (isCompleted(result)) {
            return new DataEvent<>(result, FINAL_STEP);
        }

        throw new RuntimeException("Unexpected step in result [" + result + "]");
    }

    @Step(name = "replayStep", invocationLimit = 5)
    @StepInfo(description = "Replay result according to check result")
    public WorkflowEvent replayStep(DataEvent<JsonNode> event, WorkflowContext workflowContext) throws Exception {
        JsonNode lastMsg = workflowContext.getLastContext(ERRORS);

        log.info("Executing replayStep with event: {} {}", event.getResult(), lastMsg);

        Prompt replay = promptService.getCurrentPromptOrThrow(REPLAY_RESPONSE_AFTER_CHECK_METHOD, Language.GENERAL);

        String replayMsg = replay.applyVariables(Map.of(
                ERRORS, lastMsg == null ?
                        "IMPORTANT!: Reconsider the initial (first request) because the result is not correct. It doesn't follow the required json structure." :
                        String.valueOf(lastMsg.get("reason"))
        ));

        JsonNode result = processReasoningQuery(replayMsg, Collections.emptyMap(), workflowContext);

        if (isContinue(result)) {
            return new DataEvent<>(result, NEXT_STEP);
        }

        workflowContext.add(CONTEXT, result);

        if (isUnformattedResult(result)) {
            return new DataEvent<>(result, CHECK_STEP);
        }

        return new DataEvent<>(result, "replayStep");
    }

    @Step(name = CHECK_STEP, invocationLimit = 5, onInvocationsLimit = OnInvocationsLimit.STOP)
    @StepInfo(description = "Check step of the final result")
    public WorkflowEvent checkStep(DataEvent<JsonNode> event, WorkflowContext workflowContext) throws Exception {
        JsonNode result = event.getResult();

        log.info("Executing checkStep with event: {}", result);

        if (isContinue(result)) {
            return new DataEvent<>(result, NEXT_STEP);
        }

        Prompt check = promptService.getCurrentPromptOrThrow(CHECK_RESULT_METHOD, Language.GENERAL);

        ModelTextResponse modelTextResponse = sendQuery(check.getMessage(), Collections.emptyMap(), workflowContext, false);

        String finalResult = result.toString();

        JsonNode checkResult = modelTextResponse.getResponseJson();

        workflowContext.add(CONTEXT, checkResult);

        if (!checkResult.has(RESULT)) {
            log.warn("[reasoning] Check failed result is {} for {}", checkResult, workflowContext.getContext());
            return StopEvent.ofJson(finalResult);
        }

        boolean checkStatus = checkResult.get(RESULT).asBoolean();

        if (checkStatus) {
            return new DataEvent<>(result, FINAL_STEP);
        }

        workflowContext.add(ERRORS, checkResult);
        return new DataEvent<>(result, "replayStep");
    }

    @Step(name = FINAL_STEP)
    @StepInfo(description = "Final step of the workflow")
    public StopEvent<String> finalStep(DataEvent<JsonNode> event, WorkflowContext workflowContext) throws JsonProcessingException {
        log.info("Executing finalStep with event: {}", event.getResult());

        JsonNode resultNode = event.getResult();
        String finalResult = resultNode.toString();

        return StopEvent.ofJson(finalResult);
    }

    @NotNull
    private JsonNode processReasoningQuery(String query, Map<String, Object> variables, WorkflowContext workflowContext) throws Exception {
        ModelTextResponse modelTextResponse = sendQuery(query, variables, workflowContext, true);
        return modelTextResponse.getResponseJson();
    }

    @SneakyThrows
    private ModelTextResponse sendQuery(String query, Map<String, Object> variables, WorkflowContext workflowContext, boolean reasoning) {
        // Convert context objects to ModelContentMessages for the conversation history
        List<ModelImageResponse.ModelContentMessage> contextMessages = workflowContext.getOrDefault(CONTEXT, Collections.emptyList())
                .stream()
                .map(e -> {
                    JsonNode nextAction = e instanceof JsonNode node ? node.get(NEXT_ACTION) : null;

                    if (nextAction == null) {
                        return ModelImageResponse.ModelContentMessage.create(Role.user, e.toString());
                    } else {
                        return ModelImageResponse.ModelContentMessage.create(Role.assistant, e.toString());
                    }
                })
                .toList();

        List<ModelImageResponse.ModelContentMessage> messages = new ArrayList<>();

        if (!reasoning) {
            Prompt prompt = promptService.getCurrentPromptOrThrow(REASONING_METHOD, Language.GENERAL);
            messages.add(ModelImageResponse.ModelContentMessage.create(Role.system, prompt.getMessage()));
        }

        messages.addAll(contextMessages);
        
        // Add the query to the context for future reference
        workflowContext.add(CONTEXT, query);
        
        // Use the sendPromptTextWithHistory method from ModelWorkflow which will handle all the details
        ModelTextResponse response = sendPromptTextWithHistory(
            ModelRequestParams.create()
                .setPromptText(query)
                .setContextMessages(messages)
                .setVariables(variables),
            workflowContext);

        return response;
    }

    private static boolean isUnformattedResult(JsonNode result) {
        return result.get(CONTENT) == null && !result.has(NEXT_ACTION);
    }

    private static boolean isResponseBeforeFinal(JsonNode result) {
        JsonNode na = result.get(NEXT_ACTION);
        return result.get("response") != null && (na == null || !CONTINUE.equals(na.asText()));
    }

    private static boolean isContinue(JsonNode result) {
        JsonNode na = result.get(NEXT_ACTION);
        return na != null && CONTINUE.equals(na.asText());
    }

    private static boolean isCompleted(JsonNode result) {
        JsonNode na = result.get(NEXT_ACTION);
        return na != null && "none".equals(na.asText());
    }

    private static boolean isFinal(JsonNode result) {
        JsonNode na = result.get(NEXT_ACTION);
        return na != null && FINAL_ANSWER.equals(na.asText());
    }
}