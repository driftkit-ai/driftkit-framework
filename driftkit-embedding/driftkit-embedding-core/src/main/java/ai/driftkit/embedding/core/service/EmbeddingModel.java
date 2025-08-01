package ai.driftkit.embedding.core.service;

import ai.driftkit.config.EtlConfig.EmbeddingServiceConfig;
import ai.driftkit.embedding.core.domain.Embedding;
import ai.driftkit.embedding.core.domain.TextSegment;
import ai.driftkit.embedding.core.local.AIOnnxBertBiEncoder;
import ai.driftkit.embedding.core.local.AIOnnxBertBiEncoder.EmbeddingAndTokenCount;
import ai.driftkit.embedding.core.domain.Response;
import ai.driftkit.embedding.core.domain.TokenUsage;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

public interface EmbeddingModel {

    boolean supportsName(String name);

    AIOnnxBertBiEncoder model();

    void configure(EmbeddingServiceConfig config);

    default Response<Embedding> embed(TextSegment segment) {
        Response<List<Embedding>> resp = embedAll(List.of(segment));
        List<Embedding> content = resp.content();

        if (CollectionUtils.isEmpty(content)) {
            return null;
        }

        return Response.from(content.get(0), resp.tokenUsage(), resp.finishReason());
    }

    default Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        int inputTokenCount = 0;

        List<Embedding> embeddings = new ArrayList<>();
        for (TextSegment segment : segments) {
            EmbeddingAndTokenCount embeddingAndTokenCount = model().embed(segment.text());
            embeddings.add(Embedding.from(embeddingAndTokenCount.embedding()));
            inputTokenCount += embeddingAndTokenCount.tokenCount();
        }

        return Response.from(embeddings, new TokenUsage(inputTokenCount));
    }

    default int estimateTokenCount(String text) {
        return model().countTokens(text);
    }
}
