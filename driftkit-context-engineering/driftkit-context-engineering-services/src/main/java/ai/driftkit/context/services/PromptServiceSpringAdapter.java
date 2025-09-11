package ai.driftkit.context.services;

import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.PromptServiceConfig;
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

@Slf4j
@Service
@Primary
public class PromptServiceSpringAdapter extends PromptService {
    
    @Autowired
    private EtlConfig etlConfig;
    
    private volatile boolean initialized = false;

    public PromptServiceSpringAdapter(@Autowired DictionaryItemService dictionaryItemService) {
        super(null, dictionaryItemService);
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
            
            log.info("Initialized PromptServiceSpringAdapter with {} prompt service", 
                promptServiceConfig.getName());
        }
    }

    @Override
    public boolean isConfigured() {
        return initialized;
    }
}