package ai.driftkit.audio.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

import ai.driftkit.audio.config.AudioProcessingConfig;

/**
 * Auto-configuration for the audio processing library.
 * <p>
 * Activated only when {@code audio.processing.enabled=true}: the session manager creates the
 * transcription engine eagerly and fails without an API key, so the starter must not switch itself
 * on for every application that merely has it on the classpath.
 */
@org.springframework.boot.autoconfigure.AutoConfiguration
@ConditionalOnProperty(prefix = "audio.processing", name = "enabled", havingValue = "true")
@ComponentScan(basePackages = "ai.driftkit.audio")
@EnableConfigurationProperties(AudioProcessingConfig.class)
public class AutoConfiguration {
}
