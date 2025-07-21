package ai.driftkit.context.core.service;

import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.Prompt.State;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryPromptService implements PromptServiceBase {

    private final Map<String, Prompt> promptsById = new ConcurrentHashMap<>();
    private final Map<String, List<Prompt>> promptsByMethod = new ConcurrentHashMap<>();

    @Override
    public void configure(Map<String, String> config) {
    }

    @Override
    public boolean supportsName(String name) {
        return "in-memory".equals(name);
    }

    @Override
    public Optional<Prompt> getPromptById(String id) {
        return Optional.ofNullable(promptsById.get(id));
    }

    @Override
    public List<Prompt> getPromptsByIds(List<String> ids) {
        return ids.stream()
                .map(promptsById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<Prompt> getPromptsByMethods(List<String> methods) {
        return methods.stream()
                .flatMap(method -> promptsByMethod.getOrDefault(method, Collections.emptyList()).stream())
                .collect(Collectors.toList());
    }

    @Override
    public List<Prompt> getPromptsByMethodsAndState(List<String> methods, State state) {
        return getPromptsByMethods(methods).stream()
                .filter(prompt -> prompt.getState() == state)
                .collect(Collectors.toList());
    }

    @Override
    public List<Prompt> getPrompts() {
        return new ArrayList<>(promptsById.values());
    }

    @Override
    public synchronized Prompt savePrompt(Prompt prompt) {
        if (prompt.getId() == null) {
            prompt.setId(UUID.randomUUID().toString());
            prompt.setCreatedTime(System.currentTimeMillis());
        }
        prompt.setUpdatedTime(System.currentTimeMillis());

        promptsById.put(prompt.getId(), prompt);

        promptsByMethod.computeIfAbsent(prompt.getMethod(), k -> new ArrayList<>()).add(prompt);

        return prompt;
    }

    @Override
    public Prompt deletePrompt(String id) {
        Prompt removed = promptsById.remove(id);

        List<Prompt> prompts = promptsByMethod.get(removed.getMethod());

        for (Iterator<Prompt> it = prompts.iterator(); it.hasNext(); ) {
            Prompt prompt = it.next();

            if (id.equals(prompt.getId())) {
                it.remove();
            }
        }

        return removed;
    }
}