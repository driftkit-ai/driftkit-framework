package ai.driftkit.embedding.core.cohere;// CohereEmbeddingModelUsage.java
import ai.driftkit.embedding.core.domain.Embedding;
import ai.driftkit.embedding.core.domain.TextSegment;
import ai.driftkit.embedding.core.domain.Response;
import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;

public class CohereEmbeddingModelUsage {

    public static void main(String[] args) {

        String apiKey = "YOUR_COHERE_API_KEY";

        CohereApiClient apiClient = Feign.builder()
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .requestInterceptor(new CohereAuthInterceptor(apiKey))
                .target(CohereApiClient.class, "https://api.cohere.ai");

        CohereEmbeddingModel embeddingModel = new CohereEmbeddingModel(apiClient);

        TextSegment segment = TextSegment.from("Your text here");
        Response<Embedding> response = embeddingModel.embed(segment);

        Embedding embedding = response.content();
        System.out.println("Embedding Vector: " + embedding);
    }
}