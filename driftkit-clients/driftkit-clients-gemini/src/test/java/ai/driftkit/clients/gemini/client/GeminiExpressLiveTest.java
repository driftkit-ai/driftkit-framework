package ai.driftkit.clients.gemini.client;

import ai.driftkit.common.domain.client.*;
import ai.driftkit.config.EtlConfig.VaultConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Vertex express against the live API. Runs only when an express key is present.
 *
 * <p>{@code GOOGLE_API_KEY=<express key> mvn -pl driftkit-clients/driftkit-clients-gemini test
 * -Dtest=GeminiExpressLiveTest}
 */
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
class GeminiExpressLiveTest {

    private static ModelClient express(String model) {
        return GeminiModelClient.create(VaultConfig.builder()
                .name("express-live").type("gemini").model(model)
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .vertexExpress(true)
                .temperature(0.1).maxTokens(1024)
                .connectTimeout(30).readTimeout(120)
                .build());
    }

    @Test
    @DisplayName("Express answers on the models the pipeline uses")
    void expressAnswers() {
        for (String model : new String[]{"gemini-3.7-flash", "gemini-3.1-pro-preview"}) {
            ModelTextResponse response = express(model).textToText(ModelTextRequest.builder()
                    .model(model)
                    .messages(java.util.List.of(
                            ModelContentMessage.create(Role.user, "Answer with the word ok")))
                    .build());

            String text = response.getResponse();
            System.out.printf("  %-24s → %s%n", model, text == null ? "(пусто)" : text.strip());
            assertNotNull(text, model + ": ответ пуст");
            assertFalse(text.isBlank(), model + ": ответ пуст");
        }
    }
}
