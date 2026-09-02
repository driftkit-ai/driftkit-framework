package ai.driftkit.common.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prefix resolution must be deterministic: the longest listed prefix wins, regardless of the
 * iteration order of the pricing map.
 */
class CostCalculatorPrefixTest {

    private static double cost(String model) {
        return CostCalculator.calculate(model, 1_000_000, 1_000_000, null);
    }

    @Test
    void datedVariantResolvesToTheMostSpecificPrefix() {
        assertEquals(cost("gpt-4o-mini"), cost("gpt-4o-mini-2024-07-18"), 1e-9);
        assertEquals(cost("gpt-4o"), cost("gpt-4o-2024-11-20"), 1e-9);
        assertEquals(cost("claude-sonnet-4"), cost("claude-sonnet-4-5-20250929"), 1e-9);
        assertEquals(cost("gemini-2.5-flash"), cost("gemini-2.5-flash-lite"), 1e-9);
    }

    @Test
    void prefixResolutionIsStableAcrossCalls() {
        double first = cost("gpt-4o-mini-2024-07-18");
        for (int i = 0; i < 50; i++) {
            assertEquals(first, cost("gpt-4o-mini-2024-07-18"), 1e-9);
        }
        assertTrue(cost("gpt-4o-mini") < cost("gpt-4o"), "sanity: mini must be cheaper than gpt-4o");
    }

    @Test
    void unknownFamilyCostsNothing() {
        assertEquals(0.0, cost("totally-unknown-model"), 1e-9);
    }
}
