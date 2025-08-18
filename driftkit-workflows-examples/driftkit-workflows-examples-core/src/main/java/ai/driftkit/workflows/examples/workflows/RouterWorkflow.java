package ai.driftkit.workflows.examples.workflows;

import ai.driftkit.clients.core.ModelClientFactory;
import ai.driftkit.clients.openai.client.OpenAIModelClient;
import ai.driftkit.common.domain.Language;
import ai.driftkit.workflows.core.chat.Message;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.VaultConfig;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.vector.core.domain.DocumentsResult;
import ai.driftkit.vector.spring.domain.Index;
import ai.driftkit.workflows.core.domain.*;
import ai.driftkit.workflows.spring.ModelWorkflow;
import ai.driftkit.workflows.spring.ModelRequestParams;
import ai.driftkit.workflows.examples.workflows.RAGSearchWorkflow.VectorStoreStartEvent;
import ai.driftkit.workflows.examples.workflows.RouterWorkflow.RouterResult;
import ai.driftkit.workflows.examples.workflows.RouterWorkflow.RouterStartEvent;
import ai.driftkit.workflows.spring.service.ModelRequestService;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class RouterWorkflow extends ModelWorkflow<RouterStartEvent, RouterResult> {
    public static final String FINAL_STEP = "finalStep";

    public static final String ROUTER_METHOD = "router";
    private final VaultConfig modelConfig;
    private final RAGSearchWorkflow searchWorkflow;

    public RouterWorkflow(EtlConfig config, PromptService promptService, RAGSearchWorkflow searchWorkflow, ModelRequestService modelRequestService) throws IOException {
        super(ModelClientFactory.fromConfig(config.getModelConfig(OpenAIModelClient.OPENAI_PREFIX).orElseThrow()),
             modelRequestService,
             promptService);
             
        this.modelConfig = config.getModelConfig(OpenAIModelClient.OPENAI_PREFIX).orElseThrow();
        this.searchWorkflow = searchWorkflow;

        promptService.createIfNotExists(
                ROUTER_METHOD,
                IOUtils.resourceToString("/prompts/dictionary/router/%s.prompt".formatted(ROUTER_METHOD), Charset.defaultCharset()),
                null,
                true,
                Language.GENERAL,
                true
        );
    }

    @Step(name = "start")
    @StepInfo(description = "Starting step of the workflow")
    public WorkflowEvent start(RouterStartEvent startEvent, WorkflowContext workflowContext) throws Exception {
        Prompt check = promptService.getCurrentPromptOrThrow(ROUTER_METHOD, Language.GENERAL);
        String model = Optional.ofNullable(modelConfig.getModelMini()).orElse(OpenAIModelClient.GPT_MINI_DEFAULT);

        RouterResult routerResult = getRouterResult(startEvent, workflowContext, check, model);

        if (routerResult.isInputType(RouterDefaultInputTypes.CUSTOM)) {
            if (CollectionUtils.isEmpty(routerResult.getCustomRoutes())) {
                return new DataEvent<>(startEvent, "retry");
            }

            Set<String> routes = startEvent.getCustomRoutes().stream()
                    .map(Route::getRoute)
                    .collect(Collectors.toSet());

            boolean allFound = routerResult.getCustomRoutes().stream()
                    .allMatch(e -> routes.contains(e.getDecision()));

            if (!allFound) {
                return new DataEvent<>(startEvent, "retry");
            }
        }

        return new DataEvent<>(routerResult, FINAL_STEP);
    }

    @Step(name = "retry")
    @StepInfo(description = "Retry with smarter model")
    public WorkflowEvent retry(DataEvent<RouterStartEvent> startEvent, WorkflowContext workflowContext) throws Exception {
        Prompt check = promptService.getCurrentPromptOrThrow(ROUTER_METHOD, Language.GENERAL);
        String model = Optional.ofNullable(modelConfig.getModel()).orElse(OpenAIModelClient.GPT_DEFAULT);

        RouterResult routerResult = getRouterResult(startEvent.getResult(), workflowContext, check, model);

        return new DataEvent<>(routerResult, FINAL_STEP);
    }

    @Step(name = FINAL_STEP)
    @StepInfo(description = "Final step of the workflow")
    public StopEvent<RouterResult> finalStep(DataEvent<RouterResult> event, WorkflowContext workflowContext) throws JsonProcessingException {
        log.info("Executing finalStep with event: {}", event.getResult());

        RouterResult resultNode = event.getResult();

        return StopEvent.ofObject(resultNode);
    }

    @NotNull
    private RouterResult getRouterResult(RouterStartEvent startEvent, WorkflowContext workflowContext, Prompt check, String model) throws JsonProcessingException {
        Map<String, Object> variables = Map.of(
                "query", startEvent.getQuery(),
                "history", JsonUtils.toJson(startEvent.getMessages()),
                "customRoutes", startEvent.getCustomRoutes(),
                "defaultInputTypes", RouterDefaultInputTypes.routes(),
                "routesDefault", List.of(RouterDefaultOutputTypes.values()),
                "indexesList", startEvent.getIndexesList()
        );

        // Send the request with the specified model and prompt message
        ModelTextResponse response = sendPromptText(
            ModelRequestParams.create()
                .setPromptText(check.getMessage())
                .setVariables(variables)
                .setTemperature(modelConfig.getTemperature())
                .setModel(model), 
            workflowContext);

        RouterResult routerResult = response.getResponseJson(RouterResult.class);

        if (CollectionUtils.isNotEmpty(routerResult.getIndexes())) {
            List<DocumentsResult> relatedDocs = new ArrayList<>();
            routerResult.setRelatedDocs(relatedDocs);

            for (RouterDecision<String> index : routerResult.getIndexes()) {
                try {
                    StopEvent<DocumentsResult> docs = searchWorkflow.execute(new VectorStoreStartEvent(index.decision, startEvent.getQuery(), 10), workflowContext);

                    relatedDocs.add(docs.get());
                } catch (Exception e) {
                    log.error("[router] Couldn't query index [%s] for query [%s]".formatted(index, startEvent.getQuery()), e);
                }
            }
        }
        return routerResult;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Route {
        private String route;
        private String description;
    }

    @Data
    public static class RouterStartEvent extends StartQueryEvent {
        private List<Message> messages;
        private List<Route> customRoutes;
        private List<Index> indexesList;
        private Message currentMessage;

        @Builder
        public RouterStartEvent(List<Message> messages, List<Route> customRoutes, List<Index> indexesList) {
            super(messages.getLast().getMessage());
            this.messages = new ArrayList<>(messages);
            this.currentMessage = messages.remove(messages.size() - 1);
            this.customRoutes = customRoutes;
            this.indexesList = indexesList;
        }

        public List<Route> getCustomRoutes() {
            return customRoutes == null ? Collections.emptyList() : customRoutes;
        }

        public List<Index> getIndexesList() {
            return indexesList == null ? Collections.emptyList() : indexesList;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouterDecision<T> {
        @JsonAlias({"route", "type", "index"})
        @JsonProperty("decision")
        private T decision;
        private double confidence;
    }

    @AllArgsConstructor
    public enum RouterDefaultInputTypes {
        GREETING("Initial greeting and contact establishment"),
        INFORMATION_REQUEST("Requests for information (e.g., FAQs or KB search)"),
        CLARIFICATION("Requests for further clarification / interactive dialogue"),
        CHAT("Regular interactive dialogue"),
        IMAGE_GENERATION("Image generation task"),
        FEEDBACK("Feedback or requests to modify generated content"),
        ESCALATION("Cases requiring human intervention or escalation"),
        SALES_SUPPORT("Inquiries related to sales, marketing, or product info"),
        PRODUCT_ISSUE("Product issue"),
        CUSTOM("Only if some of the customRoutes found in initial query"),
        UNKNOWN("Fallback when the type is unclear");

        private final String description;

        public static List<Route> routes() {
            return Arrays.stream(values())
                    .map(e -> new Route(e.name(), e.description))
                    .collect(Collectors.toList());
        }
    }

    public enum RouterDefaultOutputTypes {
        RAG,
        SUPPORT_REQUEST,
        REDO_WITH_SMARTER_MODEL,
        REASONING,
        SALES_REQUEST,
        CHAT
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouterResult {
        private Set<RouterDecision<RouterDefaultInputTypes>> inputTypes;
        private Set<RouterDecision<RouterDefaultOutputTypes>> routes;
        private Set<RouterDecision<String>> customRoutes;
        private Set<RouterDecision<String>> indexes;
        private List<DocumentsResult> relatedDocs;

        public boolean isInputType(RouterDefaultInputTypes type) {
            return inputTypes.stream().anyMatch(e -> e.getDecision() == type);
        }

        public boolean isOutputType(RouterDefaultOutputTypes type) {
            return routes.stream().anyMatch(e -> e.getDecision() == type);
        }
    }
}