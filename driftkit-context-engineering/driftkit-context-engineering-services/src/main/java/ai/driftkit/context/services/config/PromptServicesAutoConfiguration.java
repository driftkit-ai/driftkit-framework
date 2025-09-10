package ai.driftkit.context.services.config;

import ai.driftkit.context.core.registry.PromptServiceRegistry;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.context.services.MongodbPromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Auto-configuration for DriftKit Context Engineering Services.
 * 
 * This configuration automatically sets up:
 * - Core prompt services (PromptServiceSpringAdapter, MongodbPromptService)
 * - MongoDB repositories for prompt persistence
 * - Registration with global PromptServiceRegistry
 */
@Slf4j
@AutoConfiguration
@ComponentScan(basePackages = "ai.driftkit.context.services")
@EnableMongoRepositories(basePackages = "ai.driftkit.context.services.repository")
@ConditionalOnClass(PromptService.class)
public class PromptServicesAutoConfiguration {
    
    @Autowired(required = false)
    private PromptService promptService;
    
    @Bean
    @ConditionalOnMissingBean(MongodbPromptService.class)
    public MongodbPromptService mongodbPromptService() {
        log.info("Creating MongodbPromptService bean");
        return new MongodbPromptService();
    }
    
    @PostConstruct
    public void registerPromptService() {
        if (promptService != null) {
            PromptServiceRegistry.register(promptService);
            log.info("Registered PromptService with global registry: {}", 
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