package ai.driftkit.common.domain;

import ai.driftkit.common.domain.client.CacheUsage;

import java.util.Map;

/**
 * Estimates USD cost for LLM API calls based on model pricing.
 * Prices are per 1M tokens. Update as providers change pricing.
 */
public class CostCalculator {

    private static final int INPUT = 0;
    private static final int OUTPUT = 1;
    private static final int CACHE_READ = 2;
    private static final int CACHE_WRITE = 3;

    /**
     * Prices per 1M tokens:
     * {@code model_prefix -> [input, output, cache_read, cache_write]}.
     * <p>
     * {@code cache_write} is charged only by providers that bill cache creation separately —
     * Claude at 1.25x the input price (5-minute TTL). Where caching is automatic and writes are
     * not billed extra (OpenAI, DeepSeek, Gemini) the write price equals the input price, so the
     * formula stays uniform and no provider branch is needed.
     * <p>
     * Verified against provider pricing pages on 2026-09-05.
     */
    private static final Map<String, double[]> MODEL_PRICING = Map.ofEntries(
            // OpenAI
            Map.entry("gpt-4o", new double[]{2.50, 10.00, 1.25, 2.50}),
            Map.entry("gpt-4o-mini", new double[]{0.15, 0.60, 0.075, 0.15}),
            Map.entry("gpt-4-turbo", new double[]{10.00, 30.00, 5.00, 10.00}),
            Map.entry("gpt-4", new double[]{30.00, 60.00, 15.00, 30.00}),
            Map.entry("gpt-3.5-turbo", new double[]{0.50, 1.50, 0.25, 0.50}),
            Map.entry("o1", new double[]{15.00, 60.00, 7.50, 15.00}),
            Map.entry("o1-mini", new double[]{3.00, 12.00, 1.50, 3.00}),
            Map.entry("o3-mini", new double[]{1.10, 4.40, 0.55, 1.10}),
            // Claude. Longest matching prefix wins, so the "-4-5".."-4-8" entries below take
            // precedence over the legacy "claude-opus-4" one, which keeps Opus 4/4.1 pricing.
            Map.entry("claude-3-5-sonnet", new double[]{3.00, 15.00, 0.30, 3.75}),
            Map.entry("claude-3-5-haiku", new double[]{0.80, 4.00, 0.08, 1.00}),
            Map.entry("claude-3-opus", new double[]{15.00, 75.00, 1.50, 18.75}),
            Map.entry("claude-3-haiku", new double[]{0.25, 1.25, 0.03, 0.31}),
            Map.entry("claude-sonnet-4", new double[]{3.00, 15.00, 0.30, 3.75}),
            Map.entry("claude-sonnet-5", new double[]{2.00, 10.00, 0.20, 2.50}),
            Map.entry("claude-haiku-4-5", new double[]{1.00, 5.00, 0.10, 1.25}),
            Map.entry("claude-opus-4", new double[]{15.00, 75.00, 1.50, 18.75}),
            Map.entry("claude-opus-4-5", new double[]{5.00, 25.00, 0.50, 6.25}),
            Map.entry("claude-opus-4-6", new double[]{5.00, 25.00, 0.50, 6.25}),
            Map.entry("claude-opus-4-7", new double[]{5.00, 25.00, 0.50, 6.25}),
            Map.entry("claude-opus-4-8", new double[]{5.00, 25.00, 0.50, 6.25}),
            Map.entry("claude-opus-5", new double[]{5.00, 25.00, 0.50, 6.25}),
            Map.entry("claude-fable-5", new double[]{10.00, 50.00, 1.00, 12.50}),
            // DeepSeek. "deepseek-chat" is a deprecated alias that the provider now resolves to
            // V4-Flash and reports back as "deepseek-v4-flash", so both keys carry V4-Flash prices.
            Map.entry("deepseek-chat", new double[]{0.14, 0.28, 0.0028, 0.14}),
            Map.entry("deepseek-v4-flash", new double[]{0.14, 0.28, 0.0028, 0.14}),
            Map.entry("deepseek-v4-pro", new double[]{0.435, 0.87, 0.003625, 0.435}),
            Map.entry("deepseek-reasoner", new double[]{0.55, 2.19, 0.14, 0.55}),
            // Gemini
            Map.entry("gemini-2.0-flash", new double[]{0.10, 0.40, 0.025, 0.10}),
            Map.entry("gemini-2.5-pro", new double[]{1.25, 10.00, 0.31, 1.25}),
            Map.entry("gemini-2.5-flash", new double[]{0.15, 0.60, 0.0375, 0.15})
    );

    /**
     * Calculate estimated cost in USD.
     */
    public static double calculate(String model, int promptTokens, int completionTokens, CacheUsage cacheUsage) {
        double[] prices = findPricing(model);
        if (prices == null) return 0.0;

        int cacheReadTokens = 0;
        int cacheWriteTokens = 0;
        Integer reportedMissTokens = null;
        if (cacheUsage != null) {
            cacheReadTokens = cacheUsage.getCacheHitTokens() != null ? cacheUsage.getCacheHitTokens() : 0;
            cacheWriteTokens = cacheUsage.getCacheWriteTokens() != null ? cacheUsage.getCacheWriteTokens() : 0;
            reportedMissTokens = cacheUsage.getCacheMissTokens();
        }

        // "promptTokens" does not mean the same thing across providers: OpenAI and DeepSeek
        // report the TOTAL prompt (cached tokens included), Claude reports only what remained
        // after the last cache breakpoint. Deriving the uncached part by subtraction is therefore
        // correct for the first two and wrong for Claude, where it double-subtracts and clamps to
        // zero — billing the uncached input of most cached calls as free.
        //
        // cacheMissTokens is the one field every client normalises to the same meaning, so it is
        // the source of truth when present; subtraction stays as the fallback for providers that
        // report a hit count without a miss count.
        int uncachedTokens = reportedMissTokens != null
                ? reportedMissTokens
                : Math.max(0, promptTokens - cacheReadTokens);

        double inputCost = (uncachedTokens / 1_000_000.0) * prices[INPUT];
        double cacheReadCost = (cacheReadTokens / 1_000_000.0) * prices[CACHE_READ];
        // Cache writes were absent from this formula entirely. For a long, stable system prompt
        // they are the largest single line — on we.today they were 2.36M tokens of 4.49M input.
        double cacheWriteCost = (cacheWriteTokens / 1_000_000.0) * prices[CACHE_WRITE];
        double outputCost = (completionTokens / 1_000_000.0) * prices[OUTPUT];

        return inputCost + cacheReadCost + cacheWriteCost + outputCost;
    }

    private static double[] findPricing(String model) {
        if (model == null) return null;
        String lower = model.toLowerCase();

        // Try exact match first
        if (MODEL_PRICING.containsKey(lower)) return MODEL_PRICING.get(lower);

        // Longest matching prefix wins, so "gpt-4o-mini-2024-07-18" resolves to "gpt-4o-mini", never to
        // "gpt-4o" or "gpt-4" (MODEL_PRICING is an unordered map; iteration order must not decide).
        String bestKey = null;
        for (String key : MODEL_PRICING.keySet()) {
            if (lower.startsWith(key) && (bestKey == null || key.length() > bestKey.length())) {
                bestKey = key;
            }
        }
        return bestKey == null ? null : MODEL_PRICING.get(bestKey);
    }
}
