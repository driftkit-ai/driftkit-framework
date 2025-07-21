package ai.driftkit.audio.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a chunk of audio data with metadata
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioChunk {
    private byte[] data;
    private long startTime;
    private long endTime;
    private String transcription;
    private boolean endedOnSilence;

    public AudioChunk(byte[] data, long startTime, long endTime, boolean endedOnSilence) {
        this.data = data;
        this.startTime = startTime;
        this.endTime = endTime;
        this.endedOnSilence = endedOnSilence;
    }
    
    /**
     * Get duration in seconds
     */
    public double getDurationSeconds() {
        return (endTime - startTime) / 1000.0;
    }
    
    public boolean hasTranscription() {
        return transcription != null && !transcription.trim().isEmpty();
    }
}