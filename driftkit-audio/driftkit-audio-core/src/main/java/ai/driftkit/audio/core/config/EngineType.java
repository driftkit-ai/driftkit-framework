package ai.driftkit.audio.core.config;

/**
 * Enumeration of supported transcription engines.
 */
public enum EngineType {
    ASSEMBLYAI("assemblyai"),
    DEEPGRAM("deepgram");
    
    private final String value;
    
    EngineType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static EngineType fromValue(String value) {
        for (EngineType engine : values()) {
            if (engine.value.equals(value)) {
                return engine;
            }
        }
        throw new IllegalArgumentException("Unknown transcription engine: " + value);
    }
}