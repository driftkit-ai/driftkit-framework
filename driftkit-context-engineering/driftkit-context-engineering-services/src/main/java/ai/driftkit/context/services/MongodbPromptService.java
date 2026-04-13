package ai.driftkit.context.services;

import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.Prompt.State;
import ai.driftkit.common.domain.Language;
import ai.driftkit.context.core.service.PromptServiceBase;
import ai.driftkit.context.services.config.ApplicationContextProvider;
import ai.driftkit.context.services.repository.PromptRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

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
            try {
                ApplicationContext context = ApplicationContextProvider.getApplicationContext();
                if (context != null) {
                    this.promptRepository = context.getBean(PromptRepository.class);
                }
            } catch (IllegalStateException e) {
                throw new IllegalStateException("ApplicationContext is not initialized yet. Cannot access PromptRepository.", e);
            }
        }
        if (this.promptRepository == null) {
            throw new IllegalStateException("PromptRepository is not available. ApplicationContext may not be fully initialized.");
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
    public synchronized Prompt savePrompt(Prompt prompt) {
        Optional<Prompt> currentPromptOpt = getCurrentPrompt(prompt.getMethod(), prompt.getLanguage());

        if (currentPromptOpt.isEmpty() || prompt.getId() == null) {
            prompt.setId(UUID.randomUUID().toString());
            prompt.setCreatedTime(System.currentTimeMillis());
        }

        if (currentPromptOpt.isPresent()) {
            Prompt currentPrompt = currentPromptOpt.get();

            if (prompt.getMessage().equals(currentPrompt.getMessage())) {
                prompt.setId(currentPrompt.getId());
                prompt.setVersion(currentPrompt.getVersion());
            } else {
                currentPrompt.setState(State.REPLACED);
                getPromptRepository().save(currentPrompt);
                prompt.setVersion(currentPrompt.getVersion() + 1);
            }
        } else {
            // First version for this method+language
            int maxVersion = getMaxVersion(prompt.getMethod(), prompt.getLanguage());
            prompt.setVersion(maxVersion + 1);
        }

        if (prompt.getState() == null) {
            prompt.setState(State.CURRENT);
        }

        prompt.setUpdatedTime(System.currentTimeMillis());

        return getPromptRepository().save(prompt);
    }

    @Override
    public Prompt deletePrompt(String id) {
        Optional<Prompt> promptOpt = getPromptById(id);
        if (promptOpt.isPresent()) {
            Prompt deleted = promptOpt.get();
            getPromptRepository().deleteById(id);

            // If we deleted a CURRENT version, promote the latest REPLACED
            if (deleted.getState() == State.CURRENT) {
                List<Prompt> replaced = getPromptRepository()
                        .findByMethodAndState(deleted.getMethod(), State.REPLACED);
                replaced.stream()
                        .filter(p -> p.getLanguage() == deleted.getLanguage())
                        .max((a, b) -> Long.compare(a.getUpdatedTime(), b.getUpdatedTime()))
                        .ifPresent(latest -> {
                            latest.setState(State.CURRENT);
                            getPromptRepository().save(latest);
                        });
            }
            return deleted;
        }
        return null;
    }

    private int getMaxVersion(String method, Language language) {
        List<Prompt> all = getPromptRepository().findByMethod(method);
        return all.stream()
                .filter(p -> p.getLanguage() == language)
                .mapToInt(Prompt::getVersion)
                .max()
                .orElse(0);
    }

    @Override
    public boolean isConfigured() {
        return promptRepository != null;
    }

    public Optional<Prompt> getCurrentPrompt(String method, Language language) {
        return getPromptRepository().findByMethodAndLanguageAndState(method, language, State.CURRENT);
    }
}