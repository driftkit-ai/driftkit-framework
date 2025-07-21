package ai.driftkit.audio.core.config;

import ai.driftkit.audio.core.ProcessingMode;
import lombok.Data;

/**
 * Core configuration for audio processing without Spring dependencies.
 * This is a simple POJO that can be used by the core module.
 */
@Data
public class CoreAudioConfig {
    
    /**
     * Transcription engine to use.
     * Default: ASSEMBLYAI
     */
    private EngineType engine = EngineType.ASSEMBLYAI;
    
    /**
     * Processing mode for transcription.
     * Default: BATCH
     */
    private ProcessingMode processingMode = ProcessingMode.BATCH;
    
    // Engine Configuration
    private AssemblyAIConfig assemblyai = new AssemblyAIConfig();
    private CoreDeepgramConfig deepgram = new CoreDeepgramConfig();
    
    // Audio Format Settings
    private int sampleRate = 16000;
    private int bufferSize = 4096;
    private int bufferSizeMs = 100;
    
    // Chunk Duration Settings
    private int maxChunkDurationSeconds = 60;
    private int minChunkDurationSeconds = 2;
    
    // Voice Activity Detection
    private VadConfig vad = new VadConfig();
    
    // Debug Settings
    private DebugConfig debug = new DebugConfig();
    
    // Performance and Resource Settings
    private int maxChunkSizeKb = 1024;
    private int maxBufferSizeMb = 10;
    private int processingTimeoutMs = 30000;
}