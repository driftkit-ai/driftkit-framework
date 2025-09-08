package test.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test application for running workflow controllers.
 * Minimal configuration - relies entirely on auto-configuration.
 * This simulates how a real client application would use the library.
 */
@SpringBootApplication
public class TestApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}