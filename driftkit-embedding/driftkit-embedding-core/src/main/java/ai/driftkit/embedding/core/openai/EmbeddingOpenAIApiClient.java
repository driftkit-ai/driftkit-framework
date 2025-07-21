package ai.driftkit.embedding.core.openai;// OpenAIApiClient.java
import ai.driftkit.embedding.core.domain.EmbeddingRequest;
import ai.driftkit.embedding.core.domain.EmbeddingResponse;
import feign.Headers;
import feign.RequestLine;

public interface EmbeddingOpenAIApiClient {

    @RequestLine("POST /v1/embeddings")
    @Headers("Content-Type: application/json")
    EmbeddingResponse getEmbeddings(EmbeddingRequest request);
}