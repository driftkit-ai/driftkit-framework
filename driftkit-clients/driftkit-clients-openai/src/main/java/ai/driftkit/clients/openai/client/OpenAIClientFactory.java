package ai.driftkit.clients.openai.client;

import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;

public class OpenAIClientFactory {
    public static OpenAIApiClient createClient(String apiKey, String host) {
        return Feign.builder()
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .requestInterceptor(template -> template.header("Authorization", "Bearer " + apiKey))
                .logger(new Slf4jLogger(OpenAIApiClient.class))
                .logLevel(feign.Logger.Level.FULL)
                .target(OpenAIApiClient.class, host);
    }
}