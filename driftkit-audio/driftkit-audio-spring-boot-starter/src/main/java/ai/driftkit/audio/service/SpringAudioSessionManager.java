package ai.driftkit.audio.service;

import ai.driftkit.audio.config.AudioProcessingConfig;
import ai.driftkit.audio.converter.AudioConverter;
import ai.driftkit.audio.core.AudioSessionManager;
import ai.driftkit.audio.engine.TranscriptionEngineFactory;
import ai.driftkit.audio.model.TranscriptionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Spring wrapper for core AudioSessionManager
 */
@Slf4j
@Service
public class SpringAudioSessionManager implements DisposableBean {

    private final AudioSessionManager sessionManager;

    @Autowired
    public SpringAudioSessionManager(
            AudioProcessingConfig config,
            TranscriptionEngineFactory engineFactory,
            AudioConverter audioConverter
    ) {
        this.sessionManager = new AudioSessionManager(config, engineFactory, audioConverter);
    }

    // Delegate all methods to core implementation
    public void createSession(String sessionId, Consumer<TranscriptionResult> callback) {
        sessionManager.createSession(sessionId, callback);
    }

    public void processAudioChunk(String sessionId, byte[] audioData) {
        sessionManager.processAudioChunk(sessionId, audioData);
    }

    public void closeSession(String sessionId) {
        sessionManager.closeSession(sessionId);
    }

    public boolean hasActiveSession(String sessionId) {
        return sessionManager.getActiveSessions().contains(sessionId);
    }

    public void closeAllSessions() {
        sessionManager.closeAllSessions();
    }

    @Override
    public void destroy() {
        log.info("Shutting down Spring Audio Session Manager");
        sessionManager.closeAllSessions();
    }
}