package ai.driftkit.context.core.service;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.Prompt.State;

import java.util.*;
import java.util.stream.Collectors;

public interface PromptServiceBase {

    void configure(Map<String, String> config);

    boolean supportsName(String name);

    Optional<Prompt> getPromptById(String id);

    List<Prompt> getPromptsByIds(List<String> ids);

    List<Prompt> getPromptsByMethods(List<String> methods);

    List<Prompt> getPromptsByMethodsAndState(List<String> methods, State state);

    List<Prompt> getPrompts();

    Prompt savePrompt(Prompt prompt);

    Prompt deletePrompt(String id);

    default Prompt getCurrentPromptOrThrow(String method) {
        return getCurrentPromptOrThrow(method, Language.GENERAL);
    }

    default Prompt getCurrentPromptOrThrow(String method, Language language) {
        // Check for prompt override (pipeline testing)
        String override = PromptOverrideContext.getOverride(method);
        if (override != null) {
            Prompt overridden = new Prompt();
            overridden.setMethod(method);
            overridden.setMessage(override);
            overridden.setLanguage(language);
            overridden.setState(Prompt.State.CURRENT);
            return overridden;
        }

        // Check for environment-specific version
        Integer envVersion = PromptEnvironmentResolver.resolveVersion(method, language);
        if (envVersion != null) {
            List<Prompt> allVersions = getPromptsByMethods(List.of(method));
            Optional<Prompt> envPrompt = allVersions.stream()
                    .filter(p -> p.getVersion() == envVersion && p.getLanguage() == language)
                    .findFirst();
            if (envPrompt.isPresent()) {
                return envPrompt.get();
            }
            // Fall through to CURRENT if env version not found
        }

        List<Prompt> prompts = getCurrentPromptsForMethodStateAndLanguage(List.of(method), language);

        if (prompts.isEmpty()) {
            throw new RuntimeException("Prompt is not found for methods [%s] and language [%s]".formatted(method, language));
        }

        if (prompts.size() > 1) {
            throw new RuntimeException("Too many current prompts [%s] found for methods [%s] and language [%s]".formatted(prompts.size(), method, language));
        }

        return prompts.getFirst();
    }

    default Optional<Prompt> getCurrentPrompt(String method, Language language) {
        // Check for prompt override (pipeline testing)
        String override = PromptOverrideContext.getOverride(method);
        if (override != null) {
            Prompt overridden = new Prompt();
            overridden.setMethod(method);
            overridden.setMessage(override);
            overridden.setLanguage(language);
            overridden.setState(Prompt.State.CURRENT);
            return Optional.of(overridden);
        }

        // Check for environment-specific version
        Integer envVersion = PromptEnvironmentResolver.resolveVersion(method, language);
        if (envVersion != null) {
            List<Prompt> allVersions = getPromptsByMethods(List.of(method));
            Optional<Prompt> envPrompt = allVersions.stream()
                    .filter(p -> p.getVersion() == envVersion && p.getLanguage() == language)
                    .findFirst();
            if (envPrompt.isPresent()) {
                return envPrompt;
            }
        }

        List<Prompt> prompts = getCurrentPromptsForMethodStateAndLanguage(List.of(method), language);

        if (prompts.isEmpty()) {
            return Optional.empty();
        }

        if (prompts.size() > 1) {
            throw new RuntimeException("Too many current prompts [%s] found for methods [%s] and language [%s]".formatted(prompts.size(), method, language));
        }

        return Optional.ofNullable(prompts.getFirst());
    }

    default List<Prompt> getCurrentPromptsForMethodStateAndLanguage(List<String> methods, Language language) {
        List<Prompt> prompts = getPromptsByMethodsAndState(
                methods,
                State.CURRENT
        );

        if (prompts.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<Prompt>> methodToPrompts = prompts.stream().collect(Collectors.groupingBy(Prompt::getMethod));

        return methodToPrompts.values().stream().map(e -> {
            if (e.size() == 1) {
                Language lang = e.getFirst().getLanguage();

                if (lang == Language.GENERAL || lang.name().equalsIgnoreCase(language.name())) {
                    return e.getFirst();
                }

                return null;
            } else {
                Optional<Prompt> promptForRequestLanguage = e.stream().filter(k -> k.getLanguage().name().equalsIgnoreCase(language.name())).findAny();

                return promptForRequestLanguage.orElseGet(
                        () -> e.stream().filter(k -> k.getLanguage() == Language.GENERAL).findAny()
                                .orElse(null)
                );
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    default boolean isConfigured() {
        return true;
    }
}