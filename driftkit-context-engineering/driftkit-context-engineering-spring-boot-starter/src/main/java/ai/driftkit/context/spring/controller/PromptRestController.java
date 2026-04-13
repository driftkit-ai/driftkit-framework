package ai.driftkit.context.spring.controller;

import ai.driftkit.common.domain.CreatePromptRequest;
import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.common.domain.RestResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Controller
@RequestMapping(path = "/data/v1.0/admin/prompt/")
public class PromptRestController {

    @Autowired
    private PromptService promptService;

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
}
