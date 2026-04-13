package ai.driftkit.clients.openai.client;

import feign.Feign;
import feign.Request;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenAIClientFactory {

    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 300; // 5 minutes for long-running requests

    public static OpenAIApiClient createClient(String apiKey, String host) {
        return createClient(apiKey, host, null, null);
    }

    /**
     * Creates an OpenAI API client with configurable timeouts.
     *
     * @param apiKey API key for authentication
     * @param host Base URL
     * @param connectTimeoutSeconds Connection timeout in seconds (default: 30)
     * @param readTimeoutSeconds Read timeout in seconds (default: 300 / 5 minutes)
     * @return Configured OpenAIApiClient
     */
    public static OpenAIApiClient createClient(String apiKey, String host,
                                                Integer connectTimeoutSeconds,
                                                Integer readTimeoutSeconds) {
        int connectTimeout = (connectTimeoutSeconds != null && connectTimeoutSeconds > 0)
                ? connectTimeoutSeconds : DEFAULT_CONNECT_TIMEOUT_SECONDS;
        int readTimeout = (readTimeoutSeconds != null && readTimeoutSeconds > 0)
                ? readTimeoutSeconds : DEFAULT_READ_TIMEOUT_SECONDS;

        log.debug("Creating OpenAI client with connectTimeout={}s, readTimeout={}s", connectTimeout, readTimeout);

        return Feign.builder()
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .requestInterceptor(template -> template.header("Authorization", "Bearer " + apiKey))
                .logger(new Slf4jLogger(OpenAIApiClient.class))
                .logLevel(feign.Logger.Level.FULL)
                .options(new Request.Options(connectTimeout * 1000, readTimeout * 1000))
                .target(OpenAIApiClient.class, host);
    }
}
