package ai.driftkit.workflow.engine.spring.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Auto-configuration for MongoDB repositories used by workflow engine.
 * This configuration is separate to avoid conflicts when multiple modules try to enable repositories.
 */
@Slf4j
@AutoConfiguration(
    after = {MongoDataAutoConfiguration.class, MongoRepositoriesAutoConfiguration.class},
    before = WorkflowTracingAutoConfiguration.class
)
@ConditionalOnClass(MongoTemplate.class)
@ConditionalOnProperty(
    prefix = "driftkit.workflow.mongodb",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableMongoRepositories(basePackages = {
    "ai.driftkit.workflow.engine.spring.tracing.repository"
})
public class WorkflowMongoRepositoriesAutoConfiguration {
    
    public WorkflowMongoRepositoriesAutoConfiguration() {
        log.info("Enabling MongoDB repositories for workflow engine tracing");
    }
}