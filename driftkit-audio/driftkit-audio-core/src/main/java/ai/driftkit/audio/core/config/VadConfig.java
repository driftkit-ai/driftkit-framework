package ai.driftkit.audio.core.config;

import lombok.Data;

/**
 * Configuration for Voice Activity Detection (VAD).
 */
@Data
public class VadConfig {
    
    /**
     * Enable/disable VAD.
     * Default: true
     */
    private boolean enabled = true;
    
    /**
     * Energy threshold for speech detection (0.0-1.0).
     * Lower values are more sensitive.
     * Default: 0.005
     */
    private double threshold = 0.005;
    
    /**
     * Minimum duration of speech to consider (milliseconds).
     * Default: 250ms
     */
    private int minSpeechDurationMs = 250;
    
    /**
     * Duration of silence before finalizing chunk (milliseconds).
     * Default: 1000ms
     */
    private int silenceDurationMs = 1000;
    
    /**
     * Enable adaptive threshold adjustment.
     * Default: true
     */
    private boolean adaptiveThreshold = true;
    
    /**
     * Base noise level for adaptive threshold.
     * Default: 0.001
     */
    private double noiseLevel = 0.001;
}