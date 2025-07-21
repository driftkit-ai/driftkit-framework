package ai.driftkit.audio.service;

import ai.driftkit.audio.converter.AudioConverter;
import ai.driftkit.audio.core.config.CoreAudioConfig;
import lombok.extern.slf4j.Slf4j;
import ai.driftkit.audio.config.AudioProcessingConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for converting audio formats using Java libraries and FFmpeg fallback
 */
@Slf4j
@Service
public class SpringAudioConverter extends AudioConverter {

    @Autowired
    public SpringAudioConverter(CoreAudioConfig config) {
        super(config);
    }
}