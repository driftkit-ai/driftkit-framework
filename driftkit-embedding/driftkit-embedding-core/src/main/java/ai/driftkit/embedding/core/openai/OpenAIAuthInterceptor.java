package ai.driftkit.embedding.core.openai;// OpenAIAuthInterceptor.java
import feign.RequestInterceptor;
import feign.RequestTemplate;

public class OpenAIAuthInterceptor implements RequestInterceptor {

    private final String apiKey;

    public OpenAIAuthInterceptor(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.header("Authorization", "Bearer " + apiKey);
        template.header("Content-Type", "application/json");
    }
}