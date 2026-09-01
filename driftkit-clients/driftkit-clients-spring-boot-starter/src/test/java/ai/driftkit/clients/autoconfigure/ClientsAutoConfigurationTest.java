package ai.driftkit.clients.autoconfigure;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.config.EtlConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the starter's auto-configuration is registered for Spring Boot 3
 * (AutoConfiguration.imports) and behaves as documented.
 */
class ClientsAutoConfigurationTest {

    // ConfigurationPropertiesAutoConfiguration provides the @ConfigurationProperties binder that a real
    // Spring Boot application always has; without it the @Bean-level @ConfigurationProperties is not bound.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    EtlConfigAutoConfiguration.class,
                    ModelClientAutoConfiguration.class));

    @Test
    void importsFileRegistersBothAutoConfigurations() throws Exception {
        String imports = new String(getClass().getClassLoader()
                .getResourceAsStream("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                .readAllBytes());
        assertThat(imports).contains(EtlConfigAutoConfiguration.class.getName());
        assertThat(imports).contains(ModelClientAutoConfiguration.class.getName());
        Class.forName(EtlConfigAutoConfiguration.class.getName());
        Class.forName(ModelClientAutoConfiguration.class.getName());
    }

    @Test
    void withoutVaultOnlyEtlConfigIsCreated() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(EtlConfig.class);
            assertThat(context).doesNotHaveBean(ModelClient.class);
        });
    }

    @Test
    void vaultEntrySelectsProviderByType() {
        runner.withPropertyValues(
                        "driftkit.vault[0].name=primary",
                        "driftkit.vault[0].type=fakestarter",
                        "driftkit.vault[0].api-key=key")
                .run(context -> {
                    assertThat(context).hasSingleBean(ModelClient.class);
                    assertThat(context.getBean("primaryModelClient")).isInstanceOf(FakeStarterModelClient.class);
                });
    }

    @Test
    void unknownProviderFailsStartupWithActionableMessage() {
        runner.withPropertyValues("driftkit.vault[0].name=primary", "driftkit.vault[0].type=nope")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("nope")
                            .hasMessageContaining("fakestarter");
                });
    }

    @Test
    void userDefinedModelClientWins() {
        runner.withUserConfiguration(UserClient.class)
                .withPropertyValues("driftkit.vault[0].name=primary", "driftkit.vault[0].type=fakestarter")
                .run(context -> {
                    assertThat(context).hasSingleBean(ModelClient.class);
                    assertThat(context).doesNotHaveBean("primaryModelClient");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserClient {
        @Bean
        ModelClient<?> myClient() {
            return new FakeStarterModelClient();
        }
    }
}
