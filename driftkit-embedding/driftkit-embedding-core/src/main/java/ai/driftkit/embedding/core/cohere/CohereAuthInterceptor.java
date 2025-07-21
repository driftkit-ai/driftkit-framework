package ai.driftkit.embedding.core.cohere;// CohereAuthInterceptor.java
import feign.RequestInterceptor;
import feign.RequestTemplate;

public class CohereAuthInterceptor implements RequestInterceptor {

    private final String apiKey;

    public CohereAuthInterceptor(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.header("Authorization", "Bearer " + apiKey);
        template.header("Content-Type", "application/json");
    }
}