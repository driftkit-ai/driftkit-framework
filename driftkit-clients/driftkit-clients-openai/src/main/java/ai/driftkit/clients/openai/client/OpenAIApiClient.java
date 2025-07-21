package ai.driftkit.clients.openai.client;

import ai.driftkit.clients.openai.domain.*;
import feign.Headers;
import feign.Param;
import feign.RequestLine;

import java.io.File;

public interface OpenAIApiClient {

    @RequestLine("POST /v1/chat/completions")
    @Headers("Content-Type: application/json")
    ChatCompletionResponse createChatCompletion(ChatCompletionRequest request);

    @RequestLine("POST /v1/audio/transcriptions")
    @Headers("Content-Type: multipart/form-data")
    AudioTranscriptionResponse createAudioTranscription(@Param("file") File file);

    @RequestLine("POST /v1/embeddings")
    @Headers("Content-Type: application/json")
    EmbeddingsResponse createEmbedding(EmbeddingsRequest request);

    @RequestLine("POST /v1/images/generations")
    @Headers("Content-Type: application/json")
    CreateImageResponse createImage(CreateImageRequest request);
}