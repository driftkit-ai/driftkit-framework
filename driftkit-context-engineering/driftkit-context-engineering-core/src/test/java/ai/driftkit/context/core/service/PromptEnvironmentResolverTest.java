package ai.driftkit.context.core.service;

import ai.driftkit.common.domain.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptEnvironmentResolverTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        PromptEnvironmentResolver.clear();
        PromptEnvironmentResolver.setResolver(null);
    }

    @Nested
    class ResolveVersion {

        @Test
        void noEnvironmentSet_returnsNull() {
            PromptEnvironmentResolver.setResolver((method, lang, env) -> 5);

            assertNull(PromptEnvironmentResolver.resolveVersion("test", Language.GENERAL));
        }

        @Test
        void noResolverSet_returnsNull() {
            PromptEnvironmentResolver.setEnvironment("staging");

            assertNull(PromptEnvironmentResolver.resolveVersion("test", Language.GENERAL));
        }

        @Test
        void bothSet_delegatesToResolver() {
            PromptEnvironmentResolver.setResolver((method, lang, env) -> {
                if ("staging".equals(env) && "report".equals(method)) return 3;
                return null;
            });
            PromptEnvironmentResolver.setEnvironment("staging");

            assertEquals(3, PromptEnvironmentResolver.resolveVersion("report", Language.GENERAL));
        }

        @Test
        void resolverReturnsNull_propagatesNull() {
            PromptEnvironmentResolver.setResolver((method, lang, env) -> null);
            PromptEnvironmentResolver.setEnvironment("production");

            assertNull(PromptEnvironmentResolver.resolveVersion("unknown-method", Language.GENERAL));
        }

        @Test
        void differentEnvironments_resolveDifferently() {
            PromptEnvironmentResolver.setResolver((method, lang, env) -> switch (env) {
                case "dev" -> 5;
                case "staging" -> 3;
                case "production" -> 1;
                default -> null;
            });

            PromptEnvironmentResolver.setEnvironment("dev");
            assertEquals(5, PromptEnvironmentResolver.resolveVersion("test", Language.GENERAL));

            PromptEnvironmentResolver.setEnvironment("staging");
            assertEquals(3, PromptEnvironmentResolver.resolveVersion("test", Language.GENERAL));

            PromptEnvironmentResolver.setEnvironment("production");
            assertEquals(1, PromptEnvironmentResolver.resolveVersion("test", Language.GENERAL));
        }

        @Test
        void languagePassedToResolver() {
            PromptEnvironmentResolver.setResolver((method, lang, env) -> {
                if (lang == Language.ENGLISH) return 10;
                if (lang == Language.SPANISH) return 20;
                return null;
            });
            PromptEnvironmentResolver.setEnvironment("staging");

            assertEquals(10, PromptEnvironmentResolver.resolveVersion("test", Language.ENGLISH));
            assertEquals(20, PromptEnvironmentResolver.resolveVersion("test", Language.SPANISH));
        }
    }

    @Nested
    class EnvironmentState {

        @Test
        void getEnvironment_notSet_returnsNull() {
            assertNull(PromptEnvironmentResolver.getEnvironment());
        }

        @Test
        void setAndGet_returnsValue() {
            PromptEnvironmentResolver.setEnvironment("staging");
            assertEquals("staging", PromptEnvironmentResolver.getEnvironment());
        }

        @Test
        void clear_removesEnvironment() {
            PromptEnvironmentResolver.setEnvironment("staging");
            PromptEnvironmentResolver.clear();

            assertNull(PromptEnvironmentResolver.getEnvironment());
        }
    }
}
