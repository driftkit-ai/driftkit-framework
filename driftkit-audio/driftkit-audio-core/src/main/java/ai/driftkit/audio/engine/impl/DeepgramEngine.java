package ai.driftkit.audio.engine.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import ai.driftkit.audio.core.config.CoreAudioConfig;
import ai.driftkit.audio.engine.AbstractTranscriptionEngine;
import ai.driftkit.audio.engine.EngineConfiguration;
import ai.driftkit.audio.model.TranscriptionResult;
import ai.driftkit.audio.model.WordBuffer;
import ai.driftkit.audio.model.WordInfo;
import ai.driftkit.audio.model.SegmentResult;
import ai.driftkit.audio.model.deepgram.DeepgramResponse;
import ai.driftkit.audio.model.deepgram.DeepgramAlternative;
import ai.driftkit.audio.model.deepgram.DeepgramWord;
import okhttp3.*;
import okio.ByteString;
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Deepgram transcription engine implementation.
 * Supports both batch and streaming transcription modes.
 */
@Slf4j
public class DeepgramEngine extends AbstractTranscriptionEngine {
    
    private static final String ENGINE_NAME = "Deepgram";
    private static final String DEEPGRAM_API_URL = "https://api.deepgram.com/v1/listen";
    private static final String DEEPGRAM_WS_URL = "wss://api.deepgram.com/v1/listen";
    
    private OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    
    public DeepgramEngine(CoreAudioConfig config) {
        super(config);
    }
    
    @Override
    public String getName() {
        return ENGINE_NAME;
    }
    
    @Override
    public boolean supportsBatchMode() {
        return true;
    }
    
    @Override
    public boolean supportsStreamingMode() {
        return true;
    }
    
    @Override
    public void initialize() {
        String apiKey = config.getDeepgram().getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Deepgram API key is not configured");
        }
        
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        log.info("Deepgram engine initialized");
    }
    
    @Override
    protected CompletableFuture<TranscriptionResult> doTranscribeBatch(
            byte[] audioData, int sampleRate, String languageCode) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = buildBatchUrl(sampleRate, languageCode);
                
                RequestBody body = RequestBody.create(
                    audioData, 
                    MediaType.parse("audio/wav")
                );
                
                Request request = new Request.Builder()
                        .url(url)
                        .header("Authorization", "Token " + config.getDeepgram().getApiKey())
                        .header("Content-Type", "audio/wav")
                        .post(body)
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected response: " + response);
                    }
                    
                    String responseBody = response.body().string();
                    return parseDeepgramResponse(responseBody);
                }
                
            } catch (Exception e) {
                log.error("Deepgram batch transcription failed", e);
                return TranscriptionResult.builder()
                        .error(true)
                        .errorMessage("Transcription failed: " + e.getMessage())
                        .timestamp(System.currentTimeMillis())
                        .build();
            }
        });
    }
    
    @Override
    protected StreamingSession createStreamingSession(
            String sessionId, 
            int sampleRate, 
            String languageCode,
            Consumer<TranscriptionResult> resultCallback) {
        
        return new DeepgramStreamingSession(sessionId, sampleRate, languageCode, resultCallback);
    }
    
    @Override
    protected void doShutdown() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
        if (reconnectExecutor != null && !reconnectExecutor.isShutdown()) {
            reconnectExecutor.shutdown();
        }
    }
    
    @Override
    public EngineConfiguration getConfiguration() {
        Map<String, String> requiredConfig = new HashMap<>();
        requiredConfig.put("audio.processing.deepgram.api-key", "Deepgram API key");
        
        Map<String, String> optionalConfig = new HashMap<>();
        optionalConfig.put("audio.processing.deepgram.language", "Language code (default: en)");
        optionalConfig.put("audio.processing.deepgram.model", "Model to use (default: nova-2)");
        optionalConfig.put("audio.processing.deepgram.punctuate", "Add punctuation (default: true)");
        optionalConfig.put("audio.processing.deepgram.interim-results", "Enable interim results for streaming (default: true)");
        
        return EngineConfiguration.builder()
                .engineType(ENGINE_NAME)
                .requiredConfig(requiredConfig)
                .optionalConfig(optionalConfig)
                .processingMode(EngineConfiguration.ProcessingMode.BOTH)
                .supportedFormats(EngineConfiguration.AudioFormat.builder()
                        .supportedSampleRates(new int[]{8000, 16000, 24000, 48000})
                        .supportedChannels(new int[]{1, 2})
                        .supportedBitsPerSample(new int[]{16})
                        .supportedEncodings(new String[]{"linear16", "flac", "mulaw", "amr", "opus"})
                        .build())
                .maxStreamingChunkSize(8192) // 8KB chunks
                .recommendedBufferSizeMs(100) // 100ms buffers
                .requiresConversion(false) // Deepgram accepts raw PCM
                .build();
    }
    
    private String buildBatchUrl(int sampleRate, String languageCode) {
        StringBuilder url = new StringBuilder(DEEPGRAM_API_URL);
        url.append("?encoding=linear16");
        url.append("&sample_rate=").append(sampleRate);
        
        String effectiveLanguage = languageCode != null ? languageCode : config.getDeepgram().getLanguage().getValue();
        url.append("&language=").append(effectiveLanguage);
        url.append("&model=").append(config.getDeepgram().getModel());
        url.append("&punctuate=").append(config.getDeepgram().isPunctuate());
        
        return url.toString();
    }
    
    private String buildStreamingUrl(int sampleRate, String languageCode) {
        StringBuilder url = new StringBuilder(DEEPGRAM_WS_URL);
        url.append("?encoding=linear16");
        url.append("&sample_rate=").append(sampleRate);
        
        String effectiveLanguage = languageCode != null ? languageCode : config.getDeepgram().getLanguage().getValue();
        url.append("&language=").append(effectiveLanguage);
        url.append("&model=").append(config.getDeepgram().getModel());
        url.append("&punctuate=").append(config.getDeepgram().isPunctuate());
        url.append("&interim_results=").append(config.getDeepgram().isInterimResults());
        
        return url.toString();
    }
    
    private TranscriptionResult parseDeepgramResponse(String json) {
        try {
            DeepgramResponse response = objectMapper.readValue(json, DeepgramResponse.class);
            
            // Handle streaming response format (has direct channel)
            if (response.getChannel() != null) {
                var channel = response.getChannel();
                if (channel.getAlternatives() != null && !channel.getAlternatives().isEmpty()) {
                    var alternative = channel.getAlternatives().get(0);
                    
                    String transcript = alternative.getTranscript();
                    Double confidence = alternative.getConfidence();
                    
                    return TranscriptionResult.builder()
                            .text(transcript)
                            .confidence(confidence)
                            .language(response.getLanguage() != null ? response.getLanguage() : "en")
                            .timestamp(System.currentTimeMillis())
                            .error(false)
                            .metadata(response.toMap())
                            .build();
                }
            }
            
            // Handle batch response format (has results.channels)
            if (response.getResults() != null && response.getResults().getChannels() != null && !response.getResults().getChannels().isEmpty()) {
                var channel = response.getResults().getChannels().get(0);
                if (channel.getAlternatives() != null && !channel.getAlternatives().isEmpty()) {
                    var alternative = channel.getAlternatives().get(0);
                    
                    String transcript = alternative.getTranscript();
                    Double confidence = alternative.getConfidence();
                    
                    return TranscriptionResult.builder()
                            .text(transcript)
                            .confidence(confidence)
                            .language(response.getLanguage() != null ? response.getLanguage() : "en")
                            .timestamp(System.currentTimeMillis())
                            .error(false)
                            .metadata(response.toMap())
                            .build();
                }
            }
            
            return TranscriptionResult.builder()
                    .error(true)
                    .errorMessage("No transcription results found")
                    .timestamp(System.currentTimeMillis())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to parse Deepgram response", e);
            return TranscriptionResult.builder()
                    .error(true)
                    .errorMessage("Failed to parse response: " + e.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build();
        }
    }
    
    /**
     * WebSocket-based streaming session for Deepgram.
     */
    private class DeepgramStreamingSession implements StreamingSession {
        
        private final String sessionId;
        private final int sampleRate;
        private final String languageCode;
        private final Consumer<TranscriptionResult> resultCallback;
        private WebSocket webSocket;
        private volatile boolean active = false;
        private volatile boolean shouldReconnect = true;
        private final WordBuffer wordBuffer = new WordBuffer();
        private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
        private static final int MAX_RECONNECT_ATTEMPTS = 5;
        private static final long RECONNECT_DELAY_MS = 1000;
        private static final long MAX_RECONNECT_DELAY_MS = 30000;
        
        DeepgramStreamingSession(String sessionId, int sampleRate, String languageCode, 
                                Consumer<TranscriptionResult> resultCallback) {
            this.sessionId = sessionId;
            this.sampleRate = sampleRate;
            this.languageCode = languageCode;
            this.resultCallback = resultCallback;
            
            connect();
        }
        
        private void connect() {
            try {
                String url = buildStreamingUrl(sampleRate, languageCode);
                
                Request request = new Request.Builder()
                        .url(url)
                        .header("Authorization", "Token " + config.getDeepgram().getApiKey())
                        .build();
                
                WebSocketListener listener = new DeepgramWebSocketListener();
                webSocket = httpClient.newWebSocket(request, listener);
                active = true;
                
                log.debug("Deepgram streaming session {} started", sessionId);
                
            } catch (Exception e) {
                log.error("Failed to start Deepgram streaming session", e);
                if (shouldReconnect) {
                    scheduleReconnect();
                } else {
                    throw new RuntimeException("Failed to start streaming session", e);
                }
            }
        }
        
        private void scheduleReconnect() {
            int attempts = reconnectAttempts.incrementAndGet();
            
            if (attempts > MAX_RECONNECT_ATTEMPTS) {
                log.error("Max reconnection attempts ({}) reached for session {}", MAX_RECONNECT_ATTEMPTS, sessionId);
                active = false;
                shouldReconnect = false;
                
                resultCallback.accept(TranscriptionResult.builder()
                        .error(true)
                        .errorMessage("Max reconnection attempts reached")
                        .timestamp(System.currentTimeMillis())
                        .build());
                return;
            }
            
            long delay = Math.min(RECONNECT_DELAY_MS * (1L << (attempts - 1)), MAX_RECONNECT_DELAY_MS);
            
            log.info("Scheduling reconnection attempt {} for session {} in {}ms", attempts, sessionId, delay);
            
            reconnectExecutor.schedule(() -> {
                if (shouldReconnect && !active) {
                    log.info("Attempting to reconnect session {} (attempt {})", sessionId, attempts);
                    connect();
                }
            }, delay, TimeUnit.MILLISECONDS);
        }
        
        private void onConnectionSuccess() {
            reconnectAttempts.set(0);
            log.info("Deepgram streaming session {} reconnected successfully", sessionId);
        }
        
        private void onConnectionFailure(Throwable t) {
            active = false;
            log.error("Deepgram WebSocket connection failed for session {}: {}", sessionId, t.getMessage());
            
            if (shouldReconnect) {
                scheduleReconnect();
            } else {
                resultCallback.accept(TranscriptionResult.builder()
                        .error(true)
                        .errorMessage("WebSocket connection failed: " + t.getMessage())
                        .timestamp(System.currentTimeMillis())
                        .build());
            }
        }
        
        @Override
        public void sendAudio(byte[] audioData) {
            if (webSocket != null && active) {
                webSocket.send(ByteString.of(audioData));
            }
        }
        
        @Override
        public void close() {
            shouldReconnect = false;
            active = false;
            if (webSocket != null) {
                webSocket.close(1000, "Session closed");
                log.debug("Deepgram streaming session {} closed", sessionId);
            }
        }
        
        @Override
        public boolean isActive() {
            return active;
        }
        
        private class DeepgramWebSocketListener extends WebSocketListener {

            List<String> list = new ArrayList<>();
            
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.debug("Deepgram WebSocket opened for session {}", sessionId);
                onConnectionSuccess();
            }
            
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    DeepgramResponse response = objectMapper.readValue(text, DeepgramResponse.class);
                    
                    // Early exit if no channel or alternatives
                    if (response.getChannel() == null || response.getChannel().getAlternatives() == null 
                            || response.getChannel().getAlternatives().isEmpty()) {
                        return;
                    }
                    
                    // Find the best alternative (highest confidence)
                    DeepgramAlternative bestAlternative = findBestAlternative(response.getChannel().getAlternatives());
                    
                    // Convert words and update buffer
                    List<WordInfo> words = convertToWordInfoList(bestAlternative);
                    boolean isFinal = Boolean.TRUE.equals(response.getIsFinal());
                    SegmentResult segmentResult = wordBuffer.updateWords(words, isFinal);
                    
                    // Create and send result only if we have new content
                    if (StringUtils.isNotBlank(segmentResult.getText())) {
                        TranscriptionResult result = createTranscriptionResult(response, bestAlternative, segmentResult, isFinal);
                        list.add(text);
                        resultCallback.accept(result);
                    }
                } catch (Exception e) {
                    log.error("Error processing Deepgram message", e);
                }
            }
            
            private DeepgramAlternative findBestAlternative(List<DeepgramAlternative> alternatives) {
                DeepgramAlternative best = alternatives.get(0);
                
                for (DeepgramAlternative alt : alternatives) {
                    if (alt.getConfidence() == null || best.getConfidence() == null) {
                        continue;
                    }
                    
                    if (alt.getConfidence() > best.getConfidence()) {
                        best = alt;
                    }
                }
                
                return best;
            }
            
            private List<WordInfo> convertToWordInfoList(DeepgramAlternative alternative) {
                List<WordInfo> words = new ArrayList<>();
                
                if (alternative.getWords() == null) {
                    return words;
                }
                
                for (DeepgramWord word : alternative.getWords()) {
                    words.add(WordInfo.builder()
                            .word(word.getWord())
                            .punctuatedWord(word.getPunctuatedWord())
                            .start(word.getStart())
                            .end(word.getEnd())
                            .confidence(word.getConfidence())
                            .language(word.getLanguage())
                            .build());
                }
                
                return words;
            }
            
            private TranscriptionResult createTranscriptionResult(DeepgramResponse response, 
                    DeepgramAlternative alternative, SegmentResult segmentResult, boolean isFinal) {
                
                return TranscriptionResult.builder()
                        .text(alternative.getTranscript()) // Original transcript from this response
                        .mergedTranscript(segmentResult.getText()) // Current segment only (since last final)
                        .words(segmentResult.getWords()) // Words for current segment only
                        .confidence(segmentResult.getConfidence())
                        .language(response.getLanguage() != null ? response.getLanguage() : "en")
                        .timestamp(System.currentTimeMillis())
                        .interim(!isFinal)
                        .error(false)
                        .metadata(response.toMap())
                        .build();
            }
            
            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(1000, null);
                active = false;
                
                if (shouldReconnect && code != 1000) {
                    log.warn("Deepgram WebSocket closing unexpectedly for session {} with code {}: {}", sessionId, code, reason);
                    scheduleReconnect();
                }
            }
            
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                onConnectionFailure(t);
            }
        }
    }
}