package ai.driftkit.contextengineering.autoconfigure;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
public class WebMvcConfiguration {

    static final String UI_LOCATION = "static/prompt-engineering/";

    @PostConstruct
    void checkUiBundle() {
        if (!new ClassPathResource(UI_LOCATION + "index.html").exists()) {
            log.warn("Prompt engineering UI bundle not found on the classpath ({}index.html). "
                    + "/prompt-engineering will return 404. The starter was probably built with -Dskip.frontend=true",
                    UI_LOCATION);
        }
    }

    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                // Explicitly configure static resource handling for frontend
                registry.addResourceHandler("/prompt-engineering/**")
                        .addResourceLocations("classpath:/" + UI_LOCATION)
                        .setCachePeriod(3600);
            }
        };
    }
}
