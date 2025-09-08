package ai.driftkit.chat.framework.ai.client;

import feign.RequestInterceptor;
import feign.codec.Encoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
@Slf4j
public class FeignConfig {

    @Value("${ai-props.username:}")
    private String username;
    
    @Value("${ai-props.password:}")
    private String password;
    
    @Autowired
    private ObjectFactory<HttpMessageConverters> messageConverters;

    @Bean
    public RequestInterceptor basicAuthRequestInterceptor() {
        return requestTemplate -> {
            if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                String auth = username + ":" + password;
                byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
                String authHeader = "Basic " + new String(encodedAuth);
                requestTemplate.header("Authorization", authHeader);
            } else {
                log.warn("AI client credentials not configured. Set ai-props.username and ai-props.password");
            }
            requestTemplate.header("Content-Type", "application/json");
            requestTemplate.header("Accept", "application/json");
        };
    }
    
    @Bean("aiClientEncoder")
    @Primary
    public Encoder aiClientEncoder() {
        return new SpringEncoder(this.messageConverters);
    }
}