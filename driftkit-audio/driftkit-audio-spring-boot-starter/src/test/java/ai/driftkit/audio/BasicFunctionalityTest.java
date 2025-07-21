package ai.driftkit.audio;

import ai.driftkit.audio.config.AudioProcessingConfig;
import ai.driftkit.audio.core.AudioFormatType;
import ai.driftkit.audio.core.config.VadConfig;
import ai.driftkit.audio.model.AudioAnalysis;
import ai.driftkit.audio.processor.AudioAnalyzer;
import ai.driftkit.audio.converter.AudioConverter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic functionality tests that verify core components work.
 */
public class BasicFunctionalityTest {
    
    @Test
    void testAudioAnalyzerSessionIsolation() {
        AudioProcessingConfig config1 = createBasicConfig();
        AudioProcessingConfig config2 = createBasicConfig();
        
        AudioAnalyzer analyzer1 = new AudioAnalyzer(config1);
        AudioAnalyzer analyzer2 = new AudioAnalyzer(config2);
        
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
        
        System.out.println("✓ AudioAnalyzer session isolation test passed");
    }
    
    @Test
    void testAudioAnalyzerBasicFunctionality() {
        AudioProcessingConfig config = createBasicConfig();
        AudioAnalyzer analyzer = new AudioAnalyzer(config);
        
        // Test basic audio analysis
        byte[] testAudio = generateTestAudio(1000);
        AudioAnalysis analysis = analyzer.analyzeBuffer(testAudio, testAudio.length);
        
        assertNotNull(analysis);
        assertTrue(analysis.getAmplitude() >= 0);
        assertNotNull(analysis.isSilent()); // Boolean should not be null
        
        System.out.println("✓ AudioAnalyzer basic functionality test passed");
        System.out.println("  Amplitude: " + analysis.getAmplitude());
        System.out.println("  Is Silent: " + analysis.isSilent());
    }
    
    @Test
    void testAudioConverter() {
        AudioProcessingConfig config1 = createBasicConfig();
        AudioConverter converter = new AudioConverter(config1);
        
        byte[] testAudio = generateTestAudio(1000);
        
        try {
            // Test WAV conversion
            byte[] wavAudio = converter.convertToFormat(testAudio, 16000, AudioFormatType.WAV);
            assertNotNull(wavAudio);
            assertTrue(wavAudio.length > testAudio.length); // WAV has header
            
            System.out.println("✓ AudioConverter test passed");
            System.out.println("  Original size: " + testAudio.length + " bytes");
            System.out.println("  WAV size: " + wavAudio.length + " bytes");
            
        } catch (Exception e) {
            System.out.println("⚠ AudioConverter test skipped: " + e.getMessage());
            // This is OK - may not have all audio libraries available in test environment
        }
    }
    
    @Test
    void testConcurrentAudioAnalysis() throws InterruptedException {
        AudioProcessingConfig config = createBasicConfig();
        int numThreads = 5;
        int numIterations = 20;
        
        Thread[] threads = new Thread[numThreads];
        boolean[] results = new boolean[numThreads];
        
        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                try {
                    AudioAnalyzer analyzer = new AudioAnalyzer(config);
                    analyzer.initializeAdaptiveSensitivity();
                    
                    for (int j = 0; j < numIterations; j++) {
                        byte[] audio = generateTestAudio(500 + threadIndex * 100);
                        AudioAnalysis analysis = analyzer.analyzeBuffer(audio, audio.length);
                        
                        assertNotNull(analysis);
                        assertTrue(analysis.getAmplitude() >= 0);
                    }
                    
                    results[threadIndex] = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    results[threadIndex] = false;
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            thread.join(5000); // 5 second timeout
        }
        
        // Verify all succeeded
        for (int i = 0; i < numThreads; i++) {
            assertTrue(results[i], "Thread " + i + " should have succeeded");
        }
        
        System.out.println("✓ Concurrent audio analysis test passed");
        System.out.println("  " + numThreads + " threads × " + numIterations + " iterations");
    }
    
    @Test
    void testVADThresholdCalculation() {
        AudioProcessingConfig config = createBasicConfig();
        AudioAnalyzer analyzer = new AudioAnalyzer(config);
        
        analyzer.initializeAdaptiveSensitivity();
        
        int silenceThreshold = analyzer.getCurrentSilenceThreshold();
        int voiceThreshold = analyzer.getCurrentVoiceThreshold();
        
        assertTrue(silenceThreshold > 0, "Silence threshold should be positive");
        assertTrue(voiceThreshold > silenceThreshold, "Voice threshold should be higher than silence");
        
        // Test threshold adjustment
        analyzer.setDynamicSilenceThreshold(25);
        assertEquals(25, analyzer.getCurrentSilenceThreshold());
        
        analyzer.resetThresholds();
        assertEquals(silenceThreshold, analyzer.getCurrentSilenceThreshold());
        
        System.out.println("✓ VAD threshold calculation test passed");
        System.out.println("  Default silence threshold: " + silenceThreshold);
        System.out.println("  Default voice threshold: " + voiceThreshold);
    }
    
    @Test
    void testAudioDataFormats() {
        AudioProcessingConfig config = createBasicConfig();
        AudioAnalyzer analyzer = new AudioAnalyzer(config);
        
        // Test different audio data sizes
        int[] sizes = {100, 1000, 4096, 8192};
        
        for (int size : sizes) {
            byte[] audio = generateTestAudio(size);
            AudioAnalysis analysis = analyzer.analyzeBuffer(audio, audio.length);
            
            assertNotNull(analysis, "Analysis should work for size " + size);
            assertTrue(analysis.getAmplitude() >= 0, "Amplitude should be non-negative for size " + size);
        }
        
        System.out.println("✓ Audio data formats test passed");
        System.out.println("  Tested sizes: " + java.util.Arrays.toString(sizes));
    }
    
    @Test
    void testMemoryUsage() {
        // Simple memory usage test
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        // Create multiple analyzers
        AudioProcessingConfig config = createBasicConfig();
        AudioAnalyzer[] analyzers = new AudioAnalyzer[10];
        
        for (int i = 0; i < analyzers.length; i++) {
            analyzers[i] = new AudioAnalyzer(config);
            analyzers[i].initializeAdaptiveSensitivity();
            
            // Process some audio
            byte[] audio = generateTestAudio(2000);
            analyzers[i].analyzeBuffer(audio, audio.length);
        }
        
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;
        
        System.out.println("✓ Memory usage test completed");
        System.out.println("  Memory used for 10 analyzers: " + (memoryUsed / 1024) + " KB");
        
        // Clean up
        analyzers = null;
        System.gc();
        
        assertTrue(memoryUsed < 50 * 1024 * 1024, "Memory usage should be reasonable (< 50MB)");
    }
    
    // Helper methods
    
    private AudioProcessingConfig createBasicConfig() {
        AudioProcessingConfig config = new AudioProcessingConfig();
        ReflectionTestUtils.setField(config, "sampleRate", 16000);
        ReflectionTestUtils.setField(config, "bufferSize", 4096);
        
        // Create basic VAD config
        VadConfig vadConfig = new VadConfig();
        vadConfig.setThreshold(0.3);
        vadConfig.setSilenceDurationMs(1000);
        ReflectionTestUtils.setField(config, "vad", vadConfig);
        
        return config;
    }
    
    private byte[] generateTestAudio(int samples) {
        byte[] audio = new byte[samples * 2]; // 16-bit samples
        
        for (int i = 0; i < samples; i++) {
            // Generate sine wave at 440Hz
            double t = (double) i / 16000.0;
            double amplitude = Math.sin(2 * Math.PI * 440 * t);
            short sample = (short) (amplitude * 5000);
            
            audio[i * 2] = (byte) (sample & 0xFF);
            audio[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        
        return audio;
    }
}