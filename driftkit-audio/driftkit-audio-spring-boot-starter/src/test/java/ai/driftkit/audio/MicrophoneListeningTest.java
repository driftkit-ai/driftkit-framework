package ai.driftkit.audio;

import ai.driftkit.audio.config.AudioProcessingConfig;
import ai.driftkit.audio.core.config.*;
import ai.driftkit.audio.core.ProcessingMode;
import ai.driftkit.audio.service.AudioSessionManager;
import ai.driftkit.audio.converter.AudioConverter;
import ai.driftkit.audio.engine.SpringTranscriptionEngineFactory;
import ai.driftkit.audio.model.TranscriptionResult;
import ai.driftkit.common.domain.Language;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Optional microphone listening tests.
 * These tests are disabled by default and should be run manually.
 * 
 * To run these tests:
 * 1. Remove @Disabled annotation from the test you want to run
 * 2. Make sure microphone is available and working
 * 3. Run the specific test method
 * 
 * Each test listens for 30 seconds and then stops automatically.
 * 
 * The streaming test demonstrates the new segmented transcription features:
 * - Interim results show live transcription updates with word-level timing
 * - Final results contain completed segments that should be saved
 * - Word deduplication prevents memory overflow in long sessions
 * - Only current segment data is returned (not full accumulated text)
 */
public class MicrophoneListeningTest {
    
    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = 4096;
    private static final int LISTENING_DURATION_SECONDS = 180;
    
    private AudioSessionManager sessionManager;
    private AudioFormat audioFormat;
    
    @BeforeEach
    void setUp() {
        // Create audio format for microphone capture
        audioFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            SAMPLE_RATE,
            16, // bits per sample
            1,  // channels (mono)
            2,  // frame size (16 bits = 2 bytes)
            SAMPLE_RATE,
            false // little-endian
        );
    }
    
    /**
     * Test streaming mode with real microphone input.
     * This test captures audio from the microphone and sends it to Deepgram streaming API.
     * 
     * Prerequisites:
     * - Microphone must be available
     * - Internet connection required
     */
    @Test
    @Disabled("Manual test - remove @Disabled to run with real microphone")
    void testMicrophoneStreamingMode() throws Exception {
        System.out.println("=== Microphone Streaming Test ===");
        System.out.println("Starting 10-second microphone capture for streaming transcription...");
        System.out.println("Speak into your microphone now!");
        
        // Configure for streaming mode
        AudioProcessingConfig config = createStreamingConfig();
        sessionManager = createSessionManager(config);
        
        String sessionId = "microphone-streaming-test";
        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicInteger resultCount = new AtomicInteger(0);

        var list = new ArrayList<TranscriptionResult>();
        
        // Create session with result callback
        sessionManager.createSession(sessionId, result -> {
            if (!result.isError()) {
                int count = resultCount.incrementAndGet();
                
                if (result.isInterim()) {
                    // Interim result - show live transcription
                    System.out.printf("[Interim #%d] %s (confidence: %.2f)%n", 
                        count, result.getMergedTranscript(), result.getConfidence());
                    
                    // Show word details for interim results
                    if (result.getWords() != null && !result.getWords().isEmpty()) {
                        System.out.printf("  Words: ");
                        for (ai.driftkit.audio.model.WordInfo word : result.getWords()) {
                            System.out.printf("%s[%.1f-%.1fs] ", 
                                word.getPunctuatedWord() != null ? word.getPunctuatedWord() : word.getWord(),
                                word.getStart(), word.getEnd());
                        }
                        System.out.println();
                    }
                } else {
                    // Final result - save this segment
                    System.out.printf("[FINAL #%d] %s (confidence: %.2f)%n", 
                        count, result.getMergedTranscript(), result.getConfidence());
                    
                    // Show word details for final results
                    if (result.getWords() != null && !result.getWords().isEmpty()) {
                        System.out.printf("  Final words count: %d%n", result.getWords().size());
                    }
                }

                list.add(result);
            } else {
                System.err.println("Error: " + result.getErrorMessage());
            }
        });
        
        // Start microphone capture
        try (TargetDataLine microphone = getMicrophone()) {
            microphone.start();
            
            long startTime = System.currentTimeMillis();
            long endTime = startTime + (LISTENING_DURATION_SECONDS * 1000);
            
            byte[] buffer = new byte[BUFFER_SIZE];
            
            while (System.currentTimeMillis() < endTime) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    // Send audio chunk to session manager
                    byte[] audioChunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, audioChunk, 0, bytesRead);
                    sessionManager.processAudioChunk(sessionId, audioChunk);
                }
                
                // Small delay to prevent overwhelming the API
                Thread.sleep(50);
            }
            
            microphone.stop();
        }
        
        // Close session and wait a bit for final results
        sessionManager.closeSession(sessionId);
        Thread.sleep(2000);
        
        System.out.printf("✓ Streaming test completed. Received %d transcription results.%n", 
            resultCount.get());
    }
    
    /**
     * Test batch mode with real microphone input.
     * This test captures audio from the microphone and processes it in batch mode with VAD.
     * 
     * Prerequisites:
     * - Microphone must be available
     * - Internet connection required
     */
    @Test
    @Disabled("Manual test - remove @Disabled to run with real microphone")
    void testMicrophoneBatchMode() throws Exception {
        System.out.println("=== Microphone Batch Test ===");
        System.out.println("Starting 10-second microphone capture for batch transcription...");
        System.out.println("Speak into your microphone now! (VAD will detect speech segments)");
        
        // Configure for batch mode
        AudioProcessingConfig config = createBatchConfig();
        sessionManager = createSessionManager(config);
        
        String sessionId = "microphone-batch-test";
        AtomicInteger resultCount = new AtomicInteger(0);
        
        // Create session with result callback
        sessionManager.createSession(sessionId, result -> {
            if (!result.isError()) {
                int count = resultCount.incrementAndGet();
                System.out.printf("[Batch Result #%d] %s (confidence: %.2f)%n", 
                    count, result.getText(), result.getConfidence());
            } else {
                System.err.println("Error: " + result.getErrorMessage());
            }
        });
        
        // Start microphone capture
        try (TargetDataLine microphone = getMicrophone()) {
            microphone.start();
            
            long startTime = System.currentTimeMillis();
            long endTime = startTime + (LISTENING_DURATION_SECONDS * 1000);
            
            byte[] buffer = new byte[BUFFER_SIZE];
            
            while (System.currentTimeMillis() < endTime) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    // Send audio chunk to session manager
                    byte[] audioChunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, audioChunk, 0, bytesRead);
                    sessionManager.processAudioChunk(sessionId, audioChunk);
                }
                
                // Small delay
                Thread.sleep(100);
            }
            
            microphone.stop();
        }
        
        // Close session and wait for final processing
        sessionManager.closeSession(sessionId);
        Thread.sleep(3000); // Wait longer for batch processing to complete
        
        System.out.printf("✓ Batch test completed. Received %d transcription results.%n", 
            resultCount.get());
    }
    
    /**
     * Test microphone availability and audio capture without transcription.
     * This test verifies that microphone capture works correctly.
     */
    @Test
    @Disabled("Manual test - remove @Disabled to test microphone capture")
    void testMicrophoneCapture() throws Exception {
        System.out.println("=== Microphone Capture Test ===");
        System.out.println("Testing microphone capture for 5 seconds...");
        System.out.println("Speak into your microphone to see audio levels!");
        
        try (TargetDataLine microphone = getMicrophone()) {
            microphone.start();
            
            long startTime = System.currentTimeMillis();
            long endTime = startTime + 5000; // 5 seconds
            
            byte[] buffer = new byte[BUFFER_SIZE];
            int totalSamples = 0;
            double maxAmplitude = 0;
            
            while (System.currentTimeMillis() < endTime) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    totalSamples += bytesRead / 2; // 16-bit samples
                    
                    // Calculate amplitude
                    double amplitude = calculateAmplitude(buffer, bytesRead);
                    maxAmplitude = Math.max(maxAmplitude, amplitude);
                    
                    // Print audio level indicator
                    int level = (int) (amplitude / 1000);
                    if (level > 0) {
                        System.out.print("Audio level: ");
                        for (int i = 0; i < Math.min(level, 20); i++) {
                            System.out.print("█");
                        }
                        System.out.printf(" (%.0f)%n", amplitude);
                    }
                }
                
                Thread.sleep(100);
            }
            
            microphone.stop();
            
            System.out.printf("✓ Microphone test completed.%n");
            System.out.printf("  Total samples captured: %d%n", totalSamples);
            System.out.printf("  Maximum amplitude: %.0f%n", maxAmplitude);
        }
    }
    
    private AudioProcessingConfig createStreamingConfig() {
        AudioProcessingConfig config = new AudioProcessingConfig();
        config.setEngine(EngineType.DEEPGRAM);
        config.setProcessingMode(ProcessingMode.STREAMING);
        config.setSampleRate(SAMPLE_RATE);
        config.setBufferSize(BUFFER_SIZE);
        
        // Deepgram configuration
        CoreDeepgramConfig deepgramConfig = new CoreDeepgramConfig();
        deepgramConfig.setApiKey(getApiKey());
        deepgramConfig.setLanguage(Language.MULTI);
        deepgramConfig.setModel("nova-3");
        deepgramConfig.setPunctuate(true);
        deepgramConfig.setInterimResults(true);
        config.setDeepgram(deepgramConfig);
        
        return config;
    }
    
    private AudioProcessingConfig createBatchConfig() {
        AudioProcessingConfig config = new AudioProcessingConfig();
        config.setEngine(EngineType.DEEPGRAM);
        config.setProcessingMode(ProcessingMode.BATCH);
        config.setSampleRate(SAMPLE_RATE);
        config.setBufferSize(BUFFER_SIZE);
        
        // VAD configuration for batch mode
        VadConfig vadConfig = new VadConfig();
        vadConfig.setThreshold(0.3);
        vadConfig.setSilenceDurationMs(1500); // Wait 1.5 seconds of silence before processing
        config.setVad(vadConfig);
        
        // Chunk settings
        config.setMinChunkDurationSeconds(2);
        config.setMaxChunkDurationSeconds(30);
        
        // Deepgram configuration
        CoreDeepgramConfig deepgramConfig = new CoreDeepgramConfig();
        deepgramConfig.setApiKey(getApiKey());
        deepgramConfig.setLanguage(Language.ENGLISH);
        deepgramConfig.setModel("nova-2");
        deepgramConfig.setPunctuate(true);
        deepgramConfig.setInterimResults(false); // Not used in batch mode
        config.setDeepgram(deepgramConfig);
        
        return config;
    }
    
    private TargetDataLine getMicrophone() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
        
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Microphone not supported with the specified audio format");
        }
        
        TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(audioFormat, BUFFER_SIZE);
        
        return microphone;
    }
    
    private String getApiKey() {
        // Use the API key from application.yml configuration
        return "9685e39bb61f53bce294e6195e4b7f6ebf2d36fe";
    }
    
    private double calculateAmplitude(byte[] buffer, int length) {
        double sum = 0;
        int samples = length / 2; // 16-bit samples
        
        for (int i = 0; i < samples; i++) {
            // Convert little-endian 16-bit sample
            int sample = (buffer[i * 2] & 0xFF) | ((buffer[i * 2 + 1] & 0xFF) << 8);
            if (sample > 32767) sample -= 65536; // Convert to signed
            sum += Math.abs(sample);
        }
        
        return samples > 0 ? sum / samples : 0;
    }
    
    private AudioSessionManager createSessionManager(AudioProcessingConfig config) {
        SpringTranscriptionEngineFactory engineFactory = new SpringTranscriptionEngineFactory(config);
        AudioConverter audioConverter = new AudioConverter(config);
        return new AudioSessionManager(config, engineFactory, audioConverter);
    }
}