package ai.driftkit.clients.claude;

import ai.driftkit.clients.claude.client.ClaudeModelClient;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.config.EtlConfig.VaultConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "RUN_EXPENSIVE_TESTS", matches = "true")
public class ClaudeImageTest {
    
    private ClaudeModelClient client;
    private static final String API_KEY = System.getenv("CLAUDE_API_KEY");
    
    @BeforeEach
    void setUp() {
        assertNotNull(API_KEY, "CLAUDE_API_KEY environment variable must be set");
        
        VaultConfig config = VaultConfig.builder()
                .apiKey(API_KEY)
                .model(ClaudeModelClient.CLAUDE_DEFAULT)
                .temperature(0.7)
                .maxTokens(1000)
                .build();
        
        client = new ClaudeModelClient();
        client.init(config);
    }
    
    @Test
    void testImageAnalysis() throws IOException {
        byte[] imageData = createTestImage();
        
        ModelContentMessage message = ModelContentMessage.builder()
                .role(Role.user)
                .content(List.of(
                        ModelContentMessage.ModelContentElement.builder()
                                .type(ModelTextRequest.MessageType.text)
                                .text("What do you see in this image? Describe the colors and shapes.")
                                .build(),
                        ModelContentMessage.ModelContentElement.builder()
                                .type(ModelTextRequest.MessageType.image)
                                .image(new ModelContentMessage.ModelContentElement.ImageData(
                                        imageData,
                                        "image/png"
                                ))
                                .build()
                ))
                .build();
        
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(message))
                .temperature(0.3)
                .build();
        
        ModelTextResponse response = client.imageToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getChoices());
        assertFalse(response.getChoices().isEmpty());
        
        String content = response.getChoices().get(0).getMessage().getContent();
        assertNotNull(content);
        assertFalse(content.isEmpty());
        // Should mention blue background and red square
        assertTrue(content.toLowerCase().contains("blue") || content.toLowerCase().contains("red"));
    }
    
    @Test
    void testImageWithText() throws IOException {
        byte[] imageData = createTestImageWithText("Hello Claude!");
        
        ModelContentMessage message = ModelContentMessage.builder()
                .role(Role.user)
                .content(List.of(
                        ModelContentMessage.ModelContentElement.builder()
                                .type(ModelTextRequest.MessageType.text)
                                .text("What text do you see in this image?")
                                .build(),
                        ModelContentMessage.ModelContentElement.builder()
                                .type(ModelTextRequest.MessageType.image)
                                .image(new ModelContentMessage.ModelContentElement.ImageData(
                                        imageData,
                                        "image/png"
                                ))
                                .build()
                ))
                .build();
        
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(message))
                .temperature(0.1)
                .build();
        
        ModelTextResponse response = client.imageToText(request);
        
        assertNotNull(response);
        String content = response.getChoices().get(0).getMessage().getContent();
        assertNotNull(content);
        assertTrue(content.contains("Hello Claude!"));
    }
    
    @Test
    void testMultipleImagesComparison() throws IOException {
        byte[] image1 = createTestImage();
        byte[] image2 = createTestImageWithText("Test Text");
        
        ModelContentMessage message = ModelContentMessage.builder()
                .role(Role.user)
                .content(List.of(
                        ModelContentMessage.ModelContentElement.builder()
                                .type(ModelTextRequest.MessageType.text)
                                .text("Compare these two images. What are the differences?")
                                .build(),
                        ModelContentMessage.ModelContentElement.builder()
                                .type(ModelTextRequest.MessageType.image)
                                .image(new ModelContentMessage.ModelContentElement.ImageData(
                                        image1,
                                        "image/png"
                                ))
                                .build(),
                        ModelContentMessage.ModelContentElement.builder()
                                .type(ModelTextRequest.MessageType.text)
                                .text("versus")
                                .build(),
                        ModelContentMessage.ModelContentElement.builder()
                                .type(ModelTextRequest.MessageType.image)
                                .image(new ModelContentMessage.ModelContentElement.ImageData(
                                        image2,
                                        "image/png"
                                ))
                                .build()
                ))
                .build();
        
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(message))
                .temperature(0.3)
                .build();
        
        ModelTextResponse response = client.imageToText(request);
        
        assertNotNull(response);
        String content = response.getChoices().get(0).getMessage().getContent();
        assertNotNull(content);
        // Should mention differences between the images
        assertTrue(content.toLowerCase().contains("text") || content.toLowerCase().contains("difference"));
    }
    
    // Helper methods
    
    private byte[] createTestImage() throws IOException {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        // Blue background
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 200, 200);
        
        // Red rectangle
        g.setColor(Color.RED);
        g.fillRect(50, 50, 100, 100);
        
        g.dispose();
        return imageToBytes(image, "png");
    }
    
    private byte[] createTestImageWithText(String text) throws IOException {
        BufferedImage image = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        // White background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 400, 200);
        
        // Black text
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 30));
        g.drawString(text, 50, 100);
        
        g.dispose();
        return imageToBytes(image, "png");
    }
    
    private byte[] imageToBytes(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }
    
    private String createBase64TestImage() {
        try {
            BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.BLUE);
            g.fillRect(0, 0, 200, 200);
            g.setColor(Color.RED);
            g.fillRect(50, 50, 100, 100);
            g.dispose();
            
            byte[] imageBytes = imageToBytes(image, "png");
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create base64 test image", e);
        }
    }
}