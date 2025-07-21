package ai.driftkit.context.spring.config;

import ai.driftkit.config.EtlConfig;
import ai.driftkit.context.core.registry.PromptServiceRegistry;
import ai.driftkit.context.core.service.DictionaryItemService;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.context.spring.service.SpringDictionaryItemService;
import ai.driftkit.context.spring.service.PromptServiceSpringAdapter;
import ai.driftkit.context.spring.repository.SpringDictionaryItemRepository;
import ai.driftkit.contextengineering.autoconfigure.WebMvcConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration for DriftKit Context Engineering module.
 * 
 * This configuration automatically sets up:
 * - Component scanning for context engineering services
 * - MongoDB repositories for context persistence
 * - Default EtlConfig with prompt service configuration
 * - DictionaryItemService implementation
 * - PromptService implementation
 */
@Slf4j
@AutoConfiguration(after = ai.driftkit.config.autoconfigure.EtlConfigAutoConfiguration.class)
@ComponentScan(basePackages = {
    "ai.driftkit.context.spring.service",
    "ai.driftkit.context.spring.repository",
    "ai.driftkit.context.spring.config",
    "ai.driftkit.context.spring.controller",
    "ai.driftkit.context.spring.testsuite"
})
@EnableMongoRepositories(basePackages = {
    "ai.driftkit.context.spring.repository",
    "ai.driftkit.context.spring.testsuite.repository"
})
@Import(WebMvcConfiguration.class)
public class PromptServiceAutoConfiguration {
    
    private final PromptService promptService;
    
    public PromptServiceAutoConfiguration(@Autowired(required = false) PromptService promptService) {
        this.promptService = promptService;
    }
    
    @PostConstruct
    public void registerPromptService() {
        if (promptService != null) {
            PromptServiceRegistry.register(promptService);
            log.info("Auto-registered PromptService with global registry: {}", 
                    promptService.getClass().getSimpleName());
        }
    }
    
    @PreDestroy
    public void unregisterPromptService() {
        if (promptService != null) {
            PromptServiceRegistry.unregister();
            log.debug("Unregistered PromptService from global registry");
        }
    }
}