package ai.driftkit.context.spring.controller;

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
            @RequestBody Prompt promptData,
            @RequestParam(required = false) Boolean forceRepoVersion
    ) {
        Prompt result = promptService.createIfNotExists(
                promptData.getMethod(),
                promptData.getMessage(),
                promptData.getSystemMessage(),
                promptData.isJsonResponse(),
                promptData.getLanguage(),
                BooleanUtils.isTrue(forceRepoVersion),
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
}
