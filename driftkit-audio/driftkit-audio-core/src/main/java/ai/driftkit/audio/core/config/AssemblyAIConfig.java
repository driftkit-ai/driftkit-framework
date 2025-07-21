package ai.driftkit.audio.core.config;

import ai.driftkit.common.domain.Language;
import lombok.Data;

/**
 * Configuration for AssemblyAI transcription service.
 */
@Data
public class AssemblyAIConfig {
    /**
     * AssemblyAI API key.
     */
    private String apiKey;
    
    /**
     * Language code for transcription.
     * Default: ENGLISH
     */
    private Language languageCode = Language.ENGLISH;
}