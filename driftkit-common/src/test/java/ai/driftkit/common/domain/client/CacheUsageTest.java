package ai.driftkit.common.domain.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CacheUsageTest {

    @Nested
    class HitRatio {

        @Test
        void normalRatio_calculatesCorrectly() {
            CacheUsage usage = CacheUsage.builder()
                    .cacheHitTokens(800)
                    .cacheMissTokens(200)
                    .build();

            assertEquals(0.8, usage.getHitRatio(), 0.001);
        }

        @Test
        void allHits_returns1() {
            CacheUsage usage = CacheUsage.builder()
                    .cacheHitTokens(1000)
                    .cacheMissTokens(0)
                    .build();

            assertEquals(1.0, usage.getHitRatio(), 0.001);
        }

        @Test
        void allMisses_returns0() {
            CacheUsage usage = CacheUsage.builder()
                    .cacheHitTokens(0)
                    .cacheMissTokens(1000)
                    .build();

            assertEquals(0.0, usage.getHitRatio(), 0.001);
        }

        @Test
        void bothNull_returns0() {
            CacheUsage usage = CacheUsage.builder().build();
            assertEquals(0.0, usage.getHitRatio(), 0.001);
        }

        @Test
        void hitNull_missPresent_returns0() {
            CacheUsage usage = CacheUsage.builder()
                    .cacheMissTokens(500)
                    .build();

            assertEquals(0.0, usage.getHitRatio(), 0.001);
        }

        @Test
        void hitPresent_missNull_returns1() {
            CacheUsage usage = CacheUsage.builder()
                    .cacheHitTokens(500)
                    .build();

            // hit=500, miss=0 (null→0), total=500, ratio=1.0
            assertEquals(1.0, usage.getHitRatio(), 0.001);
        }

        @Test
        void bothZero_returns0() {
            CacheUsage usage = CacheUsage.builder()
                    .cacheHitTokens(0)
                    .cacheMissTokens(0)
                    .build();

            assertEquals(0.0, usage.getHitRatio(), 0.001);
        }
    }

    @Nested
    class Builder {

        @Test
        void allFields_populated() {
            CacheUsage usage = CacheUsage.builder()
                    .cacheHitTokens(100)
                    .cacheMissTokens(200)
                    .cacheWriteTokens(50)
                    .build();

            assertEquals(100, usage.getCacheHitTokens());
            assertEquals(200, usage.getCacheMissTokens());
            assertEquals(50, usage.getCacheWriteTokens());
        }

        @Test
        void defaultFields_null() {
            CacheUsage usage = CacheUsage.builder().build();

            assertNull(usage.getCacheHitTokens());
            assertNull(usage.getCacheMissTokens());
            assertNull(usage.getCacheWriteTokens());
        }
    }
}
