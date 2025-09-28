package ai.driftkit.clients.gemini.client;

import ai.driftkit.clients.gemini.utils.GeminiUtils;
import ai.driftkit.common.domain.client.*;
import ai.driftkit.common.domain.client.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelContentMessage.ModelContentElement;
import ai.driftkit.config.EtlConfig;
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

@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
public class GeminiImageTest {
    
    private GeminiModelClient client;
    
    @BeforeEach
    void setUp() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        assertNotNull(apiKey, "GEMINI_API_KEY environment variable must be set");
        
        EtlConfig.VaultConfig config = new EtlConfig.VaultConfig();
        config.setName("gemini-image-test");
        config.setApiKey(apiKey);
        config.setModel(GeminiUtils.GEMINI_FLASH_2_5);
        config.setImageModel(GeminiUtils.GEMINI_IMAGE_MODEL);
        config.setTemperature(0.7);
        
        client = new GeminiModelClient();
        client.init(config);
    }
    
    @Test
    void testImageToText() throws IOException {
        // Create a simple test image
        BufferedImage image = createTestImage();
        byte[] imageBytes = imageToBytes(image, "png");
        
        ModelContentElement.ImageData imageData = new ModelContentElement.ImageData(imageBytes, "image/png");
        
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, "What do you see in this image? Describe the colors and shapes.", imageData)
                ))
                .model(GeminiUtils.GEMINI_FLASH_2_5)
                .build();
        
        ModelTextResponse response = client.imageToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getResponse());
        // Should describe the red rectangle on blue background
        assertTrue(response.getResponse().toLowerCase().contains("red") || 
                   response.getResponse().toLowerCase().contains("blue") ||
                   response.getResponse().toLowerCase().contains("rectangle") ||
                   response.getResponse().toLowerCase().contains("square"));
    }
    
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_EXPENSIVE_TESTS", matches = "true")
    void testTextToImage() {
        // Test image generation
        ModelImageRequest request = ModelImageRequest.builder()
                .model(GeminiUtils.GEMINI_IMAGE_MODEL)
                .prompt("A simple red circle on a white background, minimalist style")
                .n(1)
                .build();
        
        ModelImageResponse response = client.textToImage(request);
        
        assertNotNull(response);
        assertNotNull(response.getBytes());
        assertFalse(response.getBytes().isEmpty());
        
        // Verify we got an image
        ModelContentElement.ImageData generatedImage = response.getBytes().get(0);
        assertNotNull(generatedImage.getImage());
        assertTrue(generatedImage.getImage().length > 0);
        assertNotNull(generatedImage.getMimeType());
    }
    
    @Test
    void testMultimodalConversation() throws IOException {
        // Create a test image with text
        BufferedImage image = createTestImageWithText("Hello Gemini!");
        byte[] imageBytes = imageToBytes(image, "png");
        ModelContentElement.ImageData imageData = new ModelContentElement.ImageData(imageBytes, "image/png");
        
        // First message with image
        ModelTextRequest request1 = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, 
                                List.of("What text do you see in this image?"), 
                                List.of(imageData))
                ))
                .model(GeminiUtils.GEMINI_FLASH_2_5)
                .build();
        
        ModelTextResponse response1 = client.textToText(request1);
        assertNotNull(response1);
        
        // Follow-up question without image
        ModelTextRequest request2 = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, 
                                List.of("What text do you see in this image?"), 
                                List.of(imageData)),
                        ModelContentMessage.create(Role.assistant, response1.getResponse()),
                        ModelContentMessage.create(Role.user, "What color was the text?")
                ))
                .model(GeminiUtils.GEMINI_FLASH_2_5)
                .build();
        
        ModelTextResponse response2 = client.textToText(request2);
        
        assertNotNull(response2);
        assertNotNull(response2.getResponse());
        // Should mention the color black or the text
        assertTrue(response2.getResponse().toLowerCase().contains("black") ||
                   response2.getResponse().toLowerCase().contains("dark"));
    }
    
    @Test
    void testBase64ImageInput() {
        // Test with base64 encoded image
        String base64Image = createBase64TestImage();
        
        ModelTextRequest request = ModelTextRequest.builder()
                .messages(List.of(
                        ModelContentMessage.create(Role.user, List.of("Describe this image"), 
                                List.of(new ModelContentElement.ImageData(
                                        Base64.getDecoder().decode(base64Image), 
                                        "image/png"
                                )))
                ))
                .model(GeminiUtils.GEMINI_FLASH_2_5)
                .build();
        
        ModelTextResponse response = client.textToText(request);
        
        assertNotNull(response);
        assertNotNull(response.getResponse());
        assertTrue(response.getResponse().length() > 10);
    }
    
    // Helper methods
    
    private BufferedImage createTestImage() {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        // Blue background
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 200, 200);
        
        // Red rectangle
        g.setColor(Color.RED);
        g.fillRect(50, 50, 100, 100);
        
        g.dispose();
        return image;
    }
    
    private BufferedImage createTestImageWithText(String text) {
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
        return image;
    }
    
    private byte[] imageToBytes(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }
    
    private String createBase64TestImage() {
        try {
            BufferedImage image = createTestImage();
            byte[] imageBytes = imageToBytes(image, "png");
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create base64 test image", e);
        }
    }
}