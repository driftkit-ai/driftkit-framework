package ai.driftkit.common.domain;

import ai.driftkit.common.domain.client.CacheUsage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CostCalculatorTest {

    @Nested
    class Calculate {

        @Test
        void gpt4o_standardCall_calculatesCorrectly() {
            // gpt-4o: $2.50/1M input, $10.00/1M output
            double cost = CostCalculator.calculate("gpt-4o", 1_000_000, 1_000_000, null);

            assertEquals(12.50, cost, 0.001);
        }

        @Test
        void gpt4oMini_smallCall_calculatesCorrectly() {
            // gpt-4o-mini: $0.15/1M input, $0.60/1M output
            double cost = CostCalculator.calculate("gpt-4o-mini", 1000, 500, null);

            // 1000/1M * 0.15 + 500/1M * 0.60 = 0.00015 + 0.0003 = 0.00045
            assertEquals(0.00045, cost, 0.0001);
        }

        @Test
        void claude35Sonnet_withCacheHits_appliesDiscount() {
            // claude-3-5-sonnet: $3.00/1M input, $15.00/1M output, $0.30/1M cached
            CacheUsage cache = CacheUsage.builder()
                    .cacheHitTokens(800_000)
                    .cacheMissTokens(200_000)
                    .build();

            double cost = CostCalculator.calculate("claude-3-5-sonnet", 1_000_000, 100_000, cache);

            // uncached: 200K/1M * 3.00 = 0.60
            // cached: 800K/1M * 0.30 = 0.24
            // output: 100K/1M * 15.00 = 1.50
            // total: 2.34
            assertEquals(2.34, cost, 0.01);
        }

        @Test
        void deepseekChat_calculatesCorrectly() {
            double cost = CostCalculator.calculate("deepseek-chat", 500_000, 200_000, null);

            // Repriced 2026-09-05: "deepseek-chat" is a deprecated alias the provider now serves
            // with V4-Flash ($0.14 / $0.28), not the old $0.27 / $1.10.
            // 500K/1M * 0.14 + 200K/1M * 0.28 = 0.07 + 0.056 = 0.126
            assertEquals(0.126, cost, 0.001);
        }

        @Test
        void deepseekV4Flash_isRecognised() {
            // Regression: the model the provider actually reports back was absent from the table,
            // so findPricing returned null and every DeepSeek call was costed at zero.
            double cost = CostCalculator.calculate("deepseek-v4-flash", 500_000, 200_000, null);

            assertTrue(cost > 0, "deepseek-v4-flash must be priced, not silently zero");
            assertEquals(0.126, cost, 0.001);
        }

        @Test
        void zeroTokens_returnsZero() {
            double cost = CostCalculator.calculate("gpt-4o", 0, 0, null);
            assertEquals(0.0, cost, 0.0001);
        }

        @Test
        void allCached_onlyChargesCachedRate() {
            CacheUsage cache = CacheUsage.builder()
                    .cacheHitTokens(1_000_000)
                    .build();

            double cost = CostCalculator.calculate("gpt-4o", 1_000_000, 0, cache);

            // All cached: 1M/1M * 1.25 (cached rate) + 0 uncached + 0 output = 1.25
            assertEquals(1.25, cost, 0.001);
        }

        @ParameterizedTest
        @CsvSource({
                "gpt-4o-2024-08-06, true",
                "gpt-4o-mini-2024-07-18, true",
                "claude-3-5-sonnet-20241022, true",
                "claude-opus-4-20250514, true",
                "gemini-2.0-flash-001, true"
        })
        void prefixMatching_matchesModelVariants(String model, boolean shouldMatch) {
            double cost = CostCalculator.calculate(model, 1000, 1000, null);
            if (shouldMatch) {
                assertTrue(cost > 0, "Model " + model + " should be recognized via prefix matching");
            }
        }
    }

    /**
     * Golden vectors taken from real we.today production traces (2026-09-05). Each test here
     * fails against the pre-2026-09-06 implementation and documents one of the three independent
     * reasons costs were understated by 3.4x.
     */
    @Nested
    class ClaudeCacheAccounting {

        /** Averages over 471 production Claude calls: uncached 1216, read 3295, write 5015, out 433. */
        private CacheUsage productionAverages() {
            return CacheUsage.builder()
                    .cacheHitTokens(3295)
                    .cacheWriteTokens(5015)
                    .cacheMissTokens(1216)   // Claude reports this as usage.input_tokens
                    .build();
        }

        @Test
        void cacheWrites_areBilled() {
            // Cache creation was missing from the formula entirely. It is the largest line:
            // 5015 * 3.75/1M = 0.01881 of a 0.02994 total.
            double cost = CostCalculator.calculate(
                    "claude-sonnet-4-5-20250929", 1216, 433, productionAverages());

            // uncached 1216 * 3.00 = 0.003648
            // read      3295 * 0.30 = 0.0009885
            // write     5015 * 3.75 = 0.01880625
            // output     433 * 15.00 = 0.006495
            assertEquals(0.02994, cost, 0.0002);
        }

        @Test
        void uncachedInput_isNotZeroedByCacheHits() {
            // Claude's promptTokens already excludes cache reads. The old code subtracted them
            // again, went negative and clamped to zero, so uncached input was billed as free.
            CacheUsage cache = productionAverages();

            double withCache = CostCalculator.calculate(
                    "claude-sonnet-4-5-20250929", 1216, 0, cache);
            double uncachedOnly = CostCalculator.calculate(
                    "claude-sonnet-4-5-20250929", 1216, 0, null);

            assertTrue(withCache > uncachedOnly,
                    "cached call must cost more than its uncached input alone — it also pays for "
                            + "the cache write");
            assertTrue(withCache > 0.0036,
                    "uncached input (1216 tokens) must still be charged, not clamped to zero");
        }

        @Test
        void openAiSemantics_areUnchanged() {
            // Guard for the fix itself: OpenAI reports promptTokens as the TOTAL including cached
            // tokens, so its uncached part must still come out as total - cached.
            CacheUsage cache = CacheUsage.builder()
                    .cacheHitTokens(800_000)
                    .cacheMissTokens(200_000)
                    .build();

            double cost = CostCalculator.calculate("gpt-4o", 1_000_000, 0, cache);

            // uncached 200K * 2.50 = 0.50 ; cached 800K * 1.25 = 1.00
            assertEquals(1.50, cost, 0.001);
        }
    }

    @Nested
    class UnknownModels {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"unknown-model", "llama-3-70b", "mistral-large"})
        void unknownModel_returnsZero(String model) {
            double cost = CostCalculator.calculate(model, 1_000_000, 1_000_000, null);
            assertEquals(0.0, cost, 0.0001);
        }
    }

    @Nested
    class CacheInteraction {

        @Test
        void cacheUsageWithNullHitTokens_treatedAsNoCaching() {
            CacheUsage cache = CacheUsage.builder().build(); // all nulls

            double withCache = CostCalculator.calculate("gpt-4o", 1000, 500, cache);
            double withoutCache = CostCalculator.calculate("gpt-4o", 1000, 500, null);

            assertEquals(withoutCache, withCache, 0.0001);
        }

        @Test
        void cacheHitsExceedPromptTokens_clampedToZeroUncached() {
            CacheUsage cache = CacheUsage.builder()
                    .cacheHitTokens(2000)
                    .build();

            // 1000 prompt tokens but 2000 cached — uncached should be max(0, 1000-2000) = 0
            double cost = CostCalculator.calculate("gpt-4o", 1000, 0, cache);

            assertTrue(cost >= 0, "Cost should never be negative");
        }
    }
}
