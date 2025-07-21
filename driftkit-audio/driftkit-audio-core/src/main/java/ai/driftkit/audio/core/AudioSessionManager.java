package ai.driftkit.audio.core;

import ai.driftkit.audio.converter.AudioConverter;
import ai.driftkit.audio.core.config.CoreAudioConfig;
import ai.driftkit.audio.engine.TranscriptionEngine;
import ai.driftkit.audio.engine.TranscriptionEngineFactory;
import ai.driftkit.audio.model.TranscriptionResult;
import ai.driftkit.audio.processor.AudioAnalyzer;
import ai.driftkit.audio.processor.BatchAudioProcessor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Audio session manager that supports multiple transcription engines
 * and both batch and streaming processing modes.
 */
@Slf4j
public class AudioSessionManager {

    private final CoreAudioConfig config;
    private final TranscriptionEngine engine;
    private final AudioConverter audioConverter;

    // For batch mode processing
    private final ConcurrentMap<String, BatchAudioProcessor> batchProcessors = new ConcurrentHashMap<>();

    // For streaming mode - callbacks are managed by the engine
    private final ConcurrentMap<String, Consumer<TranscriptionResult>> streamingCallbacks = new ConcurrentHashMap<>();

    public AudioSessionManager(
            CoreAudioConfig config,
            TranscriptionEngineFactory engineFactory,
            AudioConverter audioConverter) {

        this.config = config;
        this.audioConverter = audioConverter;

        // Create engine based on configuration
        this.engine = engineFactory.createEngine();

        log.info("Enhanced audio session manager initialized with {} engine in {} mode",
                engine.getName(), config.getProcessingMode());
    }

    /**
     * Create a new audio processing session.
     *
     * @param sessionId      Unique session identifier
     * @param resultCallback Callback for transcription results
     */
    public void createSession(String sessionId, Consumer<TranscriptionResult> resultCallback) {
        if (hasSession(sessionId)) {
            throw new IllegalArgumentException("Session already exists: " + sessionId);
        }

        switch (config.getProcessingMode()) {
            case STREAMING:
                // For streaming mode, start a streaming session with the engine
                streamingCallbacks.put(sessionId, resultCallback);
                engine.startStreamingSession(
                        sessionId,
                        config.getSampleRate(),
                        getLanguageCode(),
                        resultCallback
                );
                log.debug("Created streaming session: {}", sessionId);
                break;
            case BATCH:
                // For batch mode, create a batch processor with its own AudioAnalyzer
                AudioAnalyzer sessionAnalyzer = new AudioAnalyzer(config);
                BatchAudioProcessor processor = new BatchAudioProcessor(
                        sessionId, config, sessionAnalyzer, audioConverter, engine, resultCallback);
                batchProcessors.put(sessionId, processor);
                log.debug("Created batch session: {}", sessionId);
                break;
        }
    }

    /**
     * Process audio chunk for a session.
     *
     * @param sessionId Session identifier
     * @param audioData Audio data to process
     */
    public void processAudioChunk(String sessionId, byte[] audioData) {
        switch (config.getProcessingMode()) {
            case STREAMING:
                // For streaming mode, send directly to engine
                engine.sendStreamingAudio(sessionId, audioData);
                break;
            case BATCH:
                // For batch mode, use the batch processor
                BatchAudioProcessor processor = batchProcessors.get(sessionId);
                if (processor == null) {
                    throw new IllegalArgumentException("No active session found: " + sessionId);
                }
                processor.processAudioChunk(audioData);
                break;
        }
    }

    /**
     * Check if a session exists.
     *
     * @param sessionId Session identifier
     * @return true if session exists
     */
    public boolean hasSession(String sessionId) {
        return batchProcessors.containsKey(sessionId) ||
                engine.isStreamingSessionActive(sessionId);
    }

    /**
     * Close a session.
     *
     * @param sessionId Session identifier
     */
    public void closeSession(String sessionId) {
        // Close streaming session if exists
        if (engine.isStreamingSessionActive(sessionId)) {
            engine.stopStreamingSession(sessionId);
            streamingCallbacks.remove(sessionId);
            log.debug("Closed streaming session: {}", sessionId);
        }

        // Close batch processor if exists
        BatchAudioProcessor processor = batchProcessors.remove(sessionId);
        if (processor != null) {
            processor.close();
            log.debug("Closed batch session: {}", sessionId);
        }
    }

    /**
     * Get all active session IDs.
     *
     * @return Set of active session IDs
     */
    public Set<String> getActiveSessions() {
        Set<String> sessions = new java.util.HashSet<>();
        sessions.addAll(batchProcessors.keySet());
        sessions.addAll(streamingCallbacks.keySet());
        return sessions;
    }

    /**
     * Close all active sessions.
     */
    public void closeAllSessions() {
        // Close all batch sessions
        batchProcessors.forEach((id, processor) -> processor.close());
        batchProcessors.clear();

        // Close all streaming sessions
        streamingCallbacks.keySet().forEach(engine::stopStreamingSession);
        streamingCallbacks.clear();

        log.info("All audio sessions closed");
    }

    public void shutdown() {
        closeAllSessions();
        engine.shutdown();
        log.info("Enhanced audio session manager shut down");
    }

    private String getLanguageCode() {
        // Get language code based on engine type
        switch (config.getEngine()) {
            case ASSEMBLYAI:
                return config.getAssemblyai().getLanguageCode().getValue();
            case DEEPGRAM:
                return config.getDeepgram().getLanguage().getValue();
            default:
                return "en"; // Default
        }
    }

    /**
     * Get session statistics (placeholder for future implementation).
     */
    public SessionStats getSessionStats(String sessionId) {
        // TODO: Implement session statistics
        return new SessionStats();
    }

    public static class SessionStats {
        // Placeholder for session statistics
        private long chunksProcessed;
        private long bytesProcessed;
        private long transcriptionsReceived;

        // Getters/setters would be here
    }
}