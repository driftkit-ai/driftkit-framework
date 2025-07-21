package ai.driftkit.context.spring.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class PromptAppController {

    // Redirects all routes from /prompt-engineering/* to the SPA index
    @GetMapping({
        "/prompt-engineering",
        "/prompt-engineering/",
        "/prompt-engineering/chat/**",
        "/prompt-engineering/prompts/**", 
        "/prompt-engineering/indexes/**",
        "/prompt-engineering/dictionaries/**", 
        "/prompt-engineering/checklists/**"
    })
    public String forwardToSpaIndex() {
        return "forward:/prompt-engineering/index.html";
    }

    // Fallback route for any paths not explicitly handled
    @GetMapping("/{path:^(?!api|data|prompt-engineering).*$}")
    public String redirect() {
        return "forward:/";
    }
}