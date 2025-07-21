package ai.driftkit.common.utils;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility class for text similarity operations
 */
public class TextSimilarityUtil {

    private static final Pattern DIACRITICS_AND_FRIENDS = Pattern.compile("[\\p{InCombiningDiacriticalMarks}\\p{IsLm}\\p{IsSk}]+");
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "and", "but", "or", "for", "nor", "on", "at", "to", "from", "by", "with",
            "in", "out", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "shall", "should",
            "can", "could", "may", "might", "must", "of", "that", "which", "who", "whom", "whose",
            "this", "these", "those", "i", "you", "he", "she", "it", "we", "they",
            "their", "our", "your", "my", "his", "her", "its"
    ));

    /**
     * Normalize text for comparison:
     * - Convert to lowercase
     * - Remove diacritics (accents)
     * - Remove punctuation
     * - Remove extra whitespace
     * - Remove stop words
     *
     * @param text Input text to normalize
     * @return Normalized text
     */
    public static String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Convert to lowercase
        String result = text.toLowerCase();

        // Remove diacritical marks (accents)
        result = Normalizer.normalize(result, Normalizer.Form.NFD);
        result = DIACRITICS_AND_FRIENDS.matcher(result).replaceAll("");

        // Remove punctuation and replace with space
        result = result.replaceAll("[^\\p{Alnum}\\s]", " ");

        // Remove extra whitespace
        result = result.replaceAll("\\s+", " ").trim();

        // Remove stop words
        StringBuilder sb = new StringBuilder();
        for (String word : result.split("\\s+")) {
            if (!STOP_WORDS.contains(word)) {
                sb.append(word).append(" ");
            }
        }

        return sb.toString().trim();
    }

    /**
     * Calculate Levenshtein distance between two strings
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Levenshtein distance
     */
    public static int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                            dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1)
                    );
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * Calculate Jaccard similarity between two strings
     * (size of intersection divided by size of union)
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Jaccard similarity index (0-1)
     */
    public static double jaccardSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }

        // Get normalized strings and tokenize
        Set<String> set1 = new HashSet<>(Arrays.asList(s1.split("\\s+")));
        Set<String> set2 = new HashSet<>(Arrays.asList(s2.split("\\s+")));

        // Calculate intersection size
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        // Calculate union size
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        // Calculate Jaccard index
        return union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
    }

    /**
     * Calculate cosine similarity between two strings
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Cosine similarity (-1 to 1)
     */
    public static double cosineSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }

        // Simple implementation using word vectors
        String[] tokens1 = s1.split("\\s+");
        String[] tokens2 = s2.split("\\s+");

        Set<String> allTokens = new HashSet<>();
        for (String token : tokens1) allTokens.add(token);
        for (String token : tokens2) allTokens.add(token);

        // Create vectors based on token frequencies
        int[] vector1 = new int[allTokens.size()];
        int[] vector2 = new int[allTokens.size()];

        int i = 0;
        for (String token : allTokens) {
            vector1[i] = countOccurrences(tokens1, token);
            vector2[i] = countOccurrences(tokens2, token);
            i++;
        }

        // Calculate cosine similarity
        return calculateCosineSimilarity(vector1, vector2);
    }

    private static int countOccurrences(String[] tokens, String token) {
        int count = 0;
        for (String t : tokens) {
            if (t.equals(token)) count++;
        }
        return count;
    }

    private static double calculateCosineSimilarity(int[] vectorA, int[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }

        if (normA == 0 || normB == 0) return 0.0;

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Calculate similarity between two strings using a combined approach
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Similarity score (0-1)
     */
    public static double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }

        if (s1.equals(s2)) {
            return 1.0;
        }

        // Normalize if not already normalized
        String ns1 = s1.trim();
        String ns2 = s2.trim();

        // Calculate different similarity metrics
        double jaccardScore = jaccardSimilarity(ns1, ns2);
        double cosineScore = cosineSimilarity(ns1, ns2);

        // Calculate Levenshtein distance based similarity (1 - normalized distance)
        int maxLength = Math.max(ns1.length(), ns2.length());
        double levenshteinScore = maxLength > 0 
            ? 1.0 - ((double) levenshteinDistance(ns1, ns2) / maxLength) 
            : 1.0;

        // Combined score with different weights
        return 0.4 * jaccardScore + 0.4 * cosineScore + 0.2 * levenshteinScore;
    }
}