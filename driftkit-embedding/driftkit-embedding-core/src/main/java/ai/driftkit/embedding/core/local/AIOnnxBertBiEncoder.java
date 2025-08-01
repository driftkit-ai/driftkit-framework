package ai.driftkit.embedding.core.local;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import ai.driftkit.embedding.core.domain.PoolingMode;
import ai.driftkit.embedding.core.util.DriftKitExceptions;
import ai.driftkit.embedding.core.util.ValidationUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class AIOnnxBertBiEncoder {

    private static final int MAX_SEQUENCE_LENGTH = 510; // 512 - 2 (special tokens [CLS] and [SEP])
    public static final String CLS = "[CLS]";
    public static final String SEP = "[SEP]";

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final Set<String> expectedInputs;
    private final HuggingFaceTokenizer tokenizer;
    private final PoolingMode poolingMode;
    private final boolean addCls;

    public AIOnnxBertBiEncoder(String modelPath, String tokenizerPath, PoolingMode poolingMode, boolean addCls) {
        this.addCls = addCls;
        try {
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(modelPath);
            this.expectedInputs = session.getInputNames();

            this.tokenizer = HuggingFaceTokenizer.builder().optTokenizerPath(Path.of(tokenizerPath))
                    .optPadding(false)
                    .optAddSpecialTokens(addCls)
                    //.optWithOverflowingTokens(true)
                    .optTruncation(true)
                    .build();

            this.poolingMode = ValidationUtils.ensureNotNull(poolingMode, "poolingMode");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public EmbeddingAndTokenCount embed(String text) {

        List<String> tokens = tokenizer.tokenize(text);
        List<List<String>> partitions = partition(tokens, MAX_SEQUENCE_LENGTH);

        List<float[]> embeddings = new ArrayList<>();
        for (List<String> partition : partitions) {
            try (Result result = encode(partition)) {
                float[] embedding = toEmbedding(result);
                embeddings.add(embedding);
            } catch (OrtException e) {
                throw new RuntimeException(e);
            }
        }

        List<Integer> weights = partitions.stream()
                .map(List::size)
                .collect(Collectors.toList());

        float[] embedding = normalize(weightedAverage(embeddings, weights));

        return new EmbeddingAndTokenCount(embedding, tokens.size());
    }

    private List<List<String>> partition(List<String> tokens, int partitionSize) {
        List<List<String>> partitions = new ArrayList<>();

        int startIdx = 0;
        int lastIdx = 0;

        if (tokens.size() > 2 && tokens.get(0).equals(CLS) && tokens.get(tokens.size() - 1).equals(SEP)) {
            startIdx = 1;
            lastIdx = -1;
        }

        for (int from = startIdx; from < tokens.size() - 1; from += partitionSize) {
            int to = Math.min(tokens.size() + lastIdx, from + partitionSize);
            List<String> partition = new ArrayList<>(tokens.subList(from, to));

            if (addCls && partition.size() < 2 || !partition.get(0).equals("CLS") || !partition.get(tokens.size() - 1).equals("SEP")) {
                partition.addFirst("CLS");
                partition.addLast("SEP");
            }

            partitions.add(partition);
        }
        return partitions;
    }

    private Result encode(List<String> tokens) throws OrtException {

        Encoding encoding = tokenizer.encode(toText(tokens), true, false);

        long[] inputIds = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();
        long[] tokenTypeIds = encoding.getTypeIds();

        long[] shape = {1, inputIds.length};

        try (
                OnnxTensor inputIdsTensor = OnnxTensor.createTensor(environment, LongBuffer.wrap(inputIds), shape);
                OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(environment, LongBuffer.wrap(attentionMask), shape);
                OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(environment, LongBuffer.wrap(tokenTypeIds), shape)
        ) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);

            if (expectedInputs.contains("token_type_ids")) {
                inputs.put("token_type_ids", tokenTypeIdsTensor);
            }

            return session.run(inputs);
        }
    }

    private String toText(List<String> tokens) {
        String text = tokenizer.buildSentence(tokens);

        List<String> tokenized = tokenizer.tokenize(text);
        List<String> tokenizedWithoutSpecialTokens = new LinkedList<>(tokenized);

        if (addCls) {
            tokenizedWithoutSpecialTokens.remove(0);
            tokenizedWithoutSpecialTokens.remove(tokenizedWithoutSpecialTokens.size() - 1);
        }

        if (tokenizedWithoutSpecialTokens.equals(tokens)) {
            return text;
        } else {
            return String.join("", tokens);
        }
    }

    private float[] toEmbedding(Result result) throws OrtException {
        float[][] vectors = ((float[][][]) result.get(0).getValue())[0];
        return pool(vectors);
    }

    private float[] pool(float[][] vectors) {
        switch (poolingMode) {
            case CLS:
                return clsPool(vectors);
            case MEAN:
                return meanPool(vectors);
            default:
                throw DriftKitExceptions.illegalArgument("Unknown pooling mode: " + poolingMode);
        }
    }

    private static float[] clsPool(float[][] vectors) {
        return vectors[0];
    }

    private static float[] meanPool(float[][] vectors) {

        int numVectors = vectors.length;
        int vectorLength = vectors[0].length;

        float[] averagedVector = new float[vectorLength];

        for (float[] vector : vectors) {
            for (int j = 0; j < vectorLength; j++) {
                averagedVector[j] += vector[j];
            }
        }

        for (int j = 0; j < vectorLength; j++) {
            averagedVector[j] /= numVectors;
        }

        return averagedVector;
    }

    private float[] weightedAverage(List<float[]> embeddings, List<Integer> weights) {
        if (embeddings.size() == 1) {
            return embeddings.get(0);
        }

        int dimensions = embeddings.get(0).length;

        float[] averagedEmbedding = new float[dimensions];
        int totalWeight = 0;

        for (int i = 0; i < embeddings.size(); i++) {
            int weight = weights.get(i);
            totalWeight += weight;

            for (int j = 0; j < dimensions; j++) {
                averagedEmbedding[j] += embeddings.get(i)[j] * weight;
            }
        }

        for (int j = 0; j < dimensions; j++) {
            averagedEmbedding[j] /= totalWeight;
        }

        return averagedEmbedding;
    }

    private static float[] normalize(float[] vector) {

        float sumSquare = 0;
        for (float v : vector) {
            sumSquare += v * v;
        }
        float norm = (float) Math.sqrt(sumSquare);

        float[] normalizedVector = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalizedVector[i] = vector[i] / norm;
        }

        return normalizedVector;
    }

    public int countTokens(String text) {
        return tokenizer.tokenize(text).size();
    }

    @Getter
    @Accessors(fluent = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbeddingAndTokenCount {
        float[] embedding;
        int tokenCount;
    }
}
