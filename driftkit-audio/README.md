# DriftKit Audio Processing Library

A high-performance Java library for real-time audio processing with voice activity detection (VAD) and speech-to-text transcription. The library provides both framework-agnostic core functionality and convenient Spring Boot integration.

## Architecture

The library is built with a modular architecture:

- **Core Module** (`driftkit-audio-core`): Framework-agnostic audio processing engine
- **Spring Boot Starter** (`driftkit-audio-spring-boot-starter`): Spring Boot integration and auto-configuration

## Features

- **Real-time Audio Processing**: Process audio streams with low latency
- **Voice Activity Detection (VAD)**: Intelligent speech segment detection
- **Multiple Transcription Engines**:
    - AssemblyAI (batch mode)
    - Deepgram (batch and streaming modes)
- **Processing Modes**:
    - Batch: VAD-based chunking with complete segment transcription
    - Streaming: Real-time transcription with word-level timing
- **Type-safe Configuration**: Enum-based settings for all options
- **Multi-language Support**: 30+ languages with type-safe language codes
- **Session Isolation**: Concurrent processing of multiple audio streams
- **Audio Format Conversion**: Support for WAV, MP3, OGG, FLAC, AAC, M4A
- **Memory Efficient**: Automatic cleanup and segmented results

## Installation

### With Spring Boot (Recommended)

```xml
<dependency>
    <groupId>ai.driftkit</groupId>
    <artifactId>driftkit-audio-spring-boot-starter</artifactId>
    <version>0.8.2</version>
</dependency>
```

### Core Library Only

```xml
<dependency>
    <groupId>ai.driftkit</groupId>
    <artifactId>driftkit-audio-core</artifactId>
    <version>0.8.2</version>
</dependency>
```

## Requirements

- Java 21+
- Maven 3.6+
- FFmpeg (optional, for extended audio format support)

## Spring Boot Initialization

To use the audio module in your Spring Boot application, the module will be automatically configured via Spring Boot's auto-configuration mechanism:

```java
@SpringBootApplication
// No additional annotations needed - auto-configuration handles everything
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

The module provides:
- **Auto-configuration class**: `ai.driftkit.audio.AutoConfiguration`
- **Component scanning**: Automatically scans `ai.driftkit.audio` package
- **Configuration properties**: `AudioProcessingConfig` with prefix `audio.processing`

## Quick Start

### Spring Boot Integration

#### 1. Configuration

Add to your `application.yml`:

```yaml
audio:
  processing:
    engine: DEEPGRAM
    processing-mode: STREAMING
    sample-rate: 16000
    buffer-size: 4096
    
    deepgram:
      api-key: ${DEEPGRAM_API_KEY}
      language: ENGLISH
      model: "nova-3"
      punctuate: true
      interim-results: true
```

#### 2. Basic Usage

```java
@Component
public class AudioService {
    
    @Autowired
    private SpringAudioSessionManager sessionManager;
    
    public void transcribeAudio(String userId, byte[] audioData) {
        // Create session with callback
        sessionManager.createSession(userId, result -> {
            if (!result.isError()) {
                if (result.isInterim()) {
                    System.out.println("[LIVE] " + result.getMergedTranscript());
                } else {
                    System.out.println("[FINAL] " + result.getMergedTranscript());
                }
            }
        });
        
        // Process audio
        sessionManager.processAudioChunk(userId, audioData);
        
        // Close session when done
        sessionManager.closeSession(userId);
    }
}
```

### Core Library Usage (Without Spring)

```java
public class AudioTranscriptionExample {
    
    public void transcribeAudio() {
        // Configure the library
        CoreAudioConfig config = new CoreAudioConfig();
        config.setEngine(EngineType.DEEPGRAM);
        config.setProcessingMode(ProcessingMode.BATCH);
        config.setSampleRate(16000);
        config.setBufferSize(4096);
        
        // Configure Deepgram
        DeepgramConfig deepgramConfig = new DeepgramConfig();
        deepgramConfig.setApiKey("your-api-key");
        deepgramConfig.setLanguage(LanguageCode.ENGLISH);
        deepgramConfig.setModel("nova-3");
        config.setDeepgram(deepgramConfig);
        
        // Create session manager
        AudioSessionManager sessionManager = new AudioSessionManager(config);
        
        // Create session
        sessionManager.createSession("user-123", result -> {
            if (!result.isError()) {
                System.out.println("Transcription: " + result.getText());
            }
        });
        
        // Process audio
        byte[] audioData = captureAudio();
        sessionManager.processAudioChunk("user-123", audioData);
        
        // Clean up
        sessionManager.closeSession("user-123");
        sessionManager.shutdown();
    }
}
```

## Configuration Options

### Engine Types (Type-safe Enums)

```java
EngineType.ASSEMBLYAI  // AssemblyAI transcription service
EngineType.DEEPGRAM    // Deepgram transcription service
```

### Processing Modes

```java
ProcessingMode.BATCH     // VAD-based chunking with complete segments
ProcessingMode.STREAMING // Real-time streaming transcription
```

### Audio Formats

```java
AudioFormatType.WAV      // Lossless WAV format
AudioFormatType.MP3      // Compressed MP3 format
AudioFormatType.OGG      // Ogg Vorbis format
AudioFormatType.FLAC     // Lossless FLAC format
AudioFormatType.AAC      // Advanced Audio Codec
AudioFormatType.M4A      // MPEG-4 Audio
```

### Language Support

```java
LanguageCode.ENGLISH
LanguageCode.SPANISH
LanguageCode.FRENCH
LanguageCode.GERMAN
LanguageCode.CHINESE_SIMPLIFIED
LanguageCode.CHINESE_TRADITIONAL
LanguageCode.JAPANESE
LanguageCode.KOREAN
LanguageCode.RUSSIAN
LanguageCode.ARABIC
LanguageCode.HINDI
// ... and 20+ more languages
```

## Advanced Usage Examples

### Streaming Mode with Word-level Timing

```java
@Component
public class StreamingTranscriptionService {
    
    @Autowired
    private SpringAudioSessionManager sessionManager;
    
    public void startStreaming(String sessionId) {
        sessionManager.createSession(sessionId, result -> {
            if (result.isError()) {
                log.error("Transcription error: {}", result.getErrorMessage());
                return;
            }
            
            if (result.isInterim()) {
                // Live transcription updates
                displayLiveTranscription(result.getMergedTranscript());
                
                // Show word-level timing for live captions
                result.getWords().forEach(word -> {
                    System.out.printf("%s [%.1f-%.1fs] conf:%.2f\n",
                        word.getPunctuatedWord(),
                        word.getStart(),
                        word.getEnd(),
                        word.getConfidence()
                    );
                });
            } else {
                // Final segment - save permanently
                saveTranscriptionSegment(result.getMergedTranscript());
            }
        });
        
        // Start streaming audio
        streamAudioToSession(sessionId);
    }
}
```

### Multi-language Processing

```java
@Component
public class MultiLanguageService {
    
    @Autowired
    private SpringAudioSessionManager sessionManager;
    
    public void processMultiLanguageAudio(String sessionId, LanguageCode language) {
        // Create session with language-specific callback
        sessionManager.createSession(sessionId, result -> {
            if (!result.isError()) {
                processLanguageSpecificResult(result, language);
            }
        });
        
        // Process audio with language context
        processAudioWithLanguage(sessionId, language);
    }
    
    private void processLanguageSpecificResult(TranscriptionResult result, LanguageCode language) {
        switch (language) {
            case ENGLISH -> processEnglishText(result.getText());
            case SPANISH -> processSpanishText(result.getText());
            case CHINESE_SIMPLIFIED -> processChineseText(result.getText());
            default -> processGenericText(result.getText());
        }
    }
}
```

### Audio Format Conversion

```java
@Component
public class AudioFormatService {
    
    @Autowired
    private AudioConverter audioConverter;
    
    public byte[] convertAudio(byte[] rawPcmData, AudioFormatType targetFormat) {
        try {
            return audioConverter.convertToFormat(rawPcmData, 16000, targetFormat);
        } catch (IOException | InterruptedException e) {
            log.error("Audio conversion failed", e);
            throw new AudioProcessingException("Failed to convert to " + targetFormat.getDisplayName(), e);
        }
    }
    
    public void demonstrateConversionMethods() {
        // Get conversion capabilities
        AudioConverter.ConversionInfo info = audioConverter.getConversionInfo("mp3");
        System.out.println("MP3 conversion: " + info.getPreferredMethod());
        
        // Check performance characteristics
        AudioConverter.PerformanceInfo perf = audioConverter.getPerformanceInfo("wav");
        System.out.println("WAV performance: " + perf.getSpeed() + " speed, " + perf.getResourceUsage() + " resources");
    }
}
```

### Concurrent Session Management

```java
@Component
public class ConcurrentSessionService {
    
    @Autowired
    private SpringAudioSessionManager sessionManager;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    public void processConcurrentSessions(List<String> userIds) {
        userIds.forEach(userId -> {
            executorService.submit(() -> {
                try {
                    sessionManager.createSession(userId, result -> {
                        if (!result.isError()) {
                            storeUserTranscription(userId, result.getText());
                        }
                    });
                    
                    // Process audio for this user
                    processUserAudio(userId);
                    
                } finally {
                    sessionManager.closeSession(userId);
                }
            });
        });
    }
}
```

## Complete Configuration Reference

```yaml
# Spring Boot configuration
spring:
  main:
    allow-circular-references: true  # Required for audio processing

audio:
  processing:
    # Engine and mode selection
    engine: DEEPGRAM                    # ASSEMBLYAI | DEEPGRAM
    processing-mode: STREAMING          # BATCH | STREAMING
    
    # Audio settings
    sample-rate: 16000                  # Sample rate in Hz
    buffer-size: 4096                   # Buffer size in bytes
    
    # VAD settings (batch mode)
    silence-duration-ms: 1500           # Silence duration to trigger processing
    min-chunk-duration-seconds: 2       # Minimum chunk duration
    max-chunk-duration-seconds: 30      # Maximum chunk duration
    
    # VAD configuration
    vad:
      threshold: 0.3                    # Voice activity threshold (0.0-1.0)
      silence-duration-ms: 1500         # Silence duration for VAD
    
    # Debug and development
    debug:
      enabled: false                    # Enable debug mode
      output-path: "./debug/audio"      # Debug output directory
      save-raw-audio: false             # Save raw PCM audio
      save-processed-audio: true        # Save processed audio files
    
    # AssemblyAI configuration
    assemblyai:
      api-key: ${ASSEMBLYAI_API_KEY}    # AssemblyAI API key
      language-code: ENGLISH            # Language code enum
    
    # Deepgram configuration
    deepgram:
      api-key: ${DEEPGRAM_API_KEY}      # Deepgram API key
      language: ENGLISH                 # Language enum
      model: "nova-3"                   # Model name
      punctuate: true                   # Enable punctuation
      interim-results: true             # Enable interim results (streaming)
      detect-language: false            # Auto-detect language
      diarize: false                    # Enable speaker diarization
      
# Logging configuration
logging:
  level:
    ai.driftkit.audio: INFO
    ai.driftkit.audio.engine: DEBUG
    ai.driftkit.audio.converter: DEBUG
```

## API Reference

### SpringAudioSessionManager (Spring Boot)

```java
// Create session with callback
void createSession(String sessionId, Consumer<TranscriptionResult> callback)

// Process audio chunk
void processAudioChunk(String sessionId, byte[] audioData)

// Close session
void closeSession(String sessionId)

// Check session existence
boolean hasSession(String sessionId)

// Get active session count
int getActiveSessionCount()
```

### AudioSessionManager (Core)

```java
// Constructor
AudioSessionManager(CoreAudioConfig config)

// Session management
void createSession(String sessionId, Consumer<TranscriptionResult> callback)
void processAudioChunk(String sessionId, byte[] audioData)
void closeSession(String sessionId)
boolean hasSession(String sessionId)

// Lifecycle
void shutdown()
```

### AudioConverter

```java
// Convert audio to specific format
byte[] convertToFormat(byte[] rawPcmData, int sampleRate, AudioFormatType audioFormat)

// Fast WAV conversion
byte[] convertToWavFast(byte[] rawPcmData, int sampleRate)

// Get conversion capabilities
ConversionInfo getConversionInfo(String format)
PerformanceInfo getPerformanceInfo(String format)
boolean isPureJavaSupported(String format)
```

### TranscriptionResult

```java
// Core content
String getText()                    // Original transcript
String getMergedTranscript()        // Deduplicated segment (streaming)
Double getConfidence()              // Confidence score (0.0-1.0)
String getLanguage()                // Language code

// Status information
boolean isError()                   // Error flag
String getErrorMessage()            // Error description
boolean isInterim()                 // Interim vs final result
Long getTimestamp()                 // Processing timestamp

// Word-level data (streaming mode)
List<WordInfo> getWords()           // Word-level details
Map<String, Object> getMetadata()   // Engine-specific metadata
```

### WordInfo (Streaming Mode)

```java
String getWord()                    // Base word text
String getPunctuatedWord()          // Word with punctuation
Double getStart()                   // Start time (seconds)
Double getEnd()                     // End time (seconds)
Double getConfidence()              // Word confidence
String getLanguage()                // Word language (multilingual)
```

## Error Handling

The library provides comprehensive error handling through the callback mechanism:

```java
sessionManager.createSession(sessionId, result -> {
    if (result.isError()) {
        String error = result.getErrorMessage();
        
        // Handle specific error types
        if (error.contains("API key")) {
            handleAuthenticationError();
        } else if (error.contains("network")) {
            handleNetworkError();
        } else if (error.contains("format")) {
            handleFormatError();
        } else {
            handleGenericError(error);
        }
    } else {
        // Process successful result
        processTranscriptionResult(result);
    }
});
```

## Performance Optimization

### Memory Management

The library implements automatic memory management:

- **Segmented Results**: Streaming mode returns only current segments
- **Automatic Cleanup**: Old words and buffers are cleaned automatically
- **Session Isolation**: Each session has independent memory usage

### Conversion Performance

Audio conversion methods are optimized by preference:

1. **Java Sound API**: Fastest for WAV, AU, AIFF (pure Java)
2. **JAVE Library**: Fast for MP3, OGG, FLAC (embedded FFmpeg)
3. **FFmpeg**: Fallback for all formats (external binary)

### Concurrent Processing

The library supports high-concurrency scenarios:

```java
// Configure for high concurrency
audio:
  processing:
    buffer-size: 2048          # Smaller buffers for faster processing
    silence-duration-ms: 1000  # Shorter silence detection
```

## Building from Source

```bash
# Clone repository
git clone https://github.com/driftkit-ai/driftkit-ai-audio.git
cd driftkit-ai-audio

# Build all modules
mvn clean compile

# Run tests
mvn test

# Package both modules
mvn clean package

# Install to local repository
mvn clean install

# Build only core module
mvn clean package -pl audio-processing-core

# Build only Spring Boot starter
mvn clean package -pl audio-processing-spring-boot-starter
```

## Module Structure

```
driftkit-ai-audio/
├── pom.xml                                    # Parent POM
├── audio-processing-core/                     # Core library
│   ├── pom.xml
│   └── src/main/java/ai/driftkit/audio/
│       ├── core/                             # Core configuration
│       ├── converter/                        # Audio format conversion
│       ├── engine/                           # Transcription engines
│       ├── model/                            # Data models
│       ├── processor/                        # Audio processing
│       └── session/                          # Session management
└── audio-processing-spring-boot-starter/     # Spring Boot integration
    ├── pom.xml
    └── src/main/java/ai/driftkit/audio/
        ├── autoconfigure/                    # Spring auto-configuration
        └── service/                          # Spring service wrappers
```

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## License

This project is licensed under the Apache License, Version 2.0 - see the LICENSE file for details.

## Demo Examples

### 1. Voice Assistant Integration

This example demonstrates building a voice-powered assistant with real-time transcription.

```java
@Service
public class VoiceAssistantService {
    
    private final SpringAudioSessionManager audioManager;
    private final ChatService chatService;
    private final Map<String, ConversationContext> conversations = new ConcurrentHashMap<>();
    
    @Autowired
    public VoiceAssistantService(SpringAudioSessionManager audioManager, ChatService chatService) {
        this.audioManager = audioManager;
        this.chatService = chatService;
    }
    
    public void startVoiceSession(String userId, Consumer<AssistantResponse> responseCallback) {
        // Initialize conversation context
        ConversationContext context = new ConversationContext(userId);
        conversations.put(userId, context);
        
        // Create audio session with streaming transcription
        audioManager.createSession(userId, result -> {
            if (result.isError()) {
                responseCallback.accept(new AssistantResponse(true, result.getErrorMessage()));
                return;
            }
            
            if (result.isInterim()) {
                // Show live transcription to user
                context.updateLiveTranscript(result.getMergedTranscript());
                responseCallback.accept(new AssistantResponse(
                    AssistantResponse.Type.LIVE_TRANSCRIPT,
                    result.getMergedTranscript()
                ));
            } else {
                // Process final utterance
                String userInput = result.getText();
                context.addUserMessage(userInput);
                
                // Generate AI response
                String aiResponse = chatService.generateResponse(
                    context.getConversationHistory()
                );
                context.addAssistantMessage(aiResponse);
                
                responseCallback.accept(new AssistantResponse(
                    AssistantResponse.Type.AI_RESPONSE,
                    aiResponse
                ));
            }
        });
    }
    
    public void processVoiceChunk(String userId, byte[] audioData) {
        audioManager.processAudioChunk(userId, audioData);
    }
    
    public void endVoiceSession(String userId) {
        audioManager.closeSession(userId);
        ConversationContext context = conversations.remove(userId);
        if (context != null) {
            saveConversationHistory(context);
        }
    }
    
    @Data
    public static class ConversationContext {
        private final String userId;
        private final List<Message> messages = new ArrayList<>();
        private String currentLiveTranscript = "";
        private final long startTime = System.currentTimeMillis();
        
        public void addUserMessage(String text) {
            messages.add(new Message(MessageRole.USER, text));
        }
        
        public void addAssistantMessage(String text) {
            messages.add(new Message(MessageRole.ASSISTANT, text));
        }
        
        public List<Message> getConversationHistory() {
            return new ArrayList<>(messages);
        }
    }
}
```

### 2. Meeting Transcription Service

This example shows how to transcribe meetings with speaker identification and summary generation.

```java
@Service
public class MeetingTranscriptionService {
    
    private final SpringAudioSessionManager audioManager;
    private final SummaryService summaryService;
    
    public void transcribeMeeting(String meetingId, List<Participant> participants) {
        MeetingTranscript transcript = new MeetingTranscript(meetingId, participants);
        
        // Configure for meeting transcription
        audioManager.createSession(meetingId, result -> {
            if (result.isError()) {
                transcript.addError(result.getErrorMessage());
                return;
            }
            
            // Process transcription with timing
            TranscriptSegment segment = new TranscriptSegment();
            segment.setText(result.getText());
            segment.setTimestamp(result.getTimestamp());
            segment.setConfidence(result.getConfidence());
            
            // Extract speaker info if available
            if (result.getMetadata() != null && result.getMetadata().containsKey("speaker")) {
                segment.setSpeakerId((String) result.getMetadata().get("speaker"));
            }
            
            transcript.addSegment(segment);
            
            // Auto-generate summary every 5 minutes
            if (transcript.getDurationMinutes() % 5 == 0) {
                generateIntermediateSummary(transcript);
            }
        });
    }
    
    public MeetingSummary finalizeMeeting(String meetingId) {
        audioManager.closeSession(meetingId);
        
        MeetingTranscript transcript = getTranscript(meetingId);
        
        // Generate comprehensive summary
        MeetingSummary summary = new MeetingSummary();
        summary.setMeetingId(meetingId);
        summary.setDuration(transcript.getDurationMinutes());
        summary.setParticipants(transcript.getParticipants());
        
        // Extract key points
        summary.setKeyPoints(summaryService.extractKeyPoints(
            transcript.getFullText()
        ));
        
        // Identify action items
        summary.setActionItems(summaryService.extractActionItems(
            transcript.getFullText()
        ));
        
        // Generate executive summary
        summary.setExecutiveSummary(summaryService.generateSummary(
            transcript.getFullText(),
            SummaryType.EXECUTIVE
        ));
        
        return summary;
    }
    
    @Data
    public static class MeetingTranscript {
        private final String meetingId;
        private final List<Participant> participants;
        private final List<TranscriptSegment> segments = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final long startTime = System.currentTimeMillis();
        
        public String getFullText() {
            return segments.stream()
                .map(TranscriptSegment::getText)
                .collect(Collectors.joining(" "));
        }
        
        public long getDurationMinutes() {
            return (System.currentTimeMillis() - startTime) / 60000;
        }
    }
}
```

### 3. Multi-Language Customer Support

This example demonstrates handling customer support calls in multiple languages.

```java
@Service
public class MultilingualSupportService {
    
    private final SpringAudioSessionManager audioManager;
    private final LanguageDetectionService languageService;
    private final TranslationService translationService;
    
    public void handleSupportCall(String callId, String agentLanguage) {
        SupportCallContext context = new SupportCallContext(callId, agentLanguage);
        
        audioManager.createSession(callId, result -> {
            if (result.isError()) {
                context.setError(result.getErrorMessage());
                return;
            }
            
            // Detect customer language
            String detectedLanguage = result.getLanguage();
            if (context.getCustomerLanguage() == null) {
                context.setCustomerLanguage(detectedLanguage);
                notifyAgent("Customer language detected: " + detectedLanguage);
            }
            
            // Process transcription
            String originalText = result.getText();
            context.addCustomerUtterance(originalText);
            
            // Translate if needed
            if (!detectedLanguage.equals(agentLanguage)) {
                String translatedText = translationService.translate(
                    originalText,
                    detectedLanguage,
                    agentLanguage
                );
                
                // Provide translation to agent
                notifyAgent(String.format(
                    "[%s]: %s\n[Translation]: %s",
                    detectedLanguage,
                    originalText,
                    translatedText
                ));
                
                context.addTranslation(originalText, translatedText);
            } else {
                notifyAgent(originalText);
            }
            
            // Analyze sentiment
            SentimentAnalysis sentiment = analyzeSentiment(originalText);
            context.updateSentiment(sentiment);
            
            // Suggest responses based on context
            if (result.isInterim() == false) {
                List<String> suggestions = generateResponseSuggestions(
                    context.getConversationHistory(),
                    detectedLanguage
                );
                provideSuggestions(suggestions);
            }
        });
    }
    
    private List<String> generateResponseSuggestions(List<String> history, String language) {
        // Generate contextual response suggestions
        List<String> suggestions = new ArrayList<>();
        
        // Analyze conversation pattern
        String lastCustomerMessage = history.get(history.size() - 1);
        
        if (lastCustomerMessage.contains("problem") || lastCustomerMessage.contains("issue")) {
            suggestions.add("I understand you're experiencing an issue. Let me help you resolve this.");
            suggestions.add("I'm sorry to hear that. Can you provide more details about the problem?");
        } else if (lastCustomerMessage.contains("thank")) {
            suggestions.add("You're welcome! Is there anything else I can help you with?");
            suggestions.add("Happy to help! Please don't hesitate to reach out if you need further assistance.");
        }
        
        // Translate suggestions to customer language if needed
        if (!language.equals("en")) {
            return suggestions.stream()
                .map(s -> translationService.translate(s, "en", language))
                .collect(Collectors.toList());
        }
        
        return suggestions;
    }
}
```

### 4. Podcast/Audio Content Processing

This example shows batch processing of audio content for podcasts or recordings.

```java
@Service
public class PodcastProcessingService {
    
    private final SpringAudioSessionManager audioManager;
    private final AudioConverter audioConverter;
    private final ContentAnalysisService contentService;
    
    public PodcastAnalysis processPodcast(String podcastId, byte[] audioFile, AudioFormatType format) {
        PodcastAnalysis analysis = new PodcastAnalysis(podcastId);
        
        // Convert to optimal format if needed
        byte[] processedAudio = audioFile;
        if (format != AudioFormatType.WAV) {
            processedAudio = audioConverter.convertToFormat(
                audioFile, 
                16000, 
                AudioFormatType.WAV
            );
        }
        
        // Process with batch mode for better accuracy
        audioManager.createSession(podcastId, result -> {
            if (result.isError()) {
                analysis.addError(result.getErrorMessage());
                return;
            }
            
            // Collect transcript segments
            TranscriptChapter chapter = new TranscriptChapter();
            chapter.setText(result.getText());
            chapter.setStartTime(analysis.getCurrentTime());
            chapter.setConfidence(result.getConfidence());
            
            analysis.addChapter(chapter);
        });
        
        // Process entire audio file
        audioManager.processAudioChunk(podcastId, processedAudio);
        audioManager.closeSession(podcastId);
        
        // Perform content analysis
        performContentAnalysis(analysis);
        
        return analysis;
    }
    
    private void performContentAnalysis(PodcastAnalysis analysis) {
        String fullTranscript = analysis.getFullTranscript();
        
        // Extract topics
        analysis.setTopics(contentService.extractTopics(fullTranscript));
        
        // Generate chapters with titles
        List<Chapter> chapters = contentService.generateChapters(fullTranscript);
        analysis.setChapters(chapters);
        
        // Extract key quotes
        analysis.setKeyQuotes(contentService.extractQuotes(fullTranscript));
        
        // Generate SEO metadata
        SEOMetadata seo = new SEOMetadata();
        seo.setTitle(contentService.generateTitle(fullTranscript));
        seo.setDescription(contentService.generateDescription(fullTranscript));
        seo.setKeywords(contentService.extractKeywords(fullTranscript));
        analysis.setSeoMetadata(seo);
        
        // Generate social media snippets
        analysis.setSocialMediaSnippets(
            generateSocialMediaContent(fullTranscript)
        );
    }
    
    private List<SocialMediaSnippet> generateSocialMediaContent(String transcript) {
        List<SocialMediaSnippet> snippets = new ArrayList<>();
        
        // Twitter/X snippet
        snippets.add(new SocialMediaSnippet(
            Platform.TWITTER,
            contentService.generateTweet(transcript)
        ));
        
        // LinkedIn snippet  
        snippets.add(new SocialMediaSnippet(
            Platform.LINKEDIN,
            contentService.generateLinkedInPost(transcript)
        ));
        
        // Instagram caption
        snippets.add(new SocialMediaSnippet(
            Platform.INSTAGRAM,
            contentService.generateInstagramCaption(transcript)
        ));
        
        return snippets;
    }
}
```

### 5. Real-time Translation Service

This example demonstrates real-time audio translation for live events.

```java
@Service
public class LiveTranslationService {
    
    private final SpringAudioSessionManager audioManager;
    private final TranslationService translationService;
    private final WebSocketService webSocketService;
    
    public void startLiveTranslation(String sessionId, String sourceLanguage, List<String> targetLanguages) {
        LiveTranslationSession session = new LiveTranslationSession(
            sessionId, sourceLanguage, targetLanguages
        );
        
        audioManager.createSession(sessionId, result -> {
            if (result.isError()) {
                broadcastError(sessionId, result.getErrorMessage());
                return;
            }
            
            // Process source transcription
            String sourceText = result.getText();
            session.addSourceSegment(sourceText, result.isInterim());
            
            // Broadcast source transcription
            broadcastTranscription(sessionId, sourceLanguage, sourceText, result.isInterim());
            
            // Translate to target languages in parallel
            CompletableFuture<?>[] translationFutures = targetLanguages.stream()
                .map(targetLang -> CompletableFuture.runAsync(() -> {
                    try {
                        String translatedText = translationService.translate(
                            sourceText,
                            sourceLanguage,
                            targetLang
                        );
                        
                        session.addTranslation(targetLang, translatedText);
                        broadcastTranscription(sessionId, targetLang, translatedText, result.isInterim());
                        
                    } catch (Exception e) {
                        log.error("Translation failed for language: " + targetLang, e);
                    }
                }))
                .toArray(CompletableFuture[]::new);
            
            // Wait for all translations if final
            if (!result.isInterim()) {
                CompletableFuture.allOf(translationFutures).join();
                session.finalizeCurrentSegment();
            }
        });
    }
    
    private void broadcastTranscription(String sessionId, String language, String text, boolean isInterim) {
        TranslationUpdate update = new TranslationUpdate();
        update.setSessionId(sessionId);
        update.setLanguage(language);
        update.setText(text);
        update.setInterim(isInterim);
        update.setTimestamp(System.currentTimeMillis());
        
        webSocketService.broadcast(
            "/translation/" + sessionId + "/" + language,
            update
        );
    }
    
    public TranslationStatistics getSessionStatistics(String sessionId) {
        LiveTranslationSession session = getSession(sessionId);
        
        TranslationStatistics stats = new TranslationStatistics();
        stats.setSessionId(sessionId);
        stats.setDurationSeconds(session.getDurationSeconds());
        stats.setSourceSegments(session.getSourceSegmentCount());
        stats.setTotalTranslations(session.getTotalTranslationCount());
        stats.setAverageDelay(session.getAverageTranslationDelay());
        stats.setLanguageBreakdown(session.getLanguageStatistics());
        
        return stats;
    }
}
```

## Support

For issues, questions, or contributions:

- GitHub Issues: [Report bugs or request features](https://github.com/driftkit-ai/driftkit-ai-audio/issues)