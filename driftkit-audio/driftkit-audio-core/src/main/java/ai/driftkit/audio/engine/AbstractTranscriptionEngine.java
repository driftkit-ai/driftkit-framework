package ai.driftkit.audio.engine;

import ai.driftkit.audio.core.config.CoreAudioConfig;
import lombok.extern.slf4j.Slf4j;
import ai.driftkit.audio.model.TranscriptionResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Abstract base class for transcription engines providing common functionality.
 */
@Slf4j
public abstract class AbstractTranscriptionEngine implements TranscriptionEngine {
    
    protected final CoreAudioConfig config;
    protected final Map<String, StreamingSession> streamingSessions = new ConcurrentHashMap<>();
    
    protected AbstractTranscriptionEngine(CoreAudioConfig config) {
        this.config = config;
    }
    
    @Override
    public CompletableFuture<TranscriptionResult> transcribeBatch(
            byte[] audioData, 
            int sampleRate, 
            String languageCode) {
        
        if (!supportsBatchMode()) {
            throw new UnsupportedOperationException(
                getName() + " does not support batch transcription mode");
        }
        
        return doTranscribeBatch(audioData, sampleRate, languageCode);
    }
    
    @Override
    public void startStreamingSession(
            String sessionId,
            int sampleRate,
            String languageCode,
            Consumer<TranscriptionResult> resultCallback) {
        
        if (!supportsStreamingMode()) {
            throw new UnsupportedOperationException(
                getName() + " does not support streaming transcription mode");
        }
        
        if (streamingSessions.containsKey(sessionId)) {
            throw new IllegalStateException(
                "Streaming session already exists: " + sessionId);
        }
        
        StreamingSession session = createStreamingSession(
            sessionId, sampleRate, languageCode, resultCallback);
        streamingSessions.put(sessionId, session);
        
        log.debug("Started streaming session {} for engine {}", sessionId, getName());
    }
    
    @Override
    public void sendStreamingAudio(String sessionId, byte[] audioData) {
        if (!supportsStreamingMode()) {
            throw new UnsupportedOperationException(
                getName() + " does not support streaming transcription mode");
        }
        
        StreamingSession session = streamingSessions.get(sessionId);
        if (session == null) {
            throw new IllegalStateException(
                "No active streaming session found: " + sessionId);
        }
        
        session.sendAudio(audioData);
    }
    
    @Override
    public void stopStreamingSession(String sessionId) {
        if (!supportsStreamingMode()) {
            throw new UnsupportedOperationException(
                getName() + " does not support streaming transcription mode");
        }
        
        StreamingSession session = streamingSessions.remove(sessionId);
        if (session != null) {
            session.close();
            log.debug("Stopped streaming session {} for engine {}", sessionId, getName());
        }
    }
    
    @Override
    public boolean isStreamingSessionActive(String sessionId) {
        StreamingSession session = streamingSessions.get(sessionId);
        return session != null && session.isActive();
    }
    
    @Override
    public void shutdown() {
        // Close all active streaming sessions
        streamingSessions.values().forEach(StreamingSession::close);
        streamingSessions.clear();
        
        doShutdown();
        log.info("{} engine shut down", getName());
    }
    
    /**
     * Perform batch transcription implementation.
     */
    protected abstract CompletableFuture<TranscriptionResult> doTranscribeBatch(
            byte[] audioData, int sampleRate, String languageCode);
    
    /**
     * Create a new streaming session implementation.
     */
    protected abstract StreamingSession createStreamingSession(
            String sessionId, 
            int sampleRate, 
            String languageCode,
            Consumer<TranscriptionResult> resultCallback);
    
    /**
     * Perform engine-specific shutdown tasks.
     */
    protected abstract void doShutdown();
    
    /**
     * Interface for streaming session implementations.
     */
    protected interface StreamingSession {
        void sendAudio(byte[] audioData);
        void close();
        boolean isActive();
    }
}