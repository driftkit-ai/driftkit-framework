# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DriftKit is an AI ETL and Workflow Processing Framework built with Java 21 and Spring Boot 3.3.1. It provides a modular architecture for building AI-powered applications with workflow orchestration, prompt engineering, and vector search capabilities.

## Build Commands

### Backend (Java/Maven)

**Important**: The project uses Lombok annotation processing which is configured in the parent POM.

```bash
# Build entire project
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Build specific module with dependencies
mvn clean install -pl driftkit-common -am

# Build audio module
mvn clean install -pl driftkit-audio/driftkit-audio-core,driftkit-audio/driftkit-audio-spring-boot-starter -am

# Build workflow engine modules
mvn clean install -pl driftkit-workflow-engine-core,driftkit-workflow-engine-agents,driftkit-workflow-test-framework -am

# Compile specific module only
mvn clean compile -pl driftkit-common

# Run tests
mvn test

# Package as JAR
mvn package

# Run Spring Boot application (from a module with main class)
mvn spring-boot:run
```

### Frontend (Vue.js - Context Engineering UI)

Navigate to frontend directory first:
```bash
cd driftkit-context-engineering/driftkit-context-engineering-spring-boot-starter/src/main/frontend
```

Then run:
```bash
# Install dependencies
yarn install

# Development server with hot-reload (http://localhost:8080)
yarn serve

# Production build
yarn build

# Lint and fix files
yarn lint
```

## Architecture Overview

### Module Structure

1. **driftkit-common**: Core utilities and shared domain objects
   - Chat memory management (`ChatMemoryService`)
   - Document processing (`DocumentService`, `DocumentSplitter`)
   - Text similarity and tokenization utilities
   - Variable extraction services

2. **driftkit-clients**: AI model client abstraction layer
   - Core interfaces in `driftkit-clients-core`
   - OpenAI implementation in `driftkit-clients-openai`
   - DeepSeek implementation in `driftkit-clients-deepseek` (OpenAI-compatible with thinking/reasoning mode and prefix cache metrics)
   - Gemini implementation in `driftkit-clients-gemini`
   - Claude implementation in `driftkit-clients-claude` (with prompt caching via `cache_control`)
   - Spring AI integration in `driftkit-clients-spring-ai`
   - Spring Boot starter for auto-configuration
   - Unified cache metrics (`CacheUsage`) and cache control (`CachePolicy`, `CacheControl`) across all providers

3. **driftkit-embedding**: Text embedding capabilities
   - Core embedding interfaces
   - Implementations: OpenAI, Cohere, local BERT models
   - Spring AI integration for multiple providers
   - LangChain4j embedding support

4. **driftkit-vector**: Vector storage and retrieval
   - In-memory, file-based, and Pinecone implementations
   - Document management with metadata support
   - RESTful endpoints via Spring Boot starter

5. **driftkit-workflow-engine-core**: New workflow execution engine
   - Fluent builder API for workflow construction
   - Annotation-based workflow definitions
   - Async step support with progress tracking
   - Human-in-the-loop capabilities
   - Retry policies and error handling

6. **driftkit-workflow-engine-agents**: Multi-agent patterns
   - LLM agent implementations
   - Sequential, hierarchical, and loop agents
   - Agent communication and coordination

7. **driftkit-workflow-test-framework**: Testing utilities
   - Mock builders for workflow steps
   - Execution tracking and assertions
   - JUnit 5 integration

8. **driftkit-context-engineering**: Prompt management system
   - Multiple storage backends (filesystem, in-memory, MongoDB)
   - Template engine integration
   - Vue.js frontend for prompt engineering
   - Test sets and evaluation capabilities
   - Spring AI integration for using DriftKit prompts with Spring AI ChatClient
   - Full bidirectional integration with tracing support

9. **driftkit-workflows-examples**: Reference implementations
   - Example workflows using the new engine
   - Spring Boot service wrappers

10. **driftkit-audio**: Audio processing and transcription capabilities
   - Core audio processing in `driftkit-audio-core`
   - Spring Boot starter for auto-configuration
   - Multi-engine support (AssemblyAI, Deepgram)
   - Batch and streaming transcription modes
   - Voice Activity Detection (VAD) with adaptive thresholds
   - Multi-format audio conversion (WAV, MP3, FLAC, OGG, AAC, M4A)
   - Session-based audio management with memory-efficient streaming


### Key Design Patterns

- **Modular Architecture**: Each component has a core module and optional Spring Boot starter
- **Interface-based Design**: Core abstractions defined in `*-core` modules
- **Spring Boot Auto-configuration**: Starters provide automatic configuration
- **Event-driven Workflows**: Workflows can emit and react to events
- **Template-based Prompts**: Support for variable substitution in prompts

### Integration Points

- **AI Models**: Abstracted through `ModelClient` interface (OpenAI, Claude, Gemini, DeepSeek, Spring AI)
- **Prompt Caching**: Unified `CachePolicy`/`CacheControl`/`CacheUsage` across Claude (manual), OpenAI (auto), DeepSeek (auto)
- **Reasoning/Thinking**: Unified `ReasoningEffort` mapped to provider-specific controls (OpenAI `reasoning_effort`, Claude `thinking.budget_tokens`, DeepSeek `thinking.type`)
- **Embeddings**: Unified `EmbeddingModel` interface
- **Vector Stores**: Common `VectorStore` interface with multiple backends
- **Workflows**: Use `WorkflowBuilder` fluent API for workflow construction
- **Prompt Storage**: Implement `PromptService` for custom storage
- **Audio Processing**: Unified `TranscriptionEngine` interface with multiple provider implementations
- **Multi-Agent Systems**: Leverage `driftkit-workflow-engine-agents` for complex agent interactions
- **Spring AI**: Full integration allowing Spring AI ChatClient to use DriftKit prompts with tracing

### Frontend Architecture

The Vue.js application in context-engineering provides:
- Prompt management and versioning
- Test set creation and management
- Evaluation run tracking
- Real-time prompt testing with trace visualization

## Spring AI Integration

DriftKit provides seamless integration with Spring AI, allowing you to leverage DriftKit's prompt management system with Spring AI's ChatClient:

### Using DriftKit Prompts with Spring AI

```java
// Use DriftKit prompts through enhanced ChatClient
@Component
public class AIService {
    private final DriftKitChatClient chatClient;
    
    public String generateResponse(String input) {
        return chatClient.promptById("my.prompt.id")
            .withVariable("input", input)
            .withLanguage(Language.ENGLISH)
            .call()
            .content();
    }
}

// Or use Spring AI ChatClient directly with DriftKit prompts
@Component
public class SpringAIService {
    private final ChatClient chatClient;
    private final DriftKitPromptProvider promptProvider;
    
    public String process(Map<String, Object> variables) {
        // Get prompt from DriftKit with full configuration
        var config = promptProvider.getPrompt("analysis.prompt", Language.ENGLISH);
        
        return chatClient.prompt()
            .system(config.getSystemMessage())
            .user(u -> u.text(config.getUserMessage()).params(variables))
            .options(opt -> opt.temperature(config.getTemperature()))
            .call()
            .content();
    }
}
```

### Configuration

```yaml
driftkit:
  spring-ai:
    application-name: "my-app"
    tracing:
      enabled: true  # Enable DriftKit tracing for Spring AI calls
    chat-client:
      enabled: true  # Create DriftKitChatClient bean
    memory:
      enabled: true  # Enable conversation memory
```

### Key Features

- **Prompt Management**: Use DriftKit's visual prompt editor with Spring AI
- **Full Tracing**: All Spring AI calls are traced through DriftKit's monitoring system
- **Language Support**: Multi-language prompts work seamlessly with Spring AI
- **Type Safety**: Structured output support with Spring AI's entity mapping
- **Advisors**: DriftKit provides tracing advisor for Spring AI ChatClient

## Important Technical Details

- **Java Version**: Requires Java 21
- **Spring Boot Version**: 3.3.1
- **Spring AI Version**: 1.0.1
- **LangChain4j Version**: 0.35.0
- **Frontend Framework**: Vue.js 3 with TypeScript
- **Build Tool**: Maven (no wrapper included)
- **Key Libraries**: Jackson for JSON, Feign for HTTP clients, Lombok for boilerplate reduction

## Security Note

The root pom.xml contains an exposed OpenAI API key that should be externalized to environment variables or secure configuration management.