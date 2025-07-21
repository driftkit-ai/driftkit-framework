package ai.driftkit.audio.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Result of audio transcription
 */
@Data
@Builder
public class TranscriptionResult {
    private String text;
    private Double confidence;
    private Long timestamp;
    private String language;
    private boolean error;
    private String errorMessage;
    private Map<String, Object> metadata;
    private boolean interim;
    private List<WordInfo> words;
    private String mergedTranscript;
    
    public static TranscriptionResult success(String text, double confidence, String language) {
        return TranscriptionResult.builder()
            .text(text)
            .confidence(confidence)
            .language(language)
            .timestamp(System.currentTimeMillis())
            .error(false)
            .build();
    }
    
    public static TranscriptionResult error(String errorMessage) {
        return TranscriptionResult.builder()
            .error(true)
            .errorMessage(errorMessage)
            .timestamp(System.currentTimeMillis())
            .build();
    }
}