package ai.driftkit.workflow.controllers;

import ai.driftkit.clients.core.ModelClientFactory;
import ai.driftkit.common.domain.DictionaryItem;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.service.ChatStore;
import ai.driftkit.common.service.TextTokenizer;
import ai.driftkit.common.service.impl.InMemoryChatStore;
import ai.driftkit.common.service.impl.SimpleTextTokenizer;
import ai.driftkit.config.EtlConfig;
import ai.driftkit.common.domain.client.ModelImageRequest;
import ai.driftkit.common.domain.client.ModelImageResponse;
import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.context.core.service.DictionaryItemService;
import ai.driftkit.context.core.service.InMemoryPromptService;
import ai.driftkit.workflow.engine.agent.RequestTracingProvider;
import ai.driftkit.workflow.engine.core.WorkflowContextFactory;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.spring.context.SpringWorkflowContextFactory;
import ai.driftkit.workflow.engine.spring.tracing.SpringRequestTracingProvider;
import ai.driftkit.workflow.engine.spring.tracing.repository.CoreModelRequestTraceRepository;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.common.domain.Language;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.List;
import java.util.Optional;

/**
 * Test configuration providing necessary beans for integration testing.
 */
@TestConfiguration
@EnableAsync
public class TestApplicationConfiguration {
    
    @Value("${openai.api.key:test-key}")
    private String openAiApiKey;
    
    @Value("${openai.api.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;
    
    @Autowired(required = false)
    private CoreModelRequestTraceRepository modelRequestTraceRepository;
    
    @Bean
    @ConditionalOnMissingBean
    public EtlConfig etlConfig() {
        EtlConfig config = new EtlConfig();
        
        EtlConfig.VaultConfig vaultConfig = new EtlConfig.VaultConfig();
        vaultConfig.setName("openai");
        vaultConfig.setApiKey(openAiApiKey);
        vaultConfig.setBaseUrl(openAiBaseUrl);
        vaultConfig.setModel("gpt-3.5-turbo");
        
        config.setVault(java.util.List.of(vaultConfig));
        return config;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ModelClient modelClient() {
        EtlConfig.VaultConfig config = new EtlConfig.VaultConfig();
        config.setName("openai");
        config.setApiKey(openAiApiKey);
        config.setBaseUrl(openAiBaseUrl);
        config.setModel("gpt-3.5-turbo");
        return ModelClientFactory.fromConfig(config);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public TextTokenizer textTokenizer() {
        return new SimpleTextTokenizer();
    }
    
    
    @Bean
    @ConditionalOnMissingBean
    public ChatStore chatStore(TextTokenizer tokenizer) {
        return new InMemoryChatStore(tokenizer);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public RequestTracingProvider requestTracingProvider(@Autowired(required = false) AsyncTaskExecutor traceExecutor) {
        // Only create if MongoDB repository is available
        if (modelRequestTraceRepository != null && traceExecutor != null) {
            return new SpringRequestTracingProvider(modelRequestTraceRepository, traceExecutor);
        }
        // Return a no-op implementation if MongoDB is not available
        return new RequestTracingProvider() {
            @Override
            public void traceTextRequest(ModelTextRequest request, 
                                        ModelTextResponse response, 
                                        RequestContext context) {}
            
            @Override
            public void traceImageRequest(ModelImageRequest request, 
                                         ModelImageResponse response, 
                                         RequestContext context) {}
            
            @Override
            public void traceImageToTextRequest(ModelTextRequest request, 
                                               ModelTextResponse response, 
                                               RequestContext context) {}
        };
    }
    
    @Bean
    @ConditionalOnMissingBean
    public WorkflowContextFactory workflowContextFactory() {
        return new SpringWorkflowContextFactory();
    }
    
    // Note: ChatSessionRepository, AsyncStepStateRepository, SuspensionDataRepository, 
    // and WorkflowStateRepository will be provided by MongoDB when available
    
    @Bean
    @ConditionalOnMissingBean
    public WorkflowEngineConfig workflowEngineConfig() {
        return WorkflowEngineConfig.builder().build();
    }
    
    @Bean
    @ConditionalOnMissingBean
    @Primary
    public WorkflowEngine workflowEngine(
            WorkflowEngineConfig config) {
        return new WorkflowEngine(config);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public PromptService promptService() {
        return new PromptService(new InMemoryPromptService(), new DictionaryItemService() {
            @Override
            public Optional<DictionaryItem> findById(String id) {
                return Optional.empty();
            }

            @Override
            public List<DictionaryItem> findByLanguage(Language language) {
                return List.of();
            }

            @Override
            public List<DictionaryItem> findByGroupId(String groupId) {
                return List.of();
            }

            @Override
            public DictionaryItem save(DictionaryItem item) {
                return null;
            }

            @Override
            public List<DictionaryItem> saveAll(List<DictionaryItem> items) {
                return List.of();
            }

            @Override
            public void deleteById(String id) {

            }

            @Override
            public boolean existsById(String id) {
                return false;
            }

            @Override
            public List<DictionaryItem> findAll() {
                return List.of();
            }
        });
    }
    
    // Note: WorkflowService will be created by auto-configuration if needed
}