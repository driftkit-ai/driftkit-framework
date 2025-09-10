package ai.driftkit.context.services;

import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.Prompt.State;
import ai.driftkit.common.domain.Language;
import ai.driftkit.context.core.service.PromptServiceBase;
import ai.driftkit.context.services.config.ApplicationContextProvider;
import ai.driftkit.context.services.repository.PromptRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class MongodbPromptService implements PromptServiceBase {

    private PromptRepository promptRepository;

    @Override
    public void configure(Map<String, String> config) {
        getPromptRepository();
    }

    @Override
    public boolean supportsName(String name) {
        return "mongodb".equals(name);
    }

    private PromptRepository getPromptRepository() {
        if (this.promptRepository == null) {
            if (ApplicationContextProvider.getApplicationContext() != null) {
                this.promptRepository = ApplicationContextProvider.getApplicationContext().getBean(PromptRepository.class);
            } else {
                throw new IllegalStateException("ApplicationContext is not initialized yet.");
            }
        }
        return this.promptRepository;
    }

    @Override
    public Optional<Prompt> getPromptById(String id) {
        return getPromptRepository().findById(id);
    }

    @Override
    public List<Prompt> getPromptsByIds(List<String> ids) {
        return getPromptRepository().findAllById(ids);
    }

    @Override
    public List<Prompt> getPromptsByMethods(List<String> methods) {
        return getPromptRepository().findByMethodIsIn(methods);
    }

    @Override
    public List<Prompt> getPromptsByMethodsAndState(List<String> methods, State state) {
        return getPromptRepository().findByMethodIsInAndState(methods, state);
    }

    @Override
    public List<Prompt> getPrompts() {
        return getPromptRepository().findAll();
    }

    @Override
    public Prompt savePrompt(Prompt prompt) {
        Optional<Prompt> currentPromptOpt = getCurrentPrompt(prompt.getMethod(), prompt.getLanguage());

        if (currentPromptOpt.isEmpty() || prompt.getId() == null) {
            prompt.setId(UUID.randomUUID().toString());
            prompt.setCreatedTime(System.currentTimeMillis());
        }

        if (currentPromptOpt.isPresent()) {
            Prompt currentPrompt = currentPromptOpt.get();

            if (prompt.getMessage().equals(currentPrompt.getMessage())) {
                prompt.setId(currentPrompt.getId());
            } else {
                currentPrompt.setState(State.REPLACED);
                getPromptRepository().save(currentPrompt);
            }
        }

        if (prompt.getState() == null) {
            prompt.setState(State.CURRENT);
        }

        prompt.setUpdatedTime(System.currentTimeMillis());

        return getPromptRepository().save(prompt);
    }

    @Override
    public Prompt deletePrompt(String id) {
        Optional<Prompt> prompt = getPromptById(id);
        getPromptRepository().deleteById(id);
        return prompt.orElse(null);
    }

    @Override
    public boolean isConfigured() {
        return promptRepository != null;
    }

    public Optional<Prompt> getCurrentPrompt(String method, Language language) {
        return getPromptRepository().findByMethodAndLanguageAndState(method, language, State.CURRENT);
    }
}