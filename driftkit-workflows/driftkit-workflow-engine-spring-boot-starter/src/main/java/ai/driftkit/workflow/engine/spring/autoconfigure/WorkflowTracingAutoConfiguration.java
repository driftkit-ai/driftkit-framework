package ai.driftkit.workflow.engine.spring.autoconfigure;

import ai.driftkit.workflow.engine.agent.NoOpRequestTracingProvider;
import ai.driftkit.workflow.engine.agent.RequestTracingProvider;
import ai.driftkit.workflow.engine.spring.tracing.SpringRequestTracingProvider;
import ai.driftkit.workflow.engine.spring.tracing.repository.CoreModelRequestTraceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Spring Boot auto-configuration for workflow tracing functionality.
 * Provides automatic setup of request tracing with MongoDB persistence.
 */
@Slf4j
@AutoConfiguration(after = MongoDataAutoConfiguration.class)
@ConditionalOnClass({RequestTracingProvider.class, CoreModelRequestTraceRepository.class})
@ConditionalOnProperty(
    prefix = "driftkit.workflow.tracing",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(WorkflowTracingProperties.class)
@ComponentScan(basePackages = "ai.driftkit.workflow.engine.spring.tracing")
public class WorkflowTracingAutoConfiguration {
    
    @Bean(name = "traceExecutor")
    @ConditionalOnMissingBean(name = "traceExecutor")
    public Executor traceExecutor(WorkflowTracingProperties properties) {
        log.info("Configuring trace executor with {} threads", properties.getTraceThreads());
        return Executors.newFixedThreadPool(properties.getTraceThreads());
    }
    
    @Bean
    @ConditionalOnMissingBean(RequestTracingProvider.class)
    @ConditionalOnBean(CoreModelRequestTraceRepository.class)
    public RequestTracingProvider requestTracingProvider(
            CoreModelRequestTraceRepository repository,
            Executor traceExecutor) {
        log.info("Configuring SpringRequestTracingProvider for workflow tracing");
        return new SpringRequestTracingProvider(repository, traceExecutor);
    }
    
    @Bean
    @ConditionalOnMissingBean(RequestTracingProvider.class)
    public RequestTracingProvider noOpRequestTracingProvider() {
        log.info("MongoDB not available or tracing disabled - using NoOpRequestTracingProvider");
        return new NoOpRequestTracingProvider();
    }
}