package ai.driftkit.context.core.service;

import ai.driftkit.common.domain.*;
import ai.driftkit.common.domain.PromptRequest.PromptIdRequest;
import ai.driftkit.common.domain.client.ResponseFormat;
import ai.driftkit.common.utils.AIUtils;
import ai.driftkit.context.core.registry.PromptServiceRegistry;
import ai.driftkit.context.core.util.PromptUtils;
import ai.driftkit.common.domain.Prompt.ResolveStrategy;
import ai.driftkit.common.domain.Prompt.State;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Slf4j
public class PromptService implements PromptServiceBase {
    public static final String STARTING_PROMPT = "Please respond to the user request [{{query}}], think step by step. Your response MUST be in [{{language}}] language";
    public static final String DEFAULT_STARTING_PROMPT = "default_starting_prompt";

    public static final String PREFIX_DICT = "dict";
    public static final String PREFIX_INDEX = "index:";
    protected PromptServiceBase promptService;
    private DictionaryItemService dictionaryItemService;

    // Queue to hold prompts when the repository isn't initialized
    private final Queue<PromptInitializationTask> promptInitializationQueue = new ConcurrentLinkedQueue<>();

    // Flag to indicate whether the service has been initialized
    private volatile boolean serviceInitialized;

    public PromptService(PromptServiceBase promptService, DictionaryItemService dictionaryItemService) {
        this.promptService = promptService;
        this.dictionaryItemService = dictionaryItemService;
        this.serviceInitialized = true;
        processQueuedPrompts();

        PromptServiceRegistry.register(this);
    }

    @Override
    public void configure(Map<String, String> config) {
    }

    public Prompt createIfNotExists(String method, String prompt, String systemMessage, boolean jsonResponse) {
        return createIfNotExists(method, prompt, systemMessage, jsonResponse, Language.GENERAL, false);
    }

    public Prompt createIfNotExists(String method, String prompt, String systemMessage, boolean jsonResponse, Language language) {
        return createIfNotExists(method, prompt, systemMessage, jsonResponse, language, false, null);
    }
    
    public Prompt createIfNotExists(String method, String prompt, String systemMessage, boolean jsonResponse, Language language, String workflow) {
        return createIfNotExists(method, prompt, systemMessage, jsonResponse, language, false, workflow);
    }

    public Prompt createIfNotExists(String method, String prompt, String systemMessage, boolean jsonResponse, Language language, boolean forceRepoVersion) {
        return createIfNotExists(method, prompt, systemMessage, jsonResponse, language, forceRepoVersion, null);
    }

    public Prompt createIfNotExists(String method, String prompt, String systemMessage, boolean jsonResponse, Language language, boolean forceRepoVersion, String workflow) {
        method = method.replace(".prompt", "");

        // If the service isn't initialized, queue the prompt for later processing
        if (!serviceInitialized || !promptService.isConfigured()) {
            promptInitializationQueue.add(new PromptInitializationTask(method, prompt, systemMessage, jsonResponse, forceRepoVersion, workflow));
            return null;
        }

        List<Prompt> promptOpt = getPromptsByMethods(List.of(method));

        if (CollectionUtils.isNotEmpty(promptOpt)) {
            Optional<Prompt> langFound = promptOpt.stream().filter(e -> e.getLanguage() == language).findAny();

            if (langFound.isPresent()) {
                if (!forceRepoVersion || langFound.get().getMessage().equals(prompt)) {
                    // If workflow is provided and different from the existing one, update it
//                    if (workflow != null && !workflow.equals(langFound.get().getWorkflow())) {
//                        Prompt updated = langFound.get();
//                        updated.setWorkflow(workflow);
//                        return savePrompt(updated);
//                    }
                    return langFound.get();
                }
            }
        }

        Prompt toSave = new Prompt(
                UUID.randomUUID().toString(),
                method,
                prompt,
                systemMessage,
                State.CURRENT,
                null,
                ResolveStrategy.LAST_VERSION,
                workflow,
                language,
                null,
                false,
                jsonResponse,
                null, // responseFormat
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );
        savePrompt(toSave);

        return toSave;
    }

    private void processQueuedPrompts() {
        PromptInitializationTask task;
        while ((task = promptInitializationQueue.poll()) != null) {
            // Process each queued prompt
            createIfNotExists(task.method, task.prompt, task.systemMessage, task.jsonResponse, Language.GENERAL, task.forceRepoVersion, task.workflow);
        }
    }

    @Override
    public boolean supportsName(String name) {
        return this.promptService.supportsName(name);
    }

    @Override
    public Optional<Prompt> getPromptById(String id) {
        return this.promptService.getPromptById(id);
    }

    @Override
    public List<Prompt> getPromptsByIds(List<String> ids) {
        return this.promptService.getPromptsByIds(ids);
    }

    @Override
    public List<Prompt> getPromptsByMethods(List<String> methods) {
        return this.promptService.getPromptsByMethods(methods);
    }

    @Override
    public List<Prompt> getPromptsByMethodsAndState(List<String> methods, State state) {
        return this.promptService.getPromptsByMethodsAndState(methods, state);
    }

    @Override
    public List<Prompt> getPrompts() {
        return this.promptService.getPrompts();
    }

    @Override
    public Prompt savePrompt(Prompt prompt) {
        return this.promptService.savePrompt(prompt);
    }

    @Override
    public Prompt deletePrompt(String id) {
        return this.promptService.deletePrompt(id);
    }

    public MessageTask getTaskFromPromptRequest(PromptRequest request) {
        Map<String, PromptIdRequest> ids2req = request.getPromptIds()
                .stream()
                .collect(Collectors.toMap(PromptIdRequest::getPromptId, e -> e));
                
        // Check that each PromptIdRequest has the required parameters
        for (PromptIdRequest promptIdRequest : request.getPromptIds()) {
            if (StringUtils.isBlank(promptIdRequest.getPromptId())) {
                throw new IllegalArgumentException("promptId cannot be null or empty in PromptIdRequest");
            }
        }

        List<Prompt> prompts = getCurrentPromptsForMethodStateAndLanguage(
                new ArrayList<>(ids2req.keySet()),
                request.getLanguage()
        );

        Double temperature = null;

        if (request.isSavePrompt() || prompts.isEmpty()) {
            prompts = new ArrayList<>();

            for (String method : ids2req.keySet()) {
                List<Prompt> generalPrompts = getPromptsByMethodsAndState(
                        List.of(method),
                        State.CURRENT
                );

                Optional<Prompt> generalPrompt = generalPrompts.stream().filter(e -> e.getLanguage() == Language.GENERAL).findAny();

                if (generalPrompt.isEmpty()) {
                    generalPrompt = Optional.ofNullable(generalPrompts.isEmpty() ? null : generalPrompts.getFirst());
                }

                Prompt prompt;
                PromptIdRequest promptIdRequest = ids2req.get(method);

                if (generalPrompt.isPresent()) {
                    // Use existing prompt as template
                    prompt = generalPrompt.get();
                    prompt.setLanguage(request.getLanguage());
                } else {
                    // Create a new prompt if no existing one is found
                    // Check that the request contains prompt text
                    if (StringUtils.isBlank(promptIdRequest.getPrompt())) {
                        throw new IllegalArgumentException("Prompt text is required when creating a new prompt for method: " + method);
                    }

                    log.info("Creating new prompt for method: {}, language: {}", method, request.getLanguage());

                    prompt = new Prompt(
                            UUID.randomUUID().toString(),
                            method,
                            promptIdRequest.getPrompt(),
                            null, // systemMessage
                            State.CURRENT,
                            request.getWorkflow(),
                            ResolveStrategy.LAST_VERSION,
                            null,
                            request.getLanguage(),
                            promptIdRequest.getTemperature(),
                            false,
                            false, // jsonResponse - can be parameterized in the future
                            null, // responseFormat
                            System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            System.currentTimeMillis()
                    );
                }

                // If the request contains prompt text, update the existing prompt
                if (StringUtils.isNotBlank(promptIdRequest.getPrompt())) {
                    prompt.setMessage(promptIdRequest.getPrompt());
                }

                savePrompt(prompt);
                prompts.add(prompt);
            }
        }

        StringBuilder systemMessageBuilder = new StringBuilder();
        StringBuilder messageBuilder = new StringBuilder();

        String workflow = request.getWorkflow();
        boolean skipWorkflow = "skip".equals(workflow);

        // If request explicitly sets jsonResponse, use that; otherwise default to false
        boolean jsonResponse = request.getJsonResponse() != null ? request.getJsonResponse() : false;
        ResponseFormat responseFormat = request.getResponseFormat();

        for (Prompt prompt : prompts) {
            PromptIdRequest promptId = ids2req.get(prompt.getMethod());

            if (temperature == null) {
                temperature = Optional.ofNullable(promptId.getTemperature()).orElse(prompt.getTemperature());
            }

            // Only override jsonResponse from prompt if not explicitly set in request
            if (request.getJsonResponse() == null) {
                jsonResponse = jsonResponse || prompt.isJsonResponse();
            }
            
            // Override responseFormat from prompt if not already set in request
            if (responseFormat == null && prompt.getResponseFormat() != null) {
                responseFormat = prompt.getResponseFormat();
            }

            if (!skipWorkflow && workflow == null && prompt.getWorkflow() != null) {
                workflow = prompt.getWorkflow();
            }

            messageBuilder.append(prompt.getMessage()).append(" ");

            if (prompt.getSystemMessage() != null) {
                systemMessageBuilder.append(prompt.getSystemMessage()).append(' ');
            }

            // Always save the prompt if the text is provided, regardless of the savePrompt flag
            if (StringUtils.isNotBlank(promptId.getPrompt())) {
                prompt.setLanguage(request.getLanguage());
                prompt.setWorkflow(request.getWorkflow());
                prompt.setMessage(promptId.getPrompt());
                savePrompt(prompt);
            } else if (!request.isSavePrompt()) {
                // If no prompt text and savePrompt flag is not set, skip this prompt
                continue;
            }
        }

        if (skipWorkflow) {
            workflow = null;
        }

        String message = messageBuilder.toString();
        Map<String, Object> variables = request.getVariables() == null ? new HashMap<>() : request.getVariables();

        Map<String, String> specialVariables = variables.entrySet().stream()
                .filter(e -> e.getValue() instanceof String str && (str.startsWith(PREFIX_DICT) || str.startsWith(PREFIX_INDEX)))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));

        if (!specialVariables.isEmpty()) {
            for (Entry<String, String> name2value : specialVariables.entrySet()) {
                String key = name2value.getKey();
                String value = name2value.getValue();

                if (value.startsWith(PREFIX_DICT)) {
                    putDictValues(name2value, value);
                } else if (value.startsWith(PREFIX_INDEX)) {
                    name2value.setValue("");
                }

            }

            // Combine all variables
            variables.putAll(specialVariables);
        }

        return MessageTask.builder()
                .messageId(AIUtils.generateId())
                .message(message)
                .modelId(request.getModelId())
                .language(request.getLanguage())
                .promptIds(prompts.stream().map(Prompt::getId).toList())
                .chatId(request.getChatId())
                .workflow(workflow)
                .temperature(temperature)
                .jsonResponse(jsonResponse)
                .responseFormat(responseFormat)
                .systemMessage(systemMessageBuilder.isEmpty() ? null : systemMessageBuilder.toString())
                .variables(variables)
                .logprobs(request.getLogprobs())
                .topLogprobs(request.getTopLogprobs())
                .purpose(request.getPurpose())
                .imageBase64(request.getImageBase64())
                .imageMimeType(request.getImageMimeType())
                .createdTime(System.currentTimeMillis())
                .build();
    }

    private void putDictValues(Entry<String, String> name2value, String value) {
        String dictionaryId = value.substring(3);

        boolean samples = dictionaryId.contains("-samples:");
        boolean markers = dictionaryId.contains("-markers:");

        Optional<DictionaryItem> itemOpt = dictionaryItemService.findById(dictionaryId.split(":")[1]);

        if (itemOpt.isEmpty()) {
            log.warn("[prompt-processor] Dictionary item is not found for id [%s]".formatted(dictionaryId));
            name2value.setValue("");
            return;
        }

        DictionaryItem item = itemOpt.get();

        if (samples) {
            if (CollectionUtils.isNotEmpty(item.getSamples())) {
                name2value.setValue(String.join(",", item.getSamples()));
            }
        } else if (markers) {
            if (CollectionUtils.isNotEmpty(item.getMarkers())) {
                name2value.setValue(String.join(",", item.getMarkers()));
            }
        } else {
            name2value.setValue("");
        }
    }

    private static class PromptInitializationTask {
        String method;
        String prompt;
        String systemMessage;
        boolean jsonResponse;
        boolean forceRepoVersion;
        String workflow;

        public PromptInitializationTask(String method, String prompt, String systemMessage, boolean jsonResponse, boolean forceRepoVersion) {
            this(method, prompt, systemMessage, jsonResponse, forceRepoVersion, null);
        }

        public PromptInitializationTask(String method, String prompt, String systemMessage, boolean jsonResponse, boolean forceRepoVersion, String workflow) {
            this.method = method;
            this.prompt = prompt;
            this.systemMessage = systemMessage;
            this.jsonResponse = jsonResponse;
            this.forceRepoVersion = forceRepoVersion;
            this.workflow = workflow;
        }
    }
}