# DriftKit Gemini Client

Google Gemini AI client implementation for DriftKit framework.

## Features

- Full support for Gemini 2.5 series models (Pro, Flash, Flash-Lite)
- Multimodal capabilities (text, images)
- Function calling and tools support
- Structured output with JSON schema
- Thinking/reasoning configuration for 2.5 models
- System instructions support
- Logprobs support
- Experimental models support (TTS, native audio)

## Supported Models

### Stable Models (Recommended)
- `gemini-2.5-pro` - State-of-the-art reasoning model
- `gemini-2.5-flash` - Best price-performance balance
- `gemini-2.5-flash-lite` - Optimized for cost efficiency

### Legacy Models (Deprecated)
- `gemini-1.5-flash`
- `gemini-1.5-pro`

### Experimental Models
- `gemini-2.0-flash-preview-image-generation` - Image generation
- `gemini-2.5-flash-preview-tts` - Text-to-speech
- `gemini-2.5-pro-preview-tts` - Text-to-speech (Pro)
- Native audio models for voice interactions

## Usage

### Basic Configuration

```java
EtlConfig.VaultConfig config = new EtlConfig.VaultConfig();
config.setName("gemini");
config.setApiKey("your-api-key");
config.setModel("gemini-2.5-flash");

ModelClient client = GeminiModelClient.create(config);
```

### Text Generation

```java
ModelTextRequest request = ModelTextRequest.builder()
    .messages(List.of(
        ModelContentMessage.create(Role.user, "Hello, how are you?")
    ))
    .build();

ModelTextResponse response = client.textToText(request);
System.out.println(response.getResponse());
```

### With System Instructions

```java
client.setSystemMessages(List.of("You are a helpful assistant."));

ModelTextRequest request = ModelTextRequest.builder()
    .messages(List.of(
        ModelContentMessage.create(Role.user, "What is the weather like?")
    ))
    .build();
```

### Structured Output

```java
// JSON Object mode
ModelTextRequest request = ModelTextRequest.builder()
    .messages(List.of(
        ModelContentMessage.create(Role.user, "Extract person info...")
    ))
    .responseFormat(ResponseFormat.jsonObject())
    .build();

// With JSON Schema
ResponseFormat.JsonSchema schema = new ResponseFormat.JsonSchema();
schema.setType("object");
schema.setProperties(Map.of(
    "name", new ResponseFormat.SchemaProperty("string", "Person's name", ...),
    "age", new ResponseFormat.SchemaProperty("integer", "Age", ...)
));

ModelTextRequest request = ModelTextRequest.builder()
    .messages(messages)
    .responseFormat(new ResponseFormat(ResponseFormat.ResponseType.JSON_SCHEMA, schema))
    .build();
```

### Function Calling

```java
ModelClient.Tool tool = ModelClient.Tool.builder()
    .type(ModelClient.ResponseFormatType.function)
    .function(ModelClient.ToolFunction.builder()
        .name("get_weather")
        .description("Get weather for a location")
        .parameters(...)
        .build())
    .build();

ModelTextRequest request = ModelTextRequest.builder()
    .messages(messages)
    .tools(List.of(tool))
    .toolMode(ModelTextRequest.ToolMode.auto)
    .build();
```

### Reasoning/Thinking (Gemini 2.5)

```java
ModelTextRequest request = ModelTextRequest.builder()
    .messages(messages)
    .model("gemini-2.5-pro")
    .reasoningEffort(ModelTextRequest.ReasoningEffort.high) // Enables thinking
    .build();
```

### Image Analysis

```java
byte[] imageBytes = Files.readAllBytes(Paths.get("image.png"));
ModelContentElement.ImageData imageData = 
    new ModelContentElement.ImageData(imageBytes, "image/png");

ModelTextRequest request = ModelTextRequest.builder()
    .messages(List.of(
        ModelContentMessage.create(Role.user, "What's in this image?", imageData)
    ))
    .build();

ModelTextResponse response = client.imageToText(request);
```

### Image Generation

```java
ModelImageRequest request = ModelImageRequest.builder()
    .model("gemini-2.0-flash-preview-image-generation")
    .prompt("A beautiful sunset over mountains")
    .n(1)
    .build();

ModelImageResponse response = client.textToImage(request);
```

## Configuration Options

- `apiKey` - Your Gemini API key (required)
- `model` - Model to use (default: gemini-2.5-flash)
- `temperature` - Sampling temperature (0.0-2.0)
- `maxTokens` - Maximum tokens to generate
- `topP` - Nucleus sampling parameter
- `topK` - Top-K sampling parameter
- `stop` - Stop sequences
- `jsonObject` - Enable JSON output support

## Testing

Set the `GEMINI_API_KEY` environment variable and run:

```bash
mvn test -pl driftkit-clients-gemini
```

For expensive tests (Pro models, image generation):
```bash
RUN_EXPENSIVE_TESTS=true mvn test -pl driftkit-clients-gemini
```

## Dependencies

The client uses Feign for HTTP communication and Jackson for JSON processing. All dependencies are managed through the parent POM.

## License

Part of the DriftKit framework.