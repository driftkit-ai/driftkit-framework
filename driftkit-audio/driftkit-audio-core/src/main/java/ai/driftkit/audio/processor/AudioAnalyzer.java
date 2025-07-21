package ai.driftkit.audio.processor;

import ai.driftkit.audio.core.config.CoreAudioConfig;
import ai.driftkit.audio.model.AudioAnalysis;

/**
 * Service for analyzing audio buffers and detecting voice activity
 */
public class AudioAnalyzer {
    
    private final CoreAudioConfig config;

    // Adaptive sensitivity fields
    private volatile long lastVoiceDetectedTime = System.currentTimeMillis();
    private volatile boolean sensitivityBoosted = false;
    private volatile int currentSilenceThreshold;
    private volatile int currentVoiceThreshold;
    
    // Silence reset timer fields
    private volatile long lastSoundDetectedTime = System.currentTimeMillis();
    private volatile int dynamicSilenceThreshold = -1;
    private volatile boolean timerRunning = false;
    private final Object thresholdLock = new Object();
    
    // Calibration support
    private double backgroundNoiseLevel = 0;
    private boolean isCalibrated = false;

    public AudioAnalyzer(CoreAudioConfig config) {
        this.config = config;
    }

    /**
     * Calibrate background noise level
     * @param samples Array of AudioAnalysis samples from calibration phase
     * @return Adjusted thresholds based on background noise
     */
    public CalibrationResult calibrateBackgroundNoise(AudioAnalysis[] samples) {
        // Default thresholds based on VAD config
        double defaultSilenceThreshold = config.getVad().getThreshold() * 100; // Convert to 0-100 scale
        double defaultVoiceThreshold = defaultSilenceThreshold * 2;
        
        if (samples == null || samples.length == 0) {
            return new CalibrationResult((int)defaultSilenceThreshold, (int)defaultVoiceThreshold);
        }
        
        // Calculate average background noise
        double totalAmplitude = 0;
        for (AudioAnalysis sample : samples) {
            totalAmplitude += sample.getAmplitude();
        }
        backgroundNoiseLevel = totalAmplitude / samples.length;
        
        // Calculate adjusted thresholds
        int adjustedSilenceThreshold = (int)(backgroundNoiseLevel * 150); // 1.5x background
        int adjustedVoiceThreshold = (int)(backgroundNoiseLevel * 250);   // 2.5x background
        
        // Use adjusted thresholds if they're higher than configured
        int finalSilenceThreshold = Math.max(adjustedSilenceThreshold, (int)defaultSilenceThreshold);
        int finalVoiceThreshold = Math.max(adjustedVoiceThreshold, (int)defaultVoiceThreshold);
        
        // Update current thresholds
        currentSilenceThreshold = finalSilenceThreshold;
        currentVoiceThreshold = finalVoiceThreshold;
        isCalibrated = true;
        
        return new CalibrationResult(finalSilenceThreshold, finalVoiceThreshold, backgroundNoiseLevel);
    }
    
    /**
     * Analyze audio buffer for voice activity and silence detection
     */
    public AudioAnalysis analyzeBuffer(byte[] buffer, int length) {
        // Convert bytes to 16-bit samples and calculate RMS (Root Mean Square)
        long sum = 0;
        int sampleCount = length / 2; // 16-bit samples
        
        for (int i = 0; i < length - 1; i += 2) {
            // Convert two bytes to a 16-bit sample (big-endian)
            short sample = (short)((buffer[i] << 8) | (buffer[i + 1] & 0xFF));
            sum += sample * sample;
        }
        
        double rms = Math.sqrt((double)sum / sampleCount);
        boolean isSilent = rms < getCurrentSilenceThreshold();
        
        // Update sound detection time for silence reset timer
        updateSoundDetectionTime(rms);
        
        return new AudioAnalysis(isSilent, rms);
    }
    
    /**
     * Analyze entire chunk for voice activity
     */
    public boolean analyzeChunkForVoice(byte[] chunkData) {
        // Analyze the entire chunk in segments to detect voice activity
        int segmentSize = config.getSampleRate() * 2; // 1 second segments
        int numSegments = chunkData.length / segmentSize;
        int voiceSegments = 0;
        double maxSegmentAmplitude = 0;
        
        for (int i = 0; i < numSegments; i++) {
            int start = i * segmentSize;
            int length = Math.min(segmentSize, chunkData.length - start);
            
            if (length > 0) {
                byte[] segment = new byte[length];
                System.arraycopy(chunkData, start, segment, 0, length);
                
                AudioAnalysis analysis = analyzeBuffer(segment, length);
                if (analysis.getAmplitude() > maxSegmentAmplitude) {
                    maxSegmentAmplitude = analysis.getAmplitude();
                }
                
                if (analysis.getAmplitude() > getCurrentVoiceThreshold()) {
                    voiceSegments++;
                }
            }
        }
        
        // Calculate average amplitude across segments
        double avgAmplitude = 0;
        if (numSegments > 0) {
            double totalAmplitude = 0;
            for (int i = 0; i < numSegments; i++) {
                int start = i * segmentSize;
                int length = Math.min(segmentSize, chunkData.length - start);
                if (length > 0) {
                    byte[] segment = new byte[length];
                    System.arraycopy(chunkData, start, segment, 0, length);
                    AudioAnalysis analysis = analyzeBuffer(segment, length);
                    totalAmplitude += analysis.getAmplitude();
                }
            }
            avgAmplitude = totalAmplitude / numSegments;
        }
        
        // Consider it has voice if:
        // 1. At least one segment has voice activity, OR
        // 2. Maximum amplitude is above 70% of threshold (more lenient)
        // 3. Average amplitude across segments is reasonably high
        boolean hasVoice = voiceSegments > 0 || 
                          maxSegmentAmplitude > (getCurrentVoiceThreshold() * 0.7) ||
                          avgAmplitude > (getCurrentVoiceThreshold() * 0.5);
        
        return hasVoice;
    }
    
    /**
     * Initialize adaptive sensitivity settings
     */
    public void initializeAdaptiveSensitivity() {
        int baseThreshold = (int)(config.getVad().getThreshold() * 100);
        currentSilenceThreshold = baseThreshold;
        currentVoiceThreshold = baseThreshold * 2;
        lastVoiceDetectedTime = System.currentTimeMillis();
        sensitivityBoosted = false;
    }
    
    /**
     * Update adaptive sensitivity based on voice activity
     */
    public void updateAdaptiveSensitivity(boolean voiceDetected) {
        long currentTime = System.currentTimeMillis();
        
        if (voiceDetected) {
            lastVoiceDetectedTime = currentTime;
            if (sensitivityBoosted) {
                // Reset to normal sensitivity when voice is detected
                int baseThreshold = (int)(config.getVad().getThreshold() * 100);
                currentSilenceThreshold = baseThreshold;
                currentVoiceThreshold = baseThreshold * 2;
                sensitivityBoosted = false;
            }
        } else {
            // Check if we should boost sensitivity after 5 seconds of silence
            long silenceDuration = currentTime - lastVoiceDetectedTime;
            if (!sensitivityBoosted && silenceDuration > 5000) {
                currentSilenceThreshold = currentSilenceThreshold / 2;
                currentVoiceThreshold = currentVoiceThreshold / 2;
                sensitivityBoosted = true;
            }
        }
    }
    
    /**
     * Get current silence threshold (may be adapted)
     */
    public int getCurrentSilenceThreshold() {
        return currentSilenceThreshold > 0 ? currentSilenceThreshold : (int)(config.getVad().getThreshold() * 100);
    }
    
    /**
     * Get current voice activity threshold (may be adapted)
     */
    public int getCurrentVoiceThreshold() {
        return currentVoiceThreshold > 0 ? currentVoiceThreshold : (int)(config.getVad().getThreshold() * 200);
    }
    
    /**
     * Update last sound detected time when analyzing audio
     */
    private void updateSoundDetectionTime(double amplitude) {
        // If sound is detected (not silence), update the timer
        if (amplitude > getCurrentSilenceThreshold()) {
            lastSoundDetectedTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Set dynamic silence threshold (for background noise adaptation)
     */
    public void setDynamicSilenceThreshold(int threshold) {
        this.currentSilenceThreshold = threshold;
    }
    
    /**
     * Set dynamic voice threshold (for background noise adaptation)  
     */
    public void setDynamicVoiceThreshold(int threshold) {
        this.currentVoiceThreshold = threshold;
    }
    
    
    /**
     * Decrease silence threshold and start 15-second timer to reset
     * @return new threshold value
     */
    public int decreaseSilenceThreshold() {
        synchronized (thresholdLock) {
            // Set to a very low threshold
            int newThreshold = 15;
            dynamicSilenceThreshold = newThreshold;
            currentSilenceThreshold = newThreshold;
            
            // Reset the timer
            lastSoundDetectedTime = System.currentTimeMillis();
            
            // Start timer thread if not already running
            startSilenceResetTimer();
            
            return newThreshold;
        }
    }
    
    /**
     * Start a timer that resets silence threshold after 15 seconds of silence
     */
    private void startSilenceResetTimer() {
        if (timerRunning) {
            return; // Timer already running
        }
        
        timerRunning = true;
        new Thread(() -> {
            while (timerRunning) {
                try {
                    Thread.sleep(1000); // Check every second
                    
                    long currentTime = System.currentTimeMillis();
                    long silenceDuration = currentTime - lastSoundDetectedTime;
                    
                    // Check if 15 seconds of silence have passed
                    if (silenceDuration >= 15000) {
                        synchronized (thresholdLock) {
                            dynamicSilenceThreshold = 0;
                            currentSilenceThreshold = 0;
                            timerRunning = false;
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
            timerRunning = false;
        }).start();
    }
    
    /**
     * Reset all thresholds to default values
     */
    public void resetThresholds() {
        synchronized (thresholdLock) {
            int baseThreshold = (int)(config.getVad().getThreshold() * 100);
            currentSilenceThreshold = baseThreshold;
            currentVoiceThreshold = baseThreshold * 2;
            dynamicSilenceThreshold = -1;
            sensitivityBoosted = false;
            isCalibrated = false;
            timerRunning = false;
        }
    }
    
    /**
     * Get current configuration state for debugging
     */
    public String getDebugInfo() {
        return String.format("AudioAnalyzer Debug Info:\n" +
            "  Background Noise: %.1f\n" +
            "  Current Silence Threshold: %d\n" +
            "  Current Voice Threshold: %d\n" +
            "  Is Calibrated: %b\n" +
            "  Sensitivity Boosted: %b\n" +
            "  Dynamic Threshold: %d\n" +
            "  Timer Running: %b",
            backgroundNoiseLevel,
            getCurrentSilenceThreshold(),
            getCurrentVoiceThreshold(),
            isCalibrated,
            sensitivityBoosted,
            dynamicSilenceThreshold,
            timerRunning
        );
    }
    
    /**
     * Calibration result class
     */
    public static class CalibrationResult {
        private final int silenceThreshold;
        private final int voiceThreshold;
        private final double backgroundNoise;
        
        public CalibrationResult(int silenceThreshold, int voiceThreshold) {
            this(silenceThreshold, voiceThreshold, 0);
        }
        
        public CalibrationResult(int silenceThreshold, int voiceThreshold, double backgroundNoise) {
            this.silenceThreshold = silenceThreshold;
            this.voiceThreshold = voiceThreshold;
            this.backgroundNoise = backgroundNoise;
        }
        
        public int getSilenceThreshold() { return silenceThreshold; }
        public int getVoiceThreshold() { return voiceThreshold; }
        public double getBackgroundNoise() { return backgroundNoise; }
    }
}