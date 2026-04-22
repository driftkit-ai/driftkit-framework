package ai.driftkit.context.spring.controller;

import ai.driftkit.common.domain.CreatePromptRequest;
import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.context.spring.testsuite.domain.PromptEnvironment;
import ai.driftkit.context.spring.testsuite.domain.PromptMethodConfig;
import ai.driftkit.context.spring.testsuite.domain.PromptAudit;
import ai.driftkit.context.spring.testsuite.repository.PromptEnvironmentRepository;
import ai.driftkit.context.spring.testsuite.repository.PromptMethodConfigRepository;
import ai.driftkit.context.spring.testsuite.service.PromptAuditService;
import ai.driftkit.common.domain.RestResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Slf4j
@Controller
@RequestMapping(path = "/data/v1.0/admin/prompt/")
public class PromptRestController {

    @Autowired
    private PromptService promptService;

    @Autowired(required = false)
    private PromptMethodConfigRepository promptMethodConfigRepository;

    @Autowired(required = false)
    private PromptEnvironmentRepository promptEnvironmentRepository;

    @Autowired(required = false)
    private PromptAuditService auditService;

    @PostMapping("/create-if-not-exists")
    public @ResponseBody RestResponse<Prompt> createIfNotExists(
            @RequestBody CreatePromptRequest promptData
    ) {
        Prompt result = promptService.createIfNotExists(
                promptData.getMethod(),
                promptData.getMessage(),
                promptData.getSystemMessage(),
                promptData.isJsonResponse(),
                promptData.getLanguage(),
                BooleanUtils.isTrue(promptData.getForceRepoVersion()),
                promptData.getWorkflow()
        );

        return new RestResponse<>(
                true,
                result
        );
    }

    @GetMapping("/")
    public @ResponseBody RestResponse<List<Prompt>> getPrompt() {
        List<Prompt> prompts = promptService.getPrompts();

        return new RestResponse<>(
                true,
                prompts
        );
    }

    @DeleteMapping("/{id}")
    public @ResponseBody RestResponse<Prompt> deletePrompt(@PathVariable String id) {
        Prompt prompt = promptService.deletePrompt(id);

        return new RestResponse<>(
                true,
                prompt
        );
    }

    @PostMapping("/list")
    public @ResponseBody RestResponse<List<Prompt>> getPrompt(
            @RequestBody List<Prompt> prompts
    ) {
        for (Prompt prompt : prompts) {
            promptService.savePrompt(prompt);
        }

        return new RestResponse<>(
                true,
                prompts
        );
    }

    @PostMapping("/")
    public @ResponseBody RestResponse<Prompt> savePrompt(
            @RequestBody Prompt input
    ) {
        Prompt prompt = promptService.savePrompt(input);
        audit(prompt.getMethod(), prompt.getLanguage(), PromptAudit.Action.SAVED, prompt.getVersion());

        return new RestResponse<>(
                true,
                prompt
        );
    }

    @GetMapping("/{method}")
    public @ResponseBody RestResponse<Prompt> getPrompt(
            @PathVariable String method
    ) {
        Prompt prompt = promptService.getCurrentPromptOrThrow(method);

        return new RestResponse<>(
                true,
                prompt
        );
    }
    
    @GetMapping("/byIds")
    public @ResponseBody RestResponse<List<Prompt>> getPromptsByIds(
            @RequestParam String ids
    ) {
        if (ids == null || ids.isBlank()) {
            return new RestResponse<>(false, null, "ids parameter must not be empty");
        }
        String[] promptIds = ids.split(",");
        List<String> idList = List.of(promptIds);
        List<Prompt> prompts = promptService.getPromptsByIds(idList);

        return new RestResponse<>(
                true,
                prompts
        );
    }

    @GetMapping("/{method}/versions")
    public @ResponseBody RestResponse<List<Prompt>> getVersions(
            @PathVariable String method,
            @RequestParam(required = false) Language language
    ) {
        List<Prompt> all = promptService.getPromptsByMethods(List.of(method));
        List<Prompt> filtered = all;
        if (language != null) {
            filtered = all.stream()
                    .filter(p -> p.getLanguage() == language)
                    .toList();
        }
        List<Prompt> sorted = filtered.stream()
                .sorted((a, b) -> Integer.compare(b.getVersion(), a.getVersion()))
                .toList();
        return new RestResponse<>(true, sorted);
    }

    // --- Lifecycle Endpoints ---

    @PostMapping("/{method}/publish")
    public @ResponseBody RestResponse<Prompt> publish(
            @PathVariable String method,
            @RequestParam(required = false, defaultValue = "GENERAL") Language language
    ) {
        Prompt draft = findPromptByMethodLanguageAndState(method, language, Prompt.State.DRAFT);
        if (draft == null) {
            return new RestResponse<>(false, null, "No DRAFT prompt found for " + method + " / " + language);
        }

        // Mark old CURRENT as REPLACED
        promptService.getPromptsByMethodsAndState(List.of(method), Prompt.State.CURRENT).stream()
                .filter(p -> p.getLanguage() == language)
                .forEach(p -> {
                    p.setState(Prompt.State.REPLACED);
                    promptService.savePrompt(p);
                });

        draft.setState(Prompt.State.CURRENT);
        draft.setUpdatedTime(System.currentTimeMillis());
        Prompt saved = promptService.savePrompt(draft);
        audit(method, language, PromptAudit.Action.PUBLISHED, saved.getVersion());
        return new RestResponse<>(true, saved);
    }

    @PostMapping("/{method}/submit-for-testing")
    public @ResponseBody RestResponse<Prompt> submitForTesting(
            @PathVariable String method,
            @RequestParam(required = false, defaultValue = "GENERAL") Language language
    ) {
        Prompt draft = findPromptByMethodLanguageAndState(method, language, Prompt.State.DRAFT);
        if (draft == null) {
            return new RestResponse<>(false, null, "No DRAFT prompt found for " + method + " / " + language);
        }

        draft.setState(Prompt.State.AUTO_TESTING);
        draft.setUpdatedTime(System.currentTimeMillis());
        Prompt saved = promptService.savePrompt(draft);
        return new RestResponse<>(true, saved);
    }

    @PostMapping("/{method}/approve")
    public @ResponseBody RestResponse<Prompt> approve(
            @PathVariable String method,
            @RequestParam(required = false, defaultValue = "GENERAL") Language language
    ) {
        Prompt testing = findPromptByMethodLanguageAndState(method, language, Prompt.State.MANUAL_TESTING);
        if (testing == null) {
            return new RestResponse<>(false, null, "No MANUAL_TESTING prompt found for " + method + " / " + language);
        }

        // Mark old CURRENT as REPLACED
        promptService.getPromptsByMethodsAndState(List.of(method), Prompt.State.CURRENT).stream()
                .filter(p -> p.getLanguage() == language)
                .forEach(p -> {
                    p.setState(Prompt.State.REPLACED);
                    promptService.savePrompt(p);
                });

        testing.setState(Prompt.State.CURRENT);
        testing.setApprovedTime(System.currentTimeMillis());
        testing.setUpdatedTime(System.currentTimeMillis());
        Prompt saved = promptService.savePrompt(testing);
        return new RestResponse<>(true, saved);
    }

    @PostMapping("/{method}/reject")
    public @ResponseBody RestResponse<Prompt> reject(
            @PathVariable String method,
            @RequestParam(required = false, defaultValue = "GENERAL") Language language
    ) {
        Prompt testing = findPromptByMethodLanguageAndState(method, language, Prompt.State.MANUAL_TESTING);
        if (testing == null) {
            testing = findPromptByMethodLanguageAndState(method, language, Prompt.State.AUTO_TESTING);
        }
        if (testing == null) {
            return new RestResponse<>(false, null, "No testing prompt found for " + method + " / " + language);
        }

        testing.setState(Prompt.State.DRAFT);
        testing.setUpdatedTime(System.currentTimeMillis());
        Prompt saved = promptService.savePrompt(testing);
        return new RestResponse<>(true, saved);
    }

    @PostMapping("/{method}/restore")
    public @ResponseBody RestResponse<Prompt> restore(
            @PathVariable String method,
            @RequestParam int version,
            @RequestParam(required = false, defaultValue = "GENERAL") Language language
    ) {
        List<Prompt> all = promptService.getPromptsByMethods(List.of(method));
        Prompt source = all.stream()
                .filter(p -> p.getVersion() == version && p.getLanguage() == language)
                .findFirst()
                .orElse(null);

        if (source == null) {
            return new RestResponse<>(false, null, "Version " + version + " not found for " + method);
        }

        // Create new DRAFT from old version
        Prompt restored = new Prompt();
        restored.setMethod(source.getMethod());
        restored.setMessage(source.getMessage());
        restored.setSystemMessage(source.getSystemMessage());
        restored.setState(Prompt.State.DRAFT);
        restored.setModelId(source.getModelId());
        restored.setWorkflow(source.getWorkflow());
        restored.setLanguage(source.getLanguage());
        restored.setTemperature(source.getTemperature());
        restored.setJsonRequest(source.isJsonRequest());
        restored.setJsonResponse(source.isJsonResponse());
        restored.setResponseFormat(source.getResponseFormat());

        Prompt saved = promptService.savePrompt(restored);
        return new RestResponse<>(true, saved);
    }

    // --- Config Endpoints ---

    @GetMapping("/config/{method}")
    public @ResponseBody RestResponse<PromptMethodConfig> getConfig(@PathVariable String method) {
        if (promptMethodConfigRepository == null) {
            return new RestResponse<>(false, null, "Config repository not available");
        }
        PromptMethodConfig config = promptMethodConfigRepository.findById(method)
                .orElse(PromptMethodConfig.builder().method(method).build());
        return new RestResponse<>(true, config);
    }

    @PutMapping("/config/{method}")
    public @ResponseBody RestResponse<PromptMethodConfig> saveConfig(
            @PathVariable String method,
            @RequestBody PromptMethodConfig config
    ) {
        if (promptMethodConfigRepository == null) {
            return new RestResponse<>(false, null, "Config repository not available");
        }
        config.setMethod(method);
        PromptMethodConfig saved = promptMethodConfigRepository.save(config);
        return new RestResponse<>(true, saved);
    }

    // --- Folder Operations ---

    @PostMapping("/move")
    public @ResponseBody RestResponse<Prompt> movePrompt(
            @RequestParam String method,
            @RequestParam String newMethod
    ) {
        List<Prompt> prompts = promptService.getPromptsByMethods(List.of(method));
        if (prompts.isEmpty()) {
            return new RestResponse<>(false, null, "No prompts found for method: " + method);
        }
        Prompt last = null;
        for (Prompt p : prompts) {
            p.setMethod(newMethod);
            last = promptService.savePrompt(p);
        }
        return new RestResponse<>(true, last);
    }

    @PostMapping("/rename-folder")
    public @ResponseBody RestResponse<Integer> renameFolder(
            @RequestParam String oldPrefix,
            @RequestParam String newPrefix
    ) {
        List<Prompt> all = promptService.getPrompts();
        int count = 0;
        for (Prompt p : all) {
            if (p.getMethod() != null && p.getMethod().startsWith(oldPrefix + "/")) {
                p.setMethod(newPrefix + p.getMethod().substring(oldPrefix.length()));
                promptService.savePrompt(p);
                count++;
            }
        }
        return new RestResponse<>(true, count, count + " prompts renamed");
    }

    @DeleteMapping("/folder/{prefix}")
    public @ResponseBody RestResponse<Integer> deleteFolder(@PathVariable String prefix) {
        List<Prompt> all = promptService.getPrompts();
        int count = 0;
        for (Prompt p : all) {
            if (p.getMethod() != null && p.getMethod().startsWith(prefix + "/")) {
                promptService.deletePrompt(p.getId());
                count++;
            }
        }
        return new RestResponse<>(true, count, count + " prompts deleted");
    }

    // --- Environment Endpoints ---

    @GetMapping("/{method}/environments")
    public @ResponseBody RestResponse<List<PromptEnvironment>> getEnvironments(@PathVariable String method) {
        if (promptEnvironmentRepository == null) {
            return new RestResponse<>(false, null, "Environment repository not available");
        }
        return new RestResponse<>(true, promptEnvironmentRepository.findByMethod(method));
    }

    @PostMapping("/{method}/promote")
    public @ResponseBody RestResponse<PromptEnvironment> promote(
            @PathVariable String method,
            @RequestParam int version,
            @RequestParam String environment,
            @RequestParam(required = false, defaultValue = "GENERAL") Language language
    ) {
        if (promptEnvironmentRepository == null) {
            return new RestResponse<>(false, null, "Environment repository not available");
        }
        PromptEnvironment env = promptEnvironmentRepository
                .findByMethodAndLanguageAndEnvironment(method, language, environment)
                .orElse(new PromptEnvironment());

        env.setMethod(method);
        env.setLanguage(language);
        env.setEnvironment(environment);
        env.setVersion(version);
        env.setUpdatedAt(System.currentTimeMillis());

        PromptEnvironment saved = promptEnvironmentRepository.save(env);
        return new RestResponse<>(true, saved);
    }

    // --- Audit Endpoints ---

    @GetMapping("/audit/{method}")
    public @ResponseBody RestResponse<List<PromptAudit>> getAuditHistory(@PathVariable String method) {
        if (auditService == null) {
            return new RestResponse<>(false, null, "Audit service not available");
        }
        return new RestResponse<>(true, auditService.getHistory(method));
    }

    // --- Helpers ---

    private Prompt findPromptByMethodLanguageAndState(String method, Language language, Prompt.State state) {
        return promptService.getPromptsByMethodsAndState(List.of(method), state).stream()
                .filter(p -> language.equals(p.getLanguage()))
                .findFirst()
                .orElse(null);
    }

    private void audit(String method, Language language, PromptAudit.Action action, int version) {
        if (auditService != null) {
            String user = extractUser();
            auditService.record(method, language, 0, version, action, user, null, null);
        }
    }

    @Autowired
    private jakarta.servlet.http.HttpServletRequest request;

    private String extractUser() {
        // Try Basic auth header (base64 decoded username)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)), StandardCharsets.UTF_8);
                return decoded.contains(":") ? decoded.substring(0, decoded.indexOf(":")) : decoded;
            } catch (Exception e) { log.warn("Failed to extract user from auth header", e); }
        }
        return request.getRemoteUser();
    }
}
