package ai.driftkit.audio.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of audio buffer analysis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioAnalysis {
    private boolean isSilent;
    private double amplitude;
    
    /**
     * Check if this buffer contains voice activity
     */
    public boolean hasVoiceActivity(int voiceThreshold) {
        return amplitude > voiceThreshold;
    }
}