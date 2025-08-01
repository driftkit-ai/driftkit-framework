package ai.driftkit.embedding.core.local;


import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.EmbeddingServiceConfig;
import ai.driftkit.embedding.core.service.EmbeddingModel;
import ai.driftkit.embedding.core.domain.Embedding;
import ai.driftkit.embedding.core.domain.TextSegment;
import ai.driftkit.embedding.core.domain.PoolingMode;
import ai.driftkit.embedding.core.domain.Response;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

@NoArgsConstructor
public class BertGenericEmbeddingModel implements EmbeddingModel {
    @Setter
    private AIOnnxBertBiEncoder encoder;

    @Override
    public boolean supportsName(String name) {
        return "local".equals(name);
    }

    @Override
    public AIOnnxBertBiEncoder model() {
        return encoder;
    }

    @Override
    public void configure(EmbeddingServiceConfig config) {
        this.encoder = new AIOnnxBertBiEncoder(
            config.get(EtlConfig.MODEL_PATH),
            config.get(EtlConfig.TOKENIZER_PATH),
            PoolingMode.MEAN,
            false
        );
    }

    public Response<Embedding> embed(TextSegment segment) {
        List<Embedding> content = embedAll(Arrays.asList(segment)).content();
        return Response.from(content.get(0));
    }
}