package ai.driftkit.audio.core;

import lombok.Getter;

/**
 * Enumeration of supported audio formats for conversion.
 */
@Getter
public enum AudioFormatType {
    // Java Sound API supported formats (fastest)
    WAV("wav", "WAVE", true, false),
    AU("au", "AU", true, false),
    AIFF("aiff", "AIFF", true, false),
    
    // JAVE library supported formats (medium speed)
    MP3("mp3", "MP3", false, true),
    OGG("ogg", "OGG", false, true),
    FLAC("flac", "FLAC", false, true),
    AAC("aac", "AAC", false, true),
    
    // FFmpeg fallback formats (slower but comprehensive)
    WMA("wma", "WMA", false, false),
    M4A("m4a", "M4A", false, false),
    OPUS("opus", "OPUS", false, false),
    AC3("ac3", "AC3", false, false);
    
    private final String extension;
    private final String displayName;
    private final boolean javaSoundSupported;
    private final boolean javeSupported;
    
    AudioFormatType(String extension, String displayName, boolean javaSoundSupported, boolean javeSupported) {
        this.extension = extension;
        this.displayName = displayName;
        this.javaSoundSupported = javaSoundSupported;
        this.javeSupported = javeSupported;
    }
    
    /**
     * Get the file extension for this format.
     */
    public String getExtension() {
        return extension;
    }
    
    /**
     * Get the display name for this format.
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Check if this format is supported by Java Sound API.
     */
    public boolean isJavaSoundSupported() {
        return javaSoundSupported;
    }
    
    /**
     * Check if this format is supported by JAVE library.
     */
    public boolean isJaveSupported() {
        return javeSupported;
    }
    
    /**
     * Get AudioFormat from string extension.
     * 
     * @param extension File extension (with or without dot)
     * @return AudioFormat enum value
     * @throws IllegalArgumentException if format is not supported
     */
    public static AudioFormatType fromExtension(String extension) {
        if (extension == null) {
            throw new IllegalArgumentException("Extension cannot be null");
        }
        
        // Remove dot if present
        String cleanExtension = extension.startsWith(".") ? extension.substring(1) : extension;
        
        for (AudioFormatType format : values()) {
            if (format.extension.equalsIgnoreCase(cleanExtension)) {
                return format;
            }
        }
        
        throw new IllegalArgumentException("Unsupported audio format: " + extension);
    }
    
    /**
     * Check if the format is supported by any conversion method.
     */
    public boolean isSupported() {
        return javaSoundSupported || javeSupported;
    }
    
    /**
     * Get the preferred conversion method for this format.
     */
    public ConversionMethod getPreferredMethod() {
        if (javaSoundSupported) {
            return ConversionMethod.JAVA_SOUND;
        } else if (javeSupported) {
            return ConversionMethod.JAVE;
        } else {
            return ConversionMethod.FFMPEG;
        }
    }
    
    /**
     * Enumeration of conversion methods.
     */
    public enum ConversionMethod {
        JAVA_SOUND("Java Sound API - Fastest, native Java"),
        JAVE("JAVE Library - Fast, embedded FFmpeg"),
        FFMPEG("FFmpeg - Slower, external dependency but most comprehensive");
        
        private final String description;
        
        ConversionMethod(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}