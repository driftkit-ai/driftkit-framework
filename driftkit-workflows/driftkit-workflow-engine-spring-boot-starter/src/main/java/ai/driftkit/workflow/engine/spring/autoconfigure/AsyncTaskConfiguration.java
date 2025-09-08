package ai.driftkit.workflow.engine.spring.autoconfigure;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Spring Boot auto-configuration for async task execution.
 * Provides thread pool configuration for async LLM operations.
 */
@Slf4j
@AutoConfiguration
@EnableAsync
@ConditionalOnProperty(
    prefix = "driftkit.workflow.async",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(AsyncTaskConfiguration.AsyncTaskProperties.class)
public class AsyncTaskConfiguration {
    
    @Bean(name = "taskExecutor")
    @ConditionalOnMissingBean(name = "taskExecutor")
    public Executor taskExecutor(AsyncTaskProperties properties) {
        log.info("Configuring async task executor with core pool size: {}, max pool size: {}", 
                properties.getCorePoolSize(), properties.getMaxPoolSize());
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("async-task-");
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        executor.setAllowCoreThreadTimeOut(properties.isAllowCoreThreadTimeOut());
        
        // Rejection policy
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());
        
        executor.initialize();
        return executor;
    }
    
    /**
     * Configuration properties for async task execution
     */
    @Data
    @ConfigurationProperties(prefix = "driftkit.workflow.async")
    public static class AsyncTaskProperties {
        /**
         * Whether async task execution is enabled
         */
        private boolean enabled = true;
        
        /**
         * Core pool size for the thread pool
         */
        private int corePoolSize = 10;
        
        /**
         * Maximum pool size for the thread pool
         */
        private int maxPoolSize = 50;
        
        /**
         * Queue capacity for pending tasks
         */
        private int queueCapacity = 100;
        
        /**
         * Thread keep-alive time in seconds
         */
        private int keepAliveSeconds = 60;
        
        /**
         * Whether core threads are allowed to time out
         */
        private boolean allowCoreThreadTimeOut = false;
        
        /**
         * Time to wait for tasks to complete on shutdown (seconds)
         */
        private int awaitTerminationSeconds = 60;
    }
}