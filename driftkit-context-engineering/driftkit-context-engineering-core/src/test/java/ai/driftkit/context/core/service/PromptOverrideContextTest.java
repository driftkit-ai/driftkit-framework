package ai.driftkit.context.core.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PromptOverrideContextTest {

    @AfterEach
    void cleanup() {
        PromptOverrideContext.clear();
    }

    @Nested
    class SetAndGet {

        @Test
        void set_singleOverride_retrievable() {
            PromptOverrideContext.set(Map.of("classifier", "override text"));

            assertEquals("override text", PromptOverrideContext.getOverride("classifier"));
        }

        @Test
        void set_multipleOverrides_allRetrievable() {
            PromptOverrideContext.set(Map.of(
                    "classifier", "text1",
                    "extractor", "text2"
            ));

            assertEquals("text1", PromptOverrideContext.getOverride("classifier"));
            assertEquals("text2", PromptOverrideContext.getOverride("extractor"));
        }

        @Test
        void getOverride_nonExistentKey_returnsNull() {
            PromptOverrideContext.set(Map.of("classifier", "text"));

            assertNull(PromptOverrideContext.getOverride("unknown"));
        }

        @Test
        void getOverride_noOverridesSet_returnsNull() {
            assertNull(PromptOverrideContext.getOverride("anything"));
        }
    }

    @Nested
    class IsActive {

        @Test
        void noOverrides_returnsFalse() {
            assertFalse(PromptOverrideContext.isActive());
        }

        @Test
        void withOverrides_returnsTrue() {
            PromptOverrideContext.set(Map.of("a", "b"));
            assertTrue(PromptOverrideContext.isActive());
        }

        @Test
        void emptyMap_returnsFalse() {
            PromptOverrideContext.set(Map.of());
            assertFalse(PromptOverrideContext.isActive());
        }

        @Test
        void nullMap_returnsFalse() {
            PromptOverrideContext.set(null);
            assertFalse(PromptOverrideContext.isActive());
        }
    }

    @Nested
    class Clear {

        @Test
        void clear_removesOverrides() {
            PromptOverrideContext.set(Map.of("a", "b"));
            assertTrue(PromptOverrideContext.isActive());

            PromptOverrideContext.clear();

            assertFalse(PromptOverrideContext.isActive());
            assertNull(PromptOverrideContext.getOverride("a"));
        }
    }

    @Nested
    class ThreadIsolation {

        @Test
        void overridesAreIsolatedPerThread() throws Exception {
            PromptOverrideContext.set(Map.of("main", "main-value"));

            AtomicReference<String> childResult = new AtomicReference<>();
            AtomicReference<String> childMainResult = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            Thread child = new Thread(() -> {
                PromptOverrideContext.set(Map.of("child", "child-value"));
                childResult.set(PromptOverrideContext.getOverride("child"));
                childMainResult.set(PromptOverrideContext.getOverride("main"));
                PromptOverrideContext.clear();
                latch.countDown();
            });
            child.start();
            latch.await();

            // Child thread had its own context
            assertEquals("child-value", childResult.get());
            assertNull(childMainResult.get()); // main thread's overrides not visible

            // Main thread still has its overrides
            assertEquals("main-value", PromptOverrideContext.getOverride("main"));
            assertNull(PromptOverrideContext.getOverride("child")); // child's not visible
        }
    }
}
