package ai.driftkit.clients.openai.domain;

import lombok.Data;

import java.util.List;

@Data
public class EmbeddingsResponse {
    private String object;
    private List<Embedding> data;
    private Usage usage;

    @Data
    public static class Embedding {
        private String object;
        private List<Double> embedding;
        private Integer index;
    }

    @Data
    public static class Usage {
        private Integer promptTokens;
        private Integer totalTokens;
    }
}