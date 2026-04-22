package ai.driftkit.context.spring.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class PromptAppController {

    // Forward all SPA routes to index.html (Vue Router handles client-side routing)
    @GetMapping({
        "/prompt-engineering",
        "/prompt-engineering/",
        "/prompt-engineering/{path:^(?!static|index\\.html).*$}",
        "/prompt-engineering/{path:^(?!static|index\\.html).*$}/**"
    })
    public String forwardToSpaIndex() {
        return "forward:/prompt-engineering/index.html";
    }
}