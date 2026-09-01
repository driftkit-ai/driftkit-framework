package ai.driftkit.audio.autoconfigure;

import ai.driftkit.audio.service.AudioSessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The audio auto-configuration must stay inert unless explicitly enabled: the session manager
 * creates the transcription engine at startup and needs a provider API key.
 */
class AudioAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AutoConfiguration.class));

    @Test
    void importsFilePointsAtAnExistingClass() throws Exception {
        String imports = new String(getClass().getClassLoader()
                .getResourceAsStream("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                .readAllBytes()).trim();
        assertThat(imports).isEqualTo(AutoConfiguration.class.getName());
        Class.forName(imports);
    }

    @Test
    void disabledByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AudioSessionManager.class);
        });
    }

    @Test
    void enabledWithoutApiKeyFailsFastWithClearMessage() {
        runner.withPropertyValues("audio.processing.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("API key");
                });
    }
}
