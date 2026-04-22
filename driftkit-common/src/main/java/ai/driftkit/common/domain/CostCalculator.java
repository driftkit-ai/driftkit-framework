package ai.driftkit.common.domain;

import ai.driftkit.common.domain.client.CacheUsage;

import java.util.Map;

/**
 * Estimates USD cost for LLM API calls based on model pricing.
 * Prices are per 1M tokens. Update as providers change pricing.
 */
public class CostCalculator {

    // Prices per 1M tokens: { model_prefix -> [input_price, output_price, cached_input_price] }
    private static final Map<String, double[]> MODEL_PRICING = Map.ofEntries(
            // OpenAI
            Map.entry("gpt-4o", new double[]{2.50, 10.00, 1.25}),
            Map.entry("gpt-4o-mini", new double[]{0.15, 0.60, 0.075}),
            Map.entry("gpt-4-turbo", new double[]{10.00, 30.00, 5.00}),
            Map.entry("gpt-4", new double[]{30.00, 60.00, 15.00}),
            Map.entry("gpt-3.5-turbo", new double[]{0.50, 1.50, 0.25}),
            Map.entry("o1", new double[]{15.00, 60.00, 7.50}),
            Map.entry("o1-mini", new double[]{3.00, 12.00, 1.50}),
            Map.entry("o3-mini", new double[]{1.10, 4.40, 0.55}),
            // Claude
            Map.entry("claude-3-5-sonnet", new double[]{3.00, 15.00, 0.30}),
            Map.entry("claude-3-5-haiku", new double[]{0.80, 4.00, 0.08}),
            Map.entry("claude-sonnet-4", new double[]{3.00, 15.00, 0.30}),
            Map.entry("claude-opus-4", new double[]{15.00, 75.00, 1.50}),
            Map.entry("claude-3-opus", new double[]{15.00, 75.00, 1.50}),
            Map.entry("claude-3-haiku", new double[]{0.25, 1.25, 0.03}),
            // DeepSeek
            Map.entry("deepseek-chat", new double[]{0.27, 1.10, 0.07}),
            Map.entry("deepseek-reasoner", new double[]{0.55, 2.19, 0.14}),
            // Gemini
            Map.entry("gemini-2.0-flash", new double[]{0.10, 0.40, 0.025}),
            Map.entry("gemini-2.5-pro", new double[]{1.25, 10.00, 0.31}),
            Map.entry("gemini-2.5-flash", new double[]{0.15, 0.60, 0.0375})
    );

    /**
     * Calculate estimated cost in USD.
     */
    public static double calculate(String model, int promptTokens, int completionTokens, CacheUsage cacheUsage) {
        double[] prices = findPricing(model);
        if (prices == null) return 0.0;

        double inputPrice = prices[0];
        double outputPrice = prices[1];
        double cachedPrice = prices[2];

        int cachedTokens = 0;
        int uncachedPromptTokens = promptTokens;

        if (cacheUsage != null && cacheUsage.getCacheHitTokens() != null) {
            cachedTokens = cacheUsage.getCacheHitTokens();
            uncachedPromptTokens = Math.max(0, promptTokens - cachedTokens);
        }

        double inputCost = (uncachedPromptTokens / 1_000_000.0) * inputPrice;
        double cachedCost = (cachedTokens / 1_000_000.0) * cachedPrice;
        double outputCost = (completionTokens / 1_000_000.0) * outputPrice;

        return inputCost + cachedCost + outputCost;
    }

    private static double[] findPricing(String model) {
        if (model == null) return null;
        String lower = model.toLowerCase();

        // Try exact match first
        if (MODEL_PRICING.containsKey(lower)) return MODEL_PRICING.get(lower);

        // Try prefix match
        for (Map.Entry<String, double[]> entry : MODEL_PRICING.entrySet()) {
            if (lower.startsWith(entry.getKey())) return entry.getValue();
        }

        return null;
    }
}
