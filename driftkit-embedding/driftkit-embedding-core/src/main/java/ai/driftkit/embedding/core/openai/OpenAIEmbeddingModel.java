package ai.driftkit.embedding.core.openai;

import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.EmbeddingServiceConfig;
import ai.driftkit.embedding.core.service.EmbeddingModel;
import ai.driftkit.embedding.core.domain.*;
import ai.driftkit.embedding.core.local.AIOnnxBertBiEncoder;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class OpenAIEmbeddingModel implements EmbeddingModel {

    private EmbeddingOpenAIApiClient apiClient;
    private String modelName;

    public OpenAIEmbeddingModel(EmbeddingOpenAIApiClient apiClient, String modelName) {
        this.apiClient = apiClient;
        this.modelName = modelName;
    }

    @Override
    public boolean supportsName(String name) {
        return "openai".equals(name);
    }

    @Override
    public AIOnnxBertBiEncoder model() {
        throw new UnsupportedOperationException("Not supported in OpenAIEmbeddingModel");
    }

    @Override
    public void configure(EmbeddingServiceConfig config) {
        this.modelName = config.getConfig().get(EtlConfig.MODEL_NAME);
        this.apiClient = Feign.builder()
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .requestInterceptor(new OpenAIAuthInterceptor(config.get(EtlConfig.API_KEY)))
                .target(EmbeddingOpenAIApiClient.class, config.get(EtlConfig.HOST, "https://api.openai.com"));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        List<String> texts = new ArrayList<>();
        for (TextSegment segment : segments) {
            texts.add(segment.text());
        }

        EmbeddingRequest request = new EmbeddingRequest();
        request.setModel(modelName);
        request.setInput(texts);

        EmbeddingResponse response = apiClient.getEmbeddings(request);

        List<Embedding> embeddings = new ArrayList<>();
        for (EmbeddingData data : response.getData()) {
            float[] embeddingValues = data.getEmbedding();
            embeddings.add(Embedding.from(embeddingValues));
        }

        int inputTokenCount = response.getUsage().getTotalTokens();

        return Response.from(embeddings, new TokenUsage(inputTokenCount));
    }

    @Override
    public int estimateTokenCount(String text) {
        // OpenAI provides approximate token counts
        // For more accurate counts, integrate with a tokenizer
        return text.length() / 4; // Approximate estimation
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