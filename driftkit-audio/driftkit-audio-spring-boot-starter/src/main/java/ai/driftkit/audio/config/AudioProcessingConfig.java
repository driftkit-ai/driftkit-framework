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

    /**
     * Switches the audio auto-configuration on. Off by default because the session manager
     * creates the transcription engine at startup and requires a provider API key.
     */
    private boolean enabled = false;
}