package ai.driftkit.workflows.core;

import ai.driftkit.config.EtlConfig.VaultConfig;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Helper class for test setup and utilities.
 */
@Slf4j
public class TestHelper {

    /**
     * Creates a test VaultConfig for OpenAI with sensible defaults.
     * API key must be provided via environment variable or parameter.
     */
    public static VaultConfig createTestConfig(String apiKey) {
        VaultConfig config = new VaultConfig();
        config.setApiKey(apiKey != null ? apiKey : System.getenv("OPENAI_API_KEY"));

        if (config.getApiKey() == null) {
            config.setApiKey("");
        }

        config.setBaseUrl("https://api.openai.com/");
        config.setModel("gpt-4o"); // Model that supports vision and structured outputs
        config.setTemperature(0.1); // Low temperature for more consistent results
        config.setMaxTokens(1000);
        config.setJsonObject(true);
        
        return config;
    }

    /**
     * Creates a simple test image with text content for testing image processing.
     * This is useful when you don't have actual image files available.
     */
    public static byte[] createTestImage() throws IOException {
        // Create a simple image with some text
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        // Set background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 400, 300);
        
        // Add some content
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("Test Document", 50, 50);
        
        g2d.setFont(new Font("Arial", Font.PLAIN, 16));
        g2d.drawString("Name: John Doe", 50, 100);
        g2d.drawString("Age: 30", 50, 130);
        g2d.drawString("Date: 2024-01-15", 50, 160);
        g2d.drawString("Company: Example Corp", 50, 190);
        
        // Add a simple shape
        g2d.setColor(Color.BLUE);
        g2d.fillRect(300, 50, 50, 50);
        
        g2d.dispose();
        
        // Convert to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    /**
     * Prints test instructions to the console.
     */
    public static void printTestInstructions() {
        log.info("===========================================");
        log.info("     STRUCTURED OUTPUT INTEGRATION TEST");
        log.info("===========================================");
        log.info("");
        log.info("To run these tests:");
        log.info("1. Set OPENAI_API_KEY environment variable");
        log.info("2. Remove @Disabled annotation from test class");
        log.info("3. Run: mvn test -Dtest=StructuredOutputIntegrationTest");
        log.info("");
        log.info("Test Coverage:");
        log.info("✓ Text-only input with structured output");
        log.info("✓ Image-only input with structured output");
        log.info("✓ Text + Image input with structured output");
        log.info("✓ Different ResponseFormat types comparison");
        log.info("");
        log.info("===========================================");
    }
}