package /*PACKAGE_NAME*/.controller;

import ai.driftkit.common.domain.CreatePromptRequest;
import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.context.core.service.PromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PromptController {

    private final PromptService promptService;

    @GetMapping
    public ResponseEntity<List<Prompt>> getAllPrompts() {
        List<Prompt> prompts = promptService.findAllPrompts();
        return ResponseEntity.ok(prompts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Prompt> getPrompt(@PathVariable String id,
                                          @RequestParam(defaultValue = "ENGLISH") Language language) {
        return promptService.findPromptById(id, language)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Prompt> createPrompt(@RequestBody CreatePromptRequest request) {
        try {
            Prompt prompt = promptService.createPrompt(request);
            return ResponseEntity.ok(prompt);
        } catch (Exception e) {
            log.error("Error creating prompt", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Prompt> updatePrompt(@PathVariable String id,
                                             @RequestBody Prompt prompt) {
        try {
            prompt.setId(id);
            Prompt updated = promptService.updatePrompt(prompt);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Error updating prompt", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePrompt(@PathVariable String id) {
        promptService.deletePrompt(id);
        return ResponseEntity.noContent().build();
    }
}