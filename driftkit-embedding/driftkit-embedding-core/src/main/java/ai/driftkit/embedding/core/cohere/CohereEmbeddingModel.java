package ai.driftkit.embedding.core.cohere;

import ai.driftkit.config.EtlConfig.EmbeddingServiceConfig;
import ai.driftkit.embedding.core.domain.Embedding;
import ai.driftkit.embedding.core.domain.TextSegment;
import ai.driftkit.embedding.core.local.AIOnnxBertBiEncoder;
import ai.driftkit.embedding.core.service.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;

public class CohereEmbeddingModel implements EmbeddingModel {

    private final CohereApiClient apiClient;

    public CohereEmbeddingModel(CohereApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public boolean supportsName(String name) {
        return "cohere".equals(name);
    }

    @Override
    public AIOnnxBertBiEncoder model() {
        throw new UnsupportedOperationException("Not supported in CohereEmbeddingModel");
    }

    @Override
    public void configure(EmbeddingServiceConfig config) {

    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        List<String> texts = new ArrayList<>();
        for (TextSegment segment : segments) {
            texts.add(segment.text());
        }

        CohereEmbeddingRequest request = new CohereEmbeddingRequest();
        request.setTexts(texts);

        CohereEmbeddingResponse response = apiClient.getEmbeddings(request);

        List<Embedding> embeddings = new ArrayList<>();
        for (List<Double> embeddingValues : response.getEmbeddings()) {
            double[] embeddingArray = embeddingValues.stream().mapToDouble(Double::doubleValue).toArray();
            embeddings.add(Embedding.from(embeddingArray));
        }

        // Cohere API may not provide token usage; set to zero or estimate if possible
        return Response.from(embeddings, new TokenUsage(0));
    }

    @Override
    public int estimateTokenCount(String text) {
        // Implement token estimation logic or return an approximate value
        return text.length() / 5; // Approximate estimation
    }

    public Response<Embedding> embed(TextSegment segment) {
        List<TextSegment> segments = new ArrayList<>();
        segments.add(segment);
        Response<List<Embedding>> responseList = embedAll(segments);
        Embedding embedding = responseList.content().get(0);
        TokenUsage tokenUsage = responseList.tokenUsage();

        return Response.from(embedding, tokenUsage);
    }
}