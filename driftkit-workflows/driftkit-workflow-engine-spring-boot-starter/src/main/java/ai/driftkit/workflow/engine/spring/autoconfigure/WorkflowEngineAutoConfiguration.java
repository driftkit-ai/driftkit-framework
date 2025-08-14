package ai.driftkit.workflow.engine.spring.autoconfigure;

import ai.driftkit.workflow.engine.async.InMemoryProgressTracker;
import ai.driftkit.workflow.engine.async.ProgressTracker;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.persistence.*;
import ai.driftkit.workflow.engine.memory.WorkflowMemoryConfiguration;
import ai.driftkit.workflow.engine.persistence.inmemory.*;
import ai.driftkit.workflow.engine.schema.DefaultSchemaProvider;
import ai.driftkit.workflow.engine.schema.SchemaProvider;
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
    
    @Bean
    @ConditionalOnMissingBean
    public SchemaProvider schemaProvider() {
        log.info("Configuring DefaultSchemaProvider");
        return new DefaultSchemaProvider();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ChatSessionRepository chatSessionRepository() {
        log.info("Configuring in-memory ChatSessionRepository");
        return new InMemoryChatSessionRepository();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ChatHistoryRepository chatHistoryRepository() {
        log.info("Configuring in-memory ChatHistoryRepository");
        return new InMemoryChatHistoryRepository();
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
    public MemoryManagementService memoryManagementService(
            ChatSessionRepository chatSessionRepository,
            ChatHistoryRepository chatHistoryRepository,
            AsyncStepStateRepository asyncStepStateRepository,
            SuspensionDataRepository suspensionDataRepository) {
        log.info("Configuring MemoryManagementService");
        // Use default memory configuration
        return new MemoryManagementService(
            chatSessionRepository,
            chatHistoryRepository,
            asyncStepStateRepository,
            suspensionDataRepository,
            WorkflowMemoryConfiguration.createDefault(chatHistoryRepository)
        );
    }
    
    @Bean
    @ConditionalOnMissingBean
    public WorkflowEngine workflowEngine(
            WorkflowEngineProperties properties,
            WorkflowStateRepository stateRepository,
            ProgressTracker progressTracker,
            SchemaProvider schemaProvider,
            ChatSessionRepository chatSessionRepository,
            ChatHistoryRepository chatHistoryRepository,
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
            .schemaProvider(schemaProvider)
            .chatSessionRepository(chatSessionRepository)
            .chatHistoryRepository(chatHistoryRepository)
            .asyncStepStateRepository(asyncStepStateRepository)
            .suspensionDataRepository(suspensionDataRepository)
            .build();
            
        return new WorkflowEngine(config);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public WorkflowService workflowService(
            WorkflowEngine engine,
            SchemaProvider schemaProvider,
            MemoryManagementService memoryManagementService) {
        log.info("Configuring WorkflowService");
        return new WorkflowService(engine, schemaProvider, memoryManagementService);
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
            SchemaProvider schemaProvider,
            ProgressTracker progressTracker,
            WorkflowService workflowService) {
        log.info("Configuring WorkflowController");
        return new WorkflowController(engine, schemaProvider, progressTracker, workflowService);
    }
}