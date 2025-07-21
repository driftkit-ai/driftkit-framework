package ai.driftkit.context.core.service;

import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.Prompt.State;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class FileSystemPromptService implements PromptServiceBase {

    private final Map<String, Prompt> promptsById = new HashMap<>();
    private final Map<String, List<Prompt>> promptsByMethod = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private File promptsFile;

    public FileSystemPromptService() throws IOException {
    }

    @SneakyThrows
    @Override
    public void configure(Map<String, String> config) {
        this.promptsFile = new File(config.get("promptsFilePath"));
        loadPrompts();
    }

    @Override
    public boolean supportsName(String name) {
        return "filesystem".equals(name);
    }

    private void loadPrompts() throws IOException {
        if (promptsFile.exists()) {
            List<Prompt> prompts = objectMapper.readValue(promptsFile, new TypeReference<List<Prompt>>() {});
            for (Prompt prompt : prompts) {
                promptsById.put(prompt.getId(), prompt);
                promptsByMethod.computeIfAbsent(prompt.getMethod(), k -> new ArrayList<>()).add(prompt);
            }
        }
    }

    private void savePrompts() throws IOException {
        List<Prompt> prompts = new ArrayList<>(promptsById.values());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(promptsFile, prompts);
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

        try {
            savePrompts();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save prompts to file system", e);
        }

        return prompt;
    }

    @SneakyThrows
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

        savePrompts();

        return removed;
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
}