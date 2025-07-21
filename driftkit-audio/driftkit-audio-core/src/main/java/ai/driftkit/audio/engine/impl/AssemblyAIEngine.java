package ai.driftkit.audio.engine.impl;

import com.assemblyai.api.AssemblyAI;
import com.assemblyai.api.resources.files.types.UploadedFile;
import com.assemblyai.api.resources.transcripts.types.*;
import lombok.extern.slf4j.Slf4j;
import ai.driftkit.audio.core.config.CoreAudioConfig;
import ai.driftkit.audio.engine.AbstractTranscriptionEngine;
import ai.driftkit.audio.engine.EngineConfiguration;
import ai.driftkit.audio.model.TranscriptionResult;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * AssemblyAI transcription engine implementation.
 * Supports batch transcription mode only.
 */
@Slf4j
public class AssemblyAIEngine extends AbstractTranscriptionEngine {
    
    private static final String ENGINE_NAME = "AssemblyAI";
    private AssemblyAI client;
    
    public AssemblyAIEngine(CoreAudioConfig config) {
        super(config);
    }
    
    @Override
    public String getName() {
        return ENGINE_NAME;
    }
    
    @Override
    public boolean supportsBatchMode() {
        return true;
    }
    
    @Override
    public boolean supportsStreamingMode() {
        return false; // AssemblyAI supports real-time streaming, but English only
    }
    
    @Override
    public void initialize() {
        String apiKey = config.getAssemblyai().getApiKey();
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("AssemblyAI API key is not configured");
        }
        
        this.client = AssemblyAI.builder()
                .apiKey(apiKey)
                .build();
        
        log.info("AssemblyAI engine initialized");
    }
    
    @Override
    protected CompletableFuture<TranscriptionResult> doTranscribeBatch(
            byte[] audioData, int sampleRate, String languageCode) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Upload audio data directly
                UploadedFile uploadedFile = client.files().upload(audioData);
                
                // Configure transcription  
                String effectiveLanguage = languageCode != null ? languageCode : config.getAssemblyai().getLanguageCode().getValue();
                TranscriptOptionalParams params = TranscriptOptionalParams.builder()
                        .languageCode(TranscriptLanguageCode.valueOf(effectiveLanguage.toUpperCase()))
                        .build();
                
                // Submit transcription and wait for completion
                Transcript transcript = client.transcripts().transcribe(uploadedFile.getUploadUrl(), params);
                
                // Build result
                return buildTranscriptionResult(transcript);
                
            } catch (Exception e) {
                log.error("AssemblyAI transcription failed", e);
                return TranscriptionResult.builder()
                        .error(true)
                        .errorMessage("Transcription failed: " + e.getMessage())
                        .timestamp(System.currentTimeMillis())
                        .build();
            }
        });
    }
    
    @Override
    protected StreamingSession createStreamingSession(
            String sessionId, 
            int sampleRate, 
            String languageCode,
            Consumer<TranscriptionResult> resultCallback) {
        
        throw new UnsupportedOperationException(
            "AssemblyAI does not support streaming transcription");
    }
    
    @Override
    protected void doShutdown() {
        // AssemblyAI client doesn't require explicit shutdown
    }
    
    @Override
    public EngineConfiguration getConfiguration() {
        Map<String, String> requiredConfig = new HashMap<>();
        requiredConfig.put("audio.processing.assemblyai.api-key", "AssemblyAI API key");
        
        Map<String, String> optionalConfig = new HashMap<>();
        optionalConfig.put("audio.processing.assemblyai.language-code", "Language code (default: en)");
        
        return EngineConfiguration.builder()
                .engineType(ENGINE_NAME)
                .requiredConfig(requiredConfig)
                .optionalConfig(optionalConfig)
                .processingMode(EngineConfiguration.ProcessingMode.BATCH_ONLY)
                .supportedFormats(EngineConfiguration.AudioFormat.builder()
                        .supportedSampleRates(new int[]{8000, 16000, 22050, 44100, 48000})
                        .supportedChannels(new int[]{1, 2})
                        .supportedBitsPerSample(new int[]{16})
                        .supportedEncodings(new String[]{"PCM", "WAV", "MP3", "M4A"})
                        .build())
                .requiresConversion(true)
                .build();
    }
    
    private TranscriptionResult buildTranscriptionResult(Transcript transcript) {
        if (transcript.getStatus() == TranscriptStatus.ERROR) {
            return TranscriptionResult.builder()
                    .error(true)
                    .errorMessage(transcript.getError().orElse("Unknown error"))
                    .timestamp(System.currentTimeMillis())
                    .build();
        }
        
        return TranscriptionResult.builder()
                .text(transcript.getText().orElse(""))
                .confidence(transcript.getConfidence().orElse(0.0))
                .language(transcript.getLanguageCode().map(Object::toString).orElse("unknown"))
                .timestamp(System.currentTimeMillis())
                .error(false)
                .metadata(buildMetadata(transcript))
                .build();
    }
    
    private Map<String, Object> buildMetadata(Transcript transcript) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("transcriptId", transcript.getId());
        metadata.put("duration", transcript.getAudioDuration());
        metadata.put("words", transcript.getWords());
        return metadata;
    }
}