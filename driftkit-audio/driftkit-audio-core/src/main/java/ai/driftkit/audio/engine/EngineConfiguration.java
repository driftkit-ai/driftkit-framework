package ai.driftkit.audio.engine;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Configuration metadata for transcription engines.
 */
@Data
@Builder
public class EngineConfiguration {
    
    /**
     * Engine type identifier.
     */
    private String engineType;
    
    /**
     * Required configuration keys and their descriptions.
     */
    private Map<String, String> requiredConfig;
    
    /**
     * Optional configuration keys and their descriptions.
     */
    private Map<String, String> optionalConfig;
    
    /**
     * Supported audio formats.
     */
    private AudioFormat supportedFormats;
    
    /**
     * Processing mode capabilities.
     */
    private ProcessingMode processingMode;
    
    /**
     * Maximum audio chunk size for streaming (in bytes).
     */
    private Integer maxStreamingChunkSize;
    
    /**
     * Recommended buffer size for streaming (in milliseconds).
     */
    private Integer recommendedBufferSizeMs;
    
    /**
     * Whether the engine requires audio format conversion.
     */
    private boolean requiresConversion;
    
    @Data
    @Builder
    public static class AudioFormat {
        private int[] supportedSampleRates;
        private int[] supportedChannels;
        private int[] supportedBitsPerSample;
        private String[] supportedEncodings;
    }
    
    public enum ProcessingMode {
        BATCH_ONLY,
        STREAMING_ONLY,
        BOTH
    }
}