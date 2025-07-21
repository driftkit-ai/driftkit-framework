package ai.driftkit.audio.core.config;

/**
 * Enumeration of supported processing modes.
 */
public enum ProcessingMode {
    BATCH("batch"),
    STREAMING("streaming");
    
    private final String value;
    
    ProcessingMode(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static ProcessingMode fromValue(String value) {
        for (ProcessingMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown processing mode: " + value);
    }
}