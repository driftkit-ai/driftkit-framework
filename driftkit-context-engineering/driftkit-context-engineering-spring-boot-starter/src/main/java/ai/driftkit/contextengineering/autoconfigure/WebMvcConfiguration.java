package ai.driftkit.contextengineering.autoconfigure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration {

    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                // Explicitly configure static resource handling for frontend
                registry.addResourceHandler("/prompt-engineering/**")
                        .addResourceLocations("classpath:/static/prompt-engineering/")
                        .setCachePeriod(3600);
            }
        };
    }
}