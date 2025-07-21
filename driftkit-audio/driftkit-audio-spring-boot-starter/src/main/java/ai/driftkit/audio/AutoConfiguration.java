package ai.driftkit.audio;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import ai.driftkit.audio.config.AudioProcessingConfig;

/**
 * Auto-configuration for audio processing library
 */
@Configuration
@ComponentScan(basePackages = "ai.driftkit.audio")
@EnableConfigurationProperties(AudioProcessingConfig.class)
public class AutoConfiguration {
}