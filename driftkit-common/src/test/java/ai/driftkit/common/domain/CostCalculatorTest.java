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

            // 500K/1M * 0.27 + 200K/1M * 1.10 = 0.135 + 0.22 = 0.355
            assertEquals(0.355, cost, 0.001);
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
