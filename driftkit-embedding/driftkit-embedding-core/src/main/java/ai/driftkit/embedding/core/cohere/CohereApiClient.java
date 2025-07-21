package ai.driftkit.embedding.core.cohere;// CohereApiClient.java
import feign.Headers;
import feign.RequestLine;

public interface CohereApiClient {

    @RequestLine("POST /embed")
    @Headers("Content-Type: application/json")
    CohereEmbeddingResponse getEmbeddings(CohereEmbeddingRequest request);
}