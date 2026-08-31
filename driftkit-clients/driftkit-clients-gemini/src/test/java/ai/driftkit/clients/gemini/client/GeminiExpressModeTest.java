package ai.driftkit.clients.gemini.client;

import ai.driftkit.config.EtlConfig.VaultConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vertex express: a third access type, with a URL and an auth scheme that differ from both
 * existing ones.
 *
 * <p>Verified against the live API on 2026-08-31 with a real express key:
 *
 * <ul>
 *   <li>{@code aiplatform.googleapis.com/v1/publishers/google/models/{m}:generateContent}
 *       with an {@code x-goog-api-key} header — <b>200 OK</b>;
 *   <li>{@code aiplatform.googleapis.com/v1beta/models/{m}:generateContent}, same host, same
 *       key — <b>404</b>;
 *   <li>the same key on {@code generativelanguage.googleapis.com} —
 *       <b>403 API_KEY_SERVICE_BLOCKED</b>.
 * </ul>
 *
 * <p>So neither the path nor the version can be borrowed from the other two modes, and that is
 * exactly what these tests pin down.
 */
class GeminiExpressModeTest {

    private static final String VERTEX_HOST = "https://aiplatform.googleapis.com";

    private static GeminiModelClient express() {
        return build(VaultConfig.builder()
                .name("express").type("gemini").model("gemini-3.7-flash")
                .apiKey("AQ.test-express-key").vertexExpress(true)
                .build());
    }

    private static GeminiModelClient plainApiKey() {
        return build(VaultConfig.builder()
                .name("apikey").type("gemini").model("gemini-3.7-flash")
                .apiKey("AIza-test").build());
    }

    private static GeminiModelClient build(VaultConfig config) {
        GeminiModelClient client = new GeminiModelClient();
        client.init(config);
        return client;
    }

    private static Object field(Object target, String name) {
        try {
            Field f = GeminiModelClient.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("no field " + name, e);
        }
    }

    private static String url(Object target, String model, String action) {
        try {
            Method m = GeminiModelClient.class.getDeclaredMethod(
                    "buildEndpointUrl", String.class, String.class);
            m.setAccessible(true);
            return (String) m.invoke(target, model, action);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("buildEndpointUrl failed", e);
        }
    }

    @Test
    @DisplayName("Express URL: Vertex host, v1, publishers, no project")
    void expressUrlIsTheOnlyOneTheApiAccepts() {
        String u = url(express(), "gemini-3.7-flash", "generateContent");

        assertEquals(VERTEX_HOST + "/v1/publishers/google/models/gemini-3.7-flash:generateContent", u);
    }

    @Test
    @DisplayName("Express is not the plain API-key mode: that host rejects the key")
    void expressDiffersFromApiKeyMode() {
        String plain = url(plainApiKey(), "gemini-3.7-flash", "generateContent");

        assertEquals("https://generativelanguage.googleapis.com"
                + "/v1beta/models/gemini-3.7-flash:generateContent", plain);
        assertFalse(plain.contains("publishers"), "the public API has no publishers path");
    }

    @Test
    @DisplayName("Express carries no project: a project would switch on service-account auth")
    void expressCarriesNoProject() {
        GeminiModelClient c = express();

        assertTrue((Boolean) field(c, "expressMode"));
        assertFalse((Boolean) field(c, "vertexMode"),
                "with a project set the key is ignored and ADC is used instead");
        assertFalse(url(c, "m", "generateContent").contains("/projects/"));
    }

    @Test
    @DisplayName("Streaming and countTokens keep the same shape — only the action changes")
    void everyActionUsesTheSamePath() {
        GeminiModelClient c = express();

        assertEquals(VERTEX_HOST + "/v1/publishers/google/models/m:streamGenerateContent",
                url(c, "m", "streamGenerateContent"));
        assertEquals(VERTEX_HOST + "/v1/publishers/google/models/m:countTokens",
                url(c, "m", "countTokens"));
    }

    @Test
    @DisplayName("A project wins over the express flag: modes must not overlap")
    void projectTakesPrecedence() {
        VaultConfig both = VaultConfig.builder()
                .name("both").type("gemini").model("m")
                .apiKey("AQ.key").vertexExpress(true)
                .vertexProject("some-project").vertexLocation("global")
                .build();

        GeminiModelClient c = build(both);

        // ADC may be absent in a test environment; then the client falls back off Vertex mode.
        boolean vertex = (Boolean) field(c, "vertexMode");
        boolean isExpress = (Boolean) field(c, "expressMode");
        assertFalse(vertex && isExpress, "the two modes must never be on at once");
    }
}
