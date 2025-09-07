package ai.driftkit.workflow.engine.spring.autoconfigure;

import ai.driftkit.workflow.engine.async.InMemoryProgressTracker;
import ai.driftkit.workflow.engine.async.ProgressTracker;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.persistence.*;
import ai.driftkit.workflow.engine.persistence.inmemory.*;
import ai.driftkit.common.service.ChatStore;
import ai.driftkit.common.service.TextTokenizer;
import ai.driftkit.common.service.impl.InMemoryChatStore;
import ai.driftkit.common.service.impl.SimpleTextTokenizer;
import ai.driftkit.workflow.engine.spring.controller.WorkflowController;
import ai.driftkit.workflow.engine.spring.service.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for DriftKit Workflow Engine.
 * 
 * <p>This configuration automatically sets up the workflow engine
 * with Spring integration when included in a Spring Boot application.</p>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(WorkflowEngine.class)
@EnableConfigurationProperties(WorkflowEngineProperties.class)
@ConditionalOnProperty(
    prefix = "driftkit.workflow.engine",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class WorkflowEngineAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public WorkflowStateRepository workflowStateRepository() {
        log.info("Configuring in-memory WorkflowStateRepository");
        return new InMemoryWorkflowStateRepository();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ProgressTracker progressTracker() {
        log.info("Configuring in-memory ProgressTracker");
        return new InMemoryProgressTracker();
    }
    
    
    @Bean(name = "workflowChatSessionRepository")
    @ConditionalOnMissingBean(name = "workflowChatSessionRepository")
    public ChatSessionRepository workflowChatSessionRepository() {
        log.info("Configuring in-memory ChatSessionRepository for workflow engine");
        return new InMemoryChatSessionRepository();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public TextTokenizer textTokenizer() {
        log.info("Configuring SimpleTextTokenizer");
        return new SimpleTextTokenizer();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ChatStore chatStore(TextTokenizer textTokenizer) {
        log.info("Configuring in-memory ChatStore");
        return new InMemoryChatStore(textTokenizer);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AsyncStepStateRepository asyncStepStateRepository() {
        log.info("Configuring in-memory AsyncStepStateRepository");
        return new InMemoryAsyncStepStateRepository();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public SuspensionDataRepository suspensionDataRepository() {
        log.info("Configuring in-memory SuspensionDataRepository");
        return new InMemorySuspensionDataRepository();
    }
    
    
    @Bean
    @ConditionalOnMissingBean
    public WorkflowEngine workflowEngine(
            WorkflowEngineProperties properties,
            WorkflowStateRepository stateRepository,
            ProgressTracker progressTracker,
            ChatSessionRepository workflowChatSessionRepository,
            ChatStore chatStore,
            AsyncStepStateRepository asyncStepStateRepository,
            SuspensionDataRepository suspensionDataRepository) {
        
        log.info("Configuring WorkflowEngine with properties: {}", properties);
        
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .coreThreads(properties.getCoreThreads())
            .maxThreads(properties.getMaxThreads())
            .scheduledThreads(properties.getScheduledThreads())
            .queueCapacity(properties.getQueueCapacity())
            .defaultStepTimeoutMs(properties.getDefaultStepTimeoutMs())
            .stateRepository(stateRepository)
            .progressTracker(progressTracker)
            .chatSessionRepository(workflowChatSessionRepository)
            .chatStore(chatStore)
            .asyncStepStateRepository(asyncStepStateRepository)
            .suspensionDataRepository(suspensionDataRepository)
            .build();
            
        return new WorkflowEngine(config);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public WorkflowService workflowService(
            WorkflowEngine engine,
            ChatSessionRepository workflowChatSessionRepository,
            AsyncStepStateRepository asyncStepStateRepository,
            SuspensionDataRepository suspensionDataRepository,
            WorkflowStateRepository workflowStateRepository,
            ChatStore chatStore) {
        log.info("Configuring WorkflowService");
        return new WorkflowService(engine, workflowChatSessionRepository, 
            asyncStepStateRepository, suspensionDataRepository, workflowStateRepository, chatStore);
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "driftkit.workflow.engine",
        name = "auto-register",
        havingValue = "true",
        matchIfMissing = true
    )
    public WorkflowBeanPostProcessor workflowBeanPostProcessor(WorkflowEngine engine) {
        log.info("Configuring WorkflowBeanPostProcessor for automatic workflow registration");
        return new WorkflowBeanPostProcessor(engine);
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(
        prefix = "driftkit.workflow.engine.controller",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public WorkflowController workflowController(
            WorkflowEngine engine,
            ProgressTracker progressTracker,
            WorkflowService workflowService) {
        log.info("Configuring WorkflowController");
        return new WorkflowController(engine, progressTracker, workflowService);
    }
}