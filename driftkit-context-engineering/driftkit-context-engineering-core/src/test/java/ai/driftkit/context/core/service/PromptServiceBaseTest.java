package ai.driftkit.context.core.service;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.Prompt.State;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the fallback chain in getCurrentPromptOrThrow:
 * 1. PromptOverrideContext (pipeline testing)
 * 2. PromptEnvironmentResolver (environment-specific version)
 * 3. CURRENT state from storage (default)
 */
class PromptServiceBaseTest {

    private TestPromptService service;

    @BeforeEach
    void setUp() {
        service = new TestPromptService();
        PromptOverrideContext.clear();
        PromptEnvironmentResolver.clear();
        PromptEnvironmentResolver.setResolver(null);
    }

    @AfterEach
    void cleanup() {
        PromptOverrideContext.clear();
        PromptEnvironmentResolver.clear();
        PromptEnvironmentResolver.setResolver(null);
    }

    @Nested
    class FallbackChain {

        @Test
        void noOverrideNoEnvironment_returnsCurrent() {
            Prompt current = makePrompt("method1", "current text", 3, State.CURRENT);
            service.addPrompt(current);

            Prompt result = service.getCurrentPromptOrThrow("method1", Language.GENERAL);

            assertEquals("current text", result.getMessage());
            assertEquals(3, result.getVersion());
        }

        @Test
        void overrideActive_returnsOverrideIgnoringStorage() {
            Prompt current = makePrompt("method1", "stored text", 3, State.CURRENT);
            service.addPrompt(current);

            PromptOverrideContext.set(Map.of("method1", "override text"));

            Prompt result = service.getCurrentPromptOrThrow("method1", Language.GENERAL);

            assertEquals("override text", result.getMessage());
            assertEquals("method1", result.getMethod());
        }

        @Test
        void environmentSet_returnsEnvironmentVersion() {
            Prompt v2 = makePrompt("method1", "v2 text", 2, State.REPLACED);
            Prompt v3 = makePrompt("method1", "v3 text", 3, State.CURRENT);
            service.addPrompt(v2);
            service.addPrompt(v3);

            PromptEnvironmentResolver.setResolver((method, lang, env) ->
                "staging".equals(env) ? 2 : null);
            PromptEnvironmentResolver.setEnvironment("staging");

            Prompt result = service.getCurrentPromptOrThrow("method1", Language.GENERAL);

            assertEquals("v2 text", result.getMessage());
            assertEquals(2, result.getVersion());
        }

        @Test
        void overrideTakesPrecedenceOverEnvironment() {
            Prompt v2 = makePrompt("method1", "v2 text", 2, State.REPLACED);
            service.addPrompt(v2);

            PromptOverrideContext.set(Map.of("method1", "override wins"));
            PromptEnvironmentResolver.setResolver((m, l, e) -> 2);
            PromptEnvironmentResolver.setEnvironment("staging");

            Prompt result = service.getCurrentPromptOrThrow("method1", Language.GENERAL);

            assertEquals("override wins", result.getMessage());
        }

        @Test
        void environmentVersionNotFound_fallsThroughToCurrent() {
            Prompt current = makePrompt("method1", "current text", 3, State.CURRENT);
            service.addPrompt(current);

            PromptEnvironmentResolver.setResolver((m, l, e) -> 99); // version 99 doesn't exist
            PromptEnvironmentResolver.setEnvironment("staging");

            Prompt result = service.getCurrentPromptOrThrow("method1", Language.GENERAL);

            assertEquals("current text", result.getMessage()); // falls back to CURRENT
        }

        @Test
        void noPromptFound_throws() {
            assertThrows(RuntimeException.class,
                    () -> service.getCurrentPromptOrThrow("nonexistent", Language.GENERAL));
        }
    }

    @Nested
    class GetCurrentPromptOptional {

        @Test
        void noPrompt_returnsEmpty() {
            Optional<Prompt> result = service.getCurrentPrompt("missing", Language.GENERAL);
            assertTrue(result.isEmpty());
        }

        @Test
        void overrideActive_returnsOverride() {
            PromptOverrideContext.set(Map.of("test", "overridden"));

            Optional<Prompt> result = service.getCurrentPrompt("test", Language.GENERAL);

            assertTrue(result.isPresent());
            assertEquals("overridden", result.get().getMessage());
        }

        @Test
        void environmentActive_returnsEnvironmentVersion() {
            Prompt v1 = makePrompt("test", "v1", 1, State.REPLACED);
            service.addPrompt(v1);

            PromptEnvironmentResolver.setResolver((m, l, e) -> 1);
            PromptEnvironmentResolver.setEnvironment("dev");

            Optional<Prompt> result = service.getCurrentPrompt("test", Language.GENERAL);

            assertTrue(result.isPresent());
            assertEquals("v1", result.get().getMessage());
        }
    }

    // --- Helpers ---

    private Prompt makePrompt(String method, String message, int version, State state) {
        Prompt p = new Prompt();
        p.setId(UUID.randomUUID().toString());
        p.setMethod(method);
        p.setMessage(message);
        p.setVersion(version);
        p.setState(state);
        p.setLanguage(Language.GENERAL);
        return p;
    }

    /**
     * In-memory implementation of PromptServiceBase for testing default methods.
     */
    static class TestPromptService implements PromptServiceBase {
        private final List<Prompt> prompts = new ArrayList<>();

        void addPrompt(Prompt p) { prompts.add(p); }

        @Override public void configure(Map<String, String> config) {}
        @Override public boolean supportsName(String name) { return true; }

        @Override public Optional<Prompt> getPromptById(String id) {
            return prompts.stream().filter(p -> p.getId().equals(id)).findFirst();
        }

        @Override public List<Prompt> getPromptsByIds(List<String> ids) {
            return prompts.stream().filter(p -> ids.contains(p.getId())).toList();
        }

        @Override public List<Prompt> getPromptsByMethods(List<String> methods) {
            return prompts.stream().filter(p -> methods.contains(p.getMethod())).toList();
        }

        @Override public List<Prompt> getPromptsByMethodsAndState(List<String> methods, State state) {
            return prompts.stream()
                    .filter(p -> methods.contains(p.getMethod()) && p.getState() == state)
                    .toList();
        }

        @Override public List<Prompt> getPrompts() { return List.copyOf(prompts); }

        @Override public Prompt savePrompt(Prompt prompt) {
            prompts.removeIf(p -> p.getId().equals(prompt.getId()));
            prompts.add(prompt);
            return prompt;
        }

        @Override public Prompt deletePrompt(String id) {
            Prompt found = getPromptById(id).orElse(null);
            prompts.removeIf(p -> p.getId().equals(id));
            return found;
        }
    }
}
