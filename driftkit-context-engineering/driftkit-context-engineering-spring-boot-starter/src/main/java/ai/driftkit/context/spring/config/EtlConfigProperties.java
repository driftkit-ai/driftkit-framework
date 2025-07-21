package ai.driftkit.context.spring.config;

import ai.driftkit.config.EtlConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
public class EtlConfigProperties {

    @Bean
    @ConditionalOnMissingBean
    @ConfigurationProperties(prefix = "driftkit")
    public EtlConfig etlConfig() {
        return new EtlConfig();
    }
}