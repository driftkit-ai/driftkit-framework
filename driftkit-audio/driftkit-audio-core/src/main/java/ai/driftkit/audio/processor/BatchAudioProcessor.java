package ai.driftkit.audio.processor;

import ai.driftkit.audio.converter.AudioConverter;
import ai.driftkit.audio.core.AudioFormatType;
import ai.driftkit.audio.core.config.CoreAudioConfig;
import lombok.extern.slf4j.Slf4j;
import ai.driftkit.audio.engine.TranscriptionEngine;
import ai.driftkit.audio.model.AudioAnalysis;
import ai.driftkit.audio.model.TranscriptionResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Processor for batch mode audio transcription.
 * Accumulates audio chunks based on VAD and sends complete segments for transcription.
 */
@Slf4j
public class BatchAudioProcessor {
    
    private final String sessionId;
    private final CoreAudioConfig config;
    private final AudioAnalyzer audioAnalyzer;
    private final AudioConverter audioConverter;
    private final TranscriptionEngine engine;
    private final Consumer<TranscriptionResult> resultCallback;
    
    private final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
    private final AtomicBoolean isProcessing = new AtomicBoolean(true);
    private final AtomicLong lastSpeechTime = new AtomicLong(0);
    private final AtomicLong totalChunksProcessed = new AtomicLong(0);
    
    private boolean inSpeechSegment = false;
    private long segmentStartTime = 0;
    
    public BatchAudioProcessor(
            String sessionId,
            CoreAudioConfig config,
            AudioAnalyzer audioAnalyzer,
            AudioConverter audioConverter,
            TranscriptionEngine engine,
            Consumer<TranscriptionResult> resultCallback) {
        
        this.sessionId = sessionId;
        this.config = config;
        this.audioAnalyzer = audioAnalyzer;
        this.audioConverter = audioConverter;
        this.engine = engine;
        this.resultCallback = resultCallback;
    }
    
    /**
     * Process an audio chunk.
     */
    public void processAudioChunk(byte[] audioData) {
        if (!isProcessing.get()) {
            log.warn("Processor is stopped, ignoring audio chunk for session {}", sessionId);
            return;
        }
        
        totalChunksProcessed.incrementAndGet();
        
        // Analyze audio for voice activity
        AudioAnalysis analysis = audioAnalyzer.analyzeBuffer(audioData, audioData.length);
        
        if (!analysis.isSilent()) {
            handleSpeechDetected(audioData);
        } else {
            handleSilenceDetected();
        }
        
        // Debug output if enabled
        if (config.getDebug().isEnabled()) {
            saveDebugAudio(audioData);
        }
    }
    
    private void handleSpeechDetected(byte[] audioData) {
        lastSpeechTime.set(System.currentTimeMillis());
        
        if (!inSpeechSegment) {
            // Start new speech segment
            inSpeechSegment = true;
            segmentStartTime = System.currentTimeMillis();
            log.debug("Speech started in session {}", sessionId);
        }
        
        // Add audio to buffer
        try {
            audioBuffer.write(audioData);
        } catch (IOException e) {
            log.error("Failed to buffer audio data", e);
        }
    }
    
    private void handleSilenceDetected() {
        if (inSpeechSegment) {
            long silenceDuration = System.currentTimeMillis() - lastSpeechTime.get();
            
            if (silenceDuration >= config.getVad().getSilenceDurationMs()) {
                // End of speech segment detected
                finalizeSpeechSegment();
            }
        }
    }
    
    private void finalizeSpeechSegment() {
        inSpeechSegment = false;
        byte[] audioData = audioBuffer.toByteArray();
        audioBuffer.reset();
        
        long segmentDuration = System.currentTimeMillis() - segmentStartTime;
        log.debug("Speech segment ended in session {} after {}ms", sessionId, segmentDuration);
        
        // Check minimum duration
        if (segmentDuration < config.getMinChunkDurationSeconds() * 1000) {
            log.debug("Segment too short ({}ms), discarding", segmentDuration);
            return;
        }
        
        // Convert audio if needed
        byte[] processedAudio = audioData;
        if (engine.getConfiguration().isRequiresConversion()) {
            try {
                processedAudio = audioConverter.convertToFormat(
                    audioData,
                    config.getSampleRate(),
                    AudioFormatType.WAV
                );
            } catch (Exception e) {
                log.error("Failed to convert audio", e);
                return;
            }
        }
        
        // Send for transcription
        final byte[] finalAudio = processedAudio;
        engine.transcribeBatch(
            finalAudio,
            config.getSampleRate(),
            getLanguageCode()
        ).thenAccept(result -> {
            if (resultCallback != null) {
                resultCallback.accept(result);
            }
        }).exceptionally(throwable -> {
            log.error("Transcription failed for session {}", sessionId, throwable);
            if (resultCallback != null) {
                resultCallback.accept(TranscriptionResult.builder()
                    .error(true)
                    .errorMessage("Transcription failed: " + throwable.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build());
            }
            return null;
        });
    }
    
    /**
     * Force finalize any pending audio.
     */
    public void flush() {
        if (audioBuffer.size() > 0) {
            finalizeSpeechSegment();
        }
    }
    
    /**
     * Close this processor.
     */
    public void close() {
        isProcessing.set(false);
        flush();
        log.info("Batch processor closed for session {} after processing {} chunks", 
                sessionId, totalChunksProcessed.get());
    }
    
    private String getLanguageCode() {
        switch (config.getEngine()) {
            case ASSEMBLYAI:
                return config.getAssemblyai().getLanguageCode().getValue();
            case DEEPGRAM:
                return config.getDeepgram().getLanguage().getValue();
            default:
                return "en";
        }
    }
    
    private void saveDebugAudio(byte[] audioData) {
        try {
            String filename = String.format("%s/session_%s_chunk_%d.raw",
                config.getDebug().getOutputPath(), sessionId, totalChunksProcessed.get());
            
            File file = new File(filename);
            file.getParentFile().mkdirs();
            
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(audioData);
            }
        } catch (IOException e) {
            log.error("Failed to save debug audio", e);
        }
    }
}