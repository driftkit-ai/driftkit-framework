package /*PACKAGE_NAME*/.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "/*PACKAGE_NAME*/.repository")
@EnableMongoAuditing
public class MongoConfig {
    // MongoDB configuration is handled by Spring Boot auto-configuration
    // This class enables MongoDB repositories and auditing
}