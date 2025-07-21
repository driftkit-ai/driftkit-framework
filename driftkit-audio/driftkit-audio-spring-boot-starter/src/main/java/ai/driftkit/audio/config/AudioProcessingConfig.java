package ai.driftkit.audio.config;

import ai.driftkit.audio.core.config.CoreAudioConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for audio processing
 */
@Data
@ConfigurationProperties(prefix = "audio.processing")
public class AudioProcessingConfig extends CoreAudioConfig {
}