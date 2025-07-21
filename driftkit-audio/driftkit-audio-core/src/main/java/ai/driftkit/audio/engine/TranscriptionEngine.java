package ai.driftkit.audio.engine;

import ai.driftkit.audio.model.TranscriptionResult;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for transcription engines supporting both batch and streaming modes.
 * Implementations can provide either batch processing, streaming, or both.
 */
public interface TranscriptionEngine {
    
    /**
     * Get the name of this transcription engine.
     * @return Engine name (e.g., "AssemblyAI", "Deepgram")
     */
    String getName();
    
    /**
     * Check if this engine supports batch transcription.
     * @return true if batch mode is supported
     */
    boolean supportsBatchMode();
    
    /**
     * Check if this engine supports streaming transcription.
     * @return true if streaming mode is supported
     */
    boolean supportsStreamingMode();
    
    /**
     * Initialize the engine with configuration.
     * Called once when the engine is created.
     */
    void initialize();
    
    /**
     * Shutdown the engine and release resources.
     */
    void shutdown();
    
    /**
     * Transcribe audio data in batch mode.
     * 
     * @param audioData Audio data to transcribe
     * @param sampleRate Sample rate of the audio
     * @param languageCode Language code for transcription
     * @return CompletableFuture with transcription result
     * @throws UnsupportedOperationException if batch mode is not supported
     */
    CompletableFuture<TranscriptionResult> transcribeBatch(
            byte[] audioData, 
            int sampleRate, 
            String languageCode);
    
    /**
     * Start a streaming transcription session.
     * 
     * @param sessionId Unique session identifier
     * @param sampleRate Sample rate of the audio stream
     * @param languageCode Language code for transcription
     * @param resultCallback Callback for transcription results
     * @throws UnsupportedOperationException if streaming mode is not supported
     */
    void startStreamingSession(
            String sessionId,
            int sampleRate,
            String languageCode,
            Consumer<TranscriptionResult> resultCallback);
    
    /**
     * Send audio data to an active streaming session.
     * 
     * @param sessionId Session identifier
     * @param audioData Audio data chunk
     * @throws IllegalStateException if session doesn't exist
     * @throws UnsupportedOperationException if streaming mode is not supported
     */
    void sendStreamingAudio(String sessionId, byte[] audioData);
    
    /**
     * Stop a streaming transcription session.
     * 
     * @param sessionId Session identifier
     * @throws UnsupportedOperationException if streaming mode is not supported
     */
    void stopStreamingSession(String sessionId);
    
    /**
     * Check if a streaming session is active.
     * 
     * @param sessionId Session identifier
     * @return true if session is active
     */
    boolean isStreamingSessionActive(String sessionId);
    
    /**
     * Get configuration requirements for this engine.
     * @return Configuration metadata
     */
    EngineConfiguration getConfiguration();
}