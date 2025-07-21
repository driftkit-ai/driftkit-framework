package ai.driftkit.context.spring.service;

import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.PromptServiceConfig;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.context.core.service.DictionaryItemService;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.context.core.service.PromptServiceBase;
import ai.driftkit.context.core.service.PromptServiceFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@Primary
public class PromptServiceSpringAdapter extends PromptService {
    
    @Autowired
    private EtlConfig etlConfig;
    
    private volatile boolean initialized = false;

    public PromptServiceSpringAdapter(@Autowired DictionaryItemService dictionaryItemService) {
        super(new PlaceholderPromptService(), dictionaryItemService);
    }

    @SneakyThrows
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (!initialized) {
            PromptServiceConfig promptServiceConfig = etlConfig.getPromptService();
            PromptServiceBase actualPromptService = PromptServiceFactory.fromName(
                promptServiceConfig.getName(), 
                promptServiceConfig.getConfig()
            );
            
            this.promptService = actualPromptService;
            initialized = true;
        }
    }
    
    private static class PlaceholderPromptService implements PromptServiceBase {
        @Override
        public void configure(Map<String, String> config) {}
        
        @Override
        public boolean supportsName(String name) { return false; }
        
        @Override
        public Optional<Prompt> getPromptById(String id) {
            return Optional.empty();
        }
        
        @Override
        public List<Prompt> getPromptsByIds(List<String> ids) {
            return Collections.emptyList();
        }
        
        @Override
        public List<Prompt> getPromptsByMethods(List<String> methods) {
            return Collections.emptyList();
        }
        
        @Override
        public List<Prompt> getPromptsByMethodsAndState(List<String> methods, Prompt.State state) {
            return Collections.emptyList();
        }
        
        @Override
        public List<Prompt> getPrompts() {
            return Collections.emptyList();
        }
        
        @Override
        public Prompt savePrompt(Prompt prompt) {
            return prompt;
        }
        
        @Override
        public Prompt deletePrompt(String id) {
            return null;
        }
        
        @Override
        public boolean isConfigured() { return false; }
    }
}