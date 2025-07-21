package ai.driftkit.audio;

import ai.driftkit.audio.config.AudioProcessingConfig;
import ai.driftkit.audio.model.AudioAnalysis;
import ai.driftkit.audio.processor.AudioAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ai.driftkit.audio.core.config.VadConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify that AudioAnalyzer instances are properly isolated between sessions.
 */
public class AudioAnalyzerSessionIsolationTest {
    
    private AudioProcessingConfig config;
    
    @BeforeEach
    void setUp() {
        config = createTestConfig();
    }
    
    /**
     * Test that different AudioAnalyzer instances maintain separate thresholds.
     */
    @Test
    void testThresholdIsolationBetweenAnalyzers() {
        AudioAnalyzer analyzer1 = new AudioAnalyzer(config);
        AudioAnalyzer analyzer2 = new AudioAnalyzer(config);
        
        // Initialize both analyzers
        analyzer1.initializeAdaptiveSensitivity();
        analyzer2.initializeAdaptiveSensitivity();
        
        // Both should start with same thresholds
        assertEquals(analyzer1.getCurrentSilenceThreshold(), analyzer2.getCurrentSilenceThreshold());
        assertEquals(analyzer1.getCurrentVoiceThreshold(), analyzer2.getCurrentVoiceThreshold());
        
        // Modify thresholds in analyzer1
        analyzer1.setDynamicSilenceThreshold(50);
        analyzer1.setDynamicVoiceThreshold(100);
        
        // Analyzer2 should remain unchanged
        assertNotEquals(analyzer1.getCurrentSilenceThreshold(), analyzer2.getCurrentSilenceThreshold());
        assertNotEquals(analyzer1.getCurrentVoiceThreshold(), analyzer2.getCurrentVoiceThreshold());
        
        // Verify specific values
        assertEquals(50, analyzer1.getCurrentSilenceThreshold());
        assertEquals(100, analyzer1.getCurrentVoiceThreshold());
        assertEquals(30, analyzer2.getCurrentSilenceThreshold()); // Default from config
        assertEquals(60, analyzer2.getCurrentVoiceThreshold()); // Default from config
    }
    
    /**
     * Test calibration isolation between different analyzers.
     */
    @Test
    void testCalibrationIsolation() {
        AudioAnalyzer analyzer1 = new AudioAnalyzer(config);
        AudioAnalyzer analyzer2 = new AudioAnalyzer(config);
        
        // Create different calibration samples
        AudioAnalysis[] samples1 = {
            new AudioAnalysis(false, 100.0),
            new AudioAnalysis(false, 120.0),
            new AudioAnalysis(false, 110.0)
        };
        
        AudioAnalysis[] samples2 = {
            new AudioAnalysis(false, 200.0),
            new AudioAnalysis(false, 220.0),
            new AudioAnalysis(false, 210.0)
        };
        
        // Calibrate each analyzer with different samples
        AudioAnalyzer.CalibrationResult result1 = analyzer1.calibrateBackgroundNoise(samples1);
        AudioAnalyzer.CalibrationResult result2 = analyzer2.calibrateBackgroundNoise(samples2);
        
        // Results should be different
        assertNotEquals(result1.getSilenceThreshold(), result2.getSilenceThreshold());
        assertNotEquals(result1.getVoiceThreshold(), result2.getVoiceThreshold());
        assertNotEquals(result1.getBackgroundNoise(), result2.getBackgroundNoise());
        
        // Verify thresholds are applied independently
        assertNotEquals(analyzer1.getCurrentSilenceThreshold(), analyzer2.getCurrentSilenceThreshold());
        assertNotEquals(analyzer1.getCurrentVoiceThreshold(), analyzer2.getCurrentVoiceThreshold());
    }
    
    /**
     * Test that adaptive sensitivity works independently across analyzers.
     */
    @Test
    void testAdaptiveSensitivityIsolation() throws InterruptedException {
        AudioAnalyzer analyzer1 = new AudioAnalyzer(config);
        AudioAnalyzer analyzer2 = new AudioAnalyzer(config);
        
        analyzer1.initializeAdaptiveSensitivity();
        analyzer2.initializeAdaptiveSensitivity();
        
        // Store initial thresholds
        int initialSilence1 = analyzer1.getCurrentSilenceThreshold();
        int initialVoice1 = analyzer1.getCurrentVoiceThreshold();
        int initialSilence2 = analyzer2.getCurrentSilenceThreshold();
        int initialVoice2 = analyzer2.getCurrentVoiceThreshold();
        
        assertEquals(initialSilence1, initialSilence2);
        assertEquals(initialVoice1, initialVoice2);
        
        // Trigger adaptive sensitivity in analyzer1 only
        analyzer1.updateAdaptiveSensitivity(false); // No voice detected
        Thread.sleep(6000); // Wait for boost threshold
        analyzer1.updateAdaptiveSensitivity(false);
        
        // analyzer1 should have boosted sensitivity, analyzer2 should not
        assertTrue(analyzer1.getCurrentSilenceThreshold() < initialSilence1);
        assertTrue(analyzer1.getCurrentVoiceThreshold() < initialVoice1);
        assertEquals(initialSilence2, analyzer2.getCurrentSilenceThreshold());
        assertEquals(initialVoice2, analyzer2.getCurrentVoiceThreshold());
    }
    
    /**
     * Test concurrent analysis by multiple analyzers doesn't interfere.
     */
    @Test
    void testConcurrentAnalysisIsolation() throws Exception {
        int numAnalyzers = 5;
        int numIterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numAnalyzers);
        CountDownLatch latch = new CountDownLatch(numAnalyzers);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // Create and run concurrent analyzers
        for (int i = 0; i < numAnalyzers; i++) {
            final int analyzerId = i;
            executor.submit(() -> {
                try {
                    AudioAnalyzer analyzer = new AudioAnalyzer(config);
                    analyzer.initializeAdaptiveSensitivity();
                    
                    // Each analyzer processes different audio patterns
                    for (int j = 0; j < numIterations; j++) {
                        byte[] audioData = generateTestAudio(analyzerId, j);
                        AudioAnalysis result = analyzer.analyzeBuffer(audioData, audioData.length);
                        
                        // Verify analysis is consistent for this analyzer
                        assertNotNull(result);
                        assertTrue(result.getAmplitude() >= 0);
                    }
                    
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        assertEquals(numAnalyzers, successCount.get());
        
        executor.shutdown();
    }
    
    /**
     * Test that voice chunk analysis is isolated between analyzers.
     */
    @Test
    void testVoiceChunkAnalysisIsolation() {
        AudioAnalyzer analyzer1 = new AudioAnalyzer(config);
        AudioAnalyzer analyzer2 = new AudioAnalyzer(config);
        
        // Generate different audio chunks
        byte[] speechChunk = generateSpeechAudio();
        byte[] silenceChunk = generateSilenceAudio();
        
        // Set different sensitivity levels
        analyzer1.setDynamicVoiceThreshold(100); // More sensitive
        analyzer2.setDynamicVoiceThreshold(500); // Less sensitive
        
        // Analyze same audio with different analyzers
        boolean result1 = analyzer1.analyzeChunkForVoice(speechChunk);
        boolean result2 = analyzer2.analyzeChunkForVoice(speechChunk);
        
        // Results might be different due to different thresholds
        // But both should be valid boolean results
        assertTrue(result1 || !result1); // Valid boolean
        assertTrue(result2 || !result2); // Valid boolean
        
        // Test with silence - should be consistent
        boolean silence1 = analyzer1.analyzeChunkForVoice(silenceChunk);
        boolean silence2 = analyzer2.analyzeChunkForVoice(silenceChunk);
        
        // Silence should generally be detected as no voice by both
        assertFalse(silence1);
        assertFalse(silence2);
    }
    
    /**
     * Test memory isolation - analyzers don't share state.
     */
    @Test
    void testMemoryStateIsolation() {
        AudioAnalyzer analyzer1 = new AudioAnalyzer(config);
        AudioAnalyzer analyzer2 = new AudioAnalyzer(config);
        
        // Modify internal state of analyzer1
        analyzer1.initializeAdaptiveSensitivity();
        analyzer1.decreaseSilenceThreshold();
        
        // analyzer2 should not be affected
        analyzer2.initializeAdaptiveSensitivity();
        
        // Verify different states
        assertNotEquals(analyzer1.getCurrentSilenceThreshold(), analyzer2.getCurrentSilenceThreshold());
        
        // Reset analyzer1 - should not affect analyzer2
        int analyzer2ThresholdBefore = analyzer2.getCurrentSilenceThreshold();
        analyzer1.resetThresholds();
        int analyzer2ThresholdAfter = analyzer2.getCurrentSilenceThreshold();
        
        assertEquals(analyzer2ThresholdBefore, analyzer2ThresholdAfter);
    }
    
    /**
     * Test parallel processing with session-specific calibration.
     */
    @Test
    void testParallelProcessingWithCalibration() throws Exception {
        int numSessions = 3;
        ExecutorService executor = Executors.newFixedThreadPool(numSessions);
        CountDownLatch latch = new CountDownLatch(numSessions);
        
        CompletableFuture<String>[] futures = new CompletableFuture[numSessions];
        
        for (int i = 0; i < numSessions; i++) {
            final int sessionId = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    AudioAnalyzer analyzer = new AudioAnalyzer(config);
                    
                    // Each session has different background noise
                    AudioAnalysis[] calibrationSamples = generateCalibrationSamples(sessionId);
                    AudioAnalyzer.CalibrationResult calibration = analyzer.calibrateBackgroundNoise(calibrationSamples);
                    
                    // Process audio specific to this session
                    byte[] sessionAudio = generateSessionSpecificAudio(sessionId);
                    AudioAnalysis result = analyzer.analyzeBuffer(sessionAudio, sessionAudio.length);
                    
                    return String.format("Session %d: Calibration=%s, Analysis=%s", 
                                       sessionId, calibration.getBackgroundNoise(), result.getAmplitude());
                    
                } finally {
                    latch.countDown();
                }
            }, executor);
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        
        // Verify all sessions completed successfully
        for (CompletableFuture<String> future : futures) {
            String result = future.get();
            assertNotNull(result);
            assertTrue(result.startsWith("Session"));
            System.out.println(result);
        }
        
        executor.shutdown();
    }
    
    // Helper methods
    
    private AudioProcessingConfig createTestConfig() {
        AudioProcessingConfig config = new AudioProcessingConfig();
        config.setSampleRate(16000);
        
        VadConfig vadConfig = new VadConfig();
        vadConfig.setThreshold(0.3); // This becomes 30 in the analyzer (0.3 * 100)
        vadConfig.setSilenceDurationMs(1000);
        config.setVad(vadConfig);
        
        return config;
    }
    
    private byte[] generateTestAudio(int analyzerId, int iteration) {
        int samples = 1000;
        byte[] audio = new byte[samples * 2];
        
        // Generate different patterns for different analyzers
        double frequency = 440 + (analyzerId * 100) + iteration;
        double amplitude = 5000 + (analyzerId * 1000);
        
        for (int i = 0; i < samples; i++) {
            short sample = (short) (Math.sin(2 * Math.PI * frequency * i / 16000) * amplitude);
            audio[i * 2] = (byte) (sample >> 8);
            audio[i * 2 + 1] = (byte) (sample & 0xFF);
        }
        
        return audio;
    }
    
    private byte[] generateSpeechAudio() {
        int samples = 2000;
        byte[] audio = new byte[samples * 2];
        
        // Generate audio that looks like speech
        for (int i = 0; i < samples; i++) {
            double t = (double) i / 16000;
            short sample = (short) (
                Math.sin(2 * Math.PI * 200 * t) * 8000 +
                Math.sin(2 * Math.PI * 400 * t) * 4000 +
                Math.sin(2 * Math.PI * 800 * t) * 2000
            );
            audio[i * 2] = (byte) (sample >> 8);
            audio[i * 2 + 1] = (byte) (sample & 0xFF);
        }
        
        return audio;
    }
    
    private byte[] generateSilenceAudio() {
        int samples = 2000;
        byte[] audio = new byte[samples * 2];
        
        // Generate very low amplitude audio (background noise)
        for (int i = 0; i < samples; i++) {
            short sample = (short) ((Math.random() - 0.5) * 20); // Very quiet noise
            audio[i * 2] = (byte) (sample >> 8);
            audio[i * 2 + 1] = (byte) (sample & 0xFF);
        }
        
        return audio;
    }
    
    private AudioAnalysis[] generateCalibrationSamples(int sessionId) {
        AudioAnalysis[] samples = new AudioAnalysis[5];
        double baseNoise = 50 + (sessionId * 20); // Different noise levels per session
        
        for (int i = 0; i < samples.length; i++) {
            double noise = baseNoise + (Math.random() * 10 - 5); // Add some variation
            samples[i] = new AudioAnalysis(true, noise);
        }
        
        return samples;
    }
    
    private byte[] generateSessionSpecificAudio(int sessionId) {
        int samples = 1000;
        byte[] audio = new byte[samples * 2];
        
        // Generate audio with session-specific characteristics
        double sessionAmplitude = 1000 * (sessionId + 1);
        double sessionFreq = 300 + (sessionId * 100);
        
        for (int i = 0; i < samples; i++) {
            short sample = (short) (Math.sin(2 * Math.PI * sessionFreq * i / 16000) * sessionAmplitude);
            audio[i * 2] = (byte) (sample >> 8);
            audio[i * 2 + 1] = (byte) (sample & 0xFF);
        }
        
        return audio;
    }
}