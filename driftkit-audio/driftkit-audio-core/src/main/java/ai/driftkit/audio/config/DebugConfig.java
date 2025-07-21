package ai.driftkit.audio.config;

import lombok.Data;

/**
 * Configuration for debug and development features.
 */
@Data
public class DebugConfig {
    
    /**
     * Enable debug mode.
     * Default: false
     */
    private boolean enabled = false;
    
    /**
     * Path for saving debug audio files.
     * Default: "./debug/audio"
     */
    private String outputPath = "./debug/audio";
    
    /**
     * Save raw PCM audio chunks.
     * Default: false
     */
    private boolean saveRawAudio = false;
    
    /**
     * Save processed/converted audio.
     * Default: true
     */
    private boolean saveProcessedAudio = true;
}