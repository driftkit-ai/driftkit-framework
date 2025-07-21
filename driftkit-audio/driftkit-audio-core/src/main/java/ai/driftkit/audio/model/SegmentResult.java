package ai.driftkit.audio.model;

import lombok.Data;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Result of a transcription segment update.
 * Contains only the current segment (since last final result).
 */
@Data
@AllArgsConstructor
public class SegmentResult {
    private String text;
    private double confidence;
    private boolean isFinal;
    private List<WordInfo> words;
}