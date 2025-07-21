package ai.driftkit.audio.engine;

import ai.driftkit.audio.config.AudioProcessingConfig;
import ai.driftkit.audio.core.config.CoreAudioConfig;
import ai.driftkit.audio.core.config.EngineType;
import ai.driftkit.audio.engine.impl.AssemblyAIEngine;
import ai.driftkit.audio.engine.impl.DeepgramEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating transcription engine instances based on configuration.
 */
@Slf4j
@Component
public class SpringTranscriptionEngineFactory extends TranscriptionEngineFactory {
    
    public SpringTranscriptionEngineFactory(CoreAudioConfig config) {
        super(config);
    }
    
}