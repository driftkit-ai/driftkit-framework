package ai.driftkit.workflows.core;

/**
 * Common constants used in tests.
 */
public final class TestConstants {
    
    private TestConstants() {
        // Prevent instantiation
    }
    
    // API URLs
    public static final String OPENAI_API_BASE_URL = "https://api.openai.com/";
    
    // Test Data URLs
    public static final String EXAMPLE_DOCUMENT_URL = "https://example.com/document.pdf";
    public static final String EXAMPLE_REVIEW_URL = "https://example.com/review.txt";
    
    // Collection Names
    public static final String MODEL_REQUEST_TRACES_COLLECTION = "model_request_traces";
    public static final String IMAGE_MESSAGE_TASKS_COLLECTION = "image_message_tasks";
    
    // Default Values
    public static final String DEFAULT_MODEL = "gpt-4";
    public static final int DEFAULT_TIMEOUT_MS = 30000;
    public static final int DEFAULT_MAX_TOKENS = 2000;
}