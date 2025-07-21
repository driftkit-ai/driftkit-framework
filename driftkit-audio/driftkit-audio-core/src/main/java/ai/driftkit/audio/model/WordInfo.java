package ai.driftkit.audio.model;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a single word with timing and confidence information.
 */
@Data
@Builder
public class WordInfo {
    private String word;
    private String punctuatedWord;
    private double start;
    private double end;
    private double confidence;
    private String language;
}