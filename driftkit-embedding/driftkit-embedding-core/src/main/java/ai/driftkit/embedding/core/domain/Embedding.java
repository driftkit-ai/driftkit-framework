package ai.driftkit.embedding.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Data
@AllArgsConstructor
public class Embedding {

    private final float[] vector;

    public static Embedding from(double[] embeddingArray) {
        return new Embedding(toFloatArray(embeddingArray));
    }

    public float[] vector() {
        return vector;
    }

    public List<Float> vectorAsList() {
        List<Float> list = new ArrayList<>(vector.length);
        for (float f : vector) {
            list.add(f);
        }
        return list;
    }

    /**
     * Normalize vector
     */
    public void normalize() {
        double norm = 0.0;
        for (float f : vector) {
            norm += f * f;
        }
        norm = Math.sqrt(norm);

        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }

    public int dimension() {
        return vector.length;
    }

    public static Embedding from(float[] vector) {
        return new Embedding(vector);
    }

    public static Embedding from(List<Float> vector) {
        float[] array = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            array[i] = vector.get(i);
        }
        return new Embedding(array);
    }

    public static float[] toFloatArray(double[] src) {
        float[] dst = new float[src.length];
        for (int i = 0; i < src.length; i++) {
            dst[i] = (float) src[i];
        }
        return dst;
    }
}
