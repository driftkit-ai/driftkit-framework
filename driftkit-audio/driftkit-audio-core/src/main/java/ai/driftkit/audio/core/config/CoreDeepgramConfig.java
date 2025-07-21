package ai.driftkit.audio.core.config;

import ai.driftkit.common.domain.Language;
import lombok.Data;

/**
 * Core configuration for Deepgram transcription service without Spring dependencies.
 */
@Data
public class CoreDeepgramConfig {
    
    /**
     * Deepgram API key.
     */
    private String apiKey;
    
    /**
     * Language code for transcription.
     * Default: ENGLISH
     */
    private Language language = Language.ENGLISH;
    
    /**
     * Deepgram model to use.
     * Options: "nova-2", "nova", "enhanced", "base"
     * Default: "nova-2"
     */
    private String model = "nova-2";
    
    /**
     * Whether to add punctuation to transcriptions.
     * Default: true
     */
    private boolean punctuate = true;
    
    /**
     * Whether to enable interim results for streaming.
     * Default: true
     */
    private boolean interimResults = true;
    
    /**
     * Whether to enable automatic language detection.
     * Default: false
     */
    private boolean detectLanguage = false;
    
    /**
     * Whether to enable speaker diarization.
     * Default: false
     */
    private boolean diarize = false;
    
    /**
     * Number of speakers to detect if diarization is enabled.
     * Default: 2
     */
    private int diarizeVersion = 2;
    
    /**
     * Whether to enable profanity filtering.
     * Default: false
     */
    private boolean profanityFilter = false;
    
    /**
     * Whether to enable redaction of sensitive information.
     * Default: false
     */
    private boolean redact = false;
    
    /**
     * Smart formatting options.
     * Default: false
     */
    private boolean smartFormat = false;
}