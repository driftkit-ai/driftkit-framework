package ai.driftkit.common.domain;

/**
 * Enumeration of supported language codes.
 * Unified language enum used across all DriftKit modules.
 */
public enum Language {
    GENERAL("general"),
    ENGLISH("en"),
    SPANISH("es"),
    FRENCH("fr"),
    GERMAN("de"),
    ITALIAN("it"),
    PORTUGUESE("pt"),
    DUTCH("nl"),
    RUSSIAN("ru"),
    CHINESE("zh"),
    JAPANESE("ja"),
    KOREAN("ko"),
    ARABIC("ar"),
    HINDI("hi"),
    TURKISH("tr"),
    POLISH("pl"),
    SWEDISH("sv"),
    NORWEGIAN("no"),
    DANISH("da"),
    FINNISH("fi"),
    CZECH("cs"),
    HUNGARIAN("hu"),
    ROMANIAN("ro"),
    BULGARIAN("bg"),
    CROATIAN("hr"),
    SLOVAK("sk"),
    SLOVENIAN("sl"),
    ESTONIAN("et"),
    LATVIAN("lv"),
    LITHUANIAN("lt"),
    UKRAINIAN("uk"),
    VIETNAMESE("vi"),
    THAI("th"),
    INDONESIAN("id"),
    MALAY("ms"),
    TAMIL("ta"),
    BENGALI("bn"),
    GUJARATI("gu"),
    MARATHI("mr"),
    TELUGU("te"),
    KANNADA("kn"),
    MALAYALAM("ml"),
    PUNJABI("pa"),
    URDU("ur"),
    HEBREW("he"),
    PERSIAN("fa"),
    GREEK("el"),
    CATALAN("ca"),
    BASQUE("eu"),
    GALICIAN("gl"),
    WELSH("cy"),
    IRISH("ga"),
    SCOTTISH_GAELIC("gd"),
    ICELANDIC("is"),
    MALTESE("mt"),
    AFRIKAANS("af"),
    SWAHILI("sw"),
    YORUBA("yo"),
    HAUSA("ha"),
    IGBO("ig"),
    AMHARIC("am"),
    SOMALI("so"),
    MULTI("multi");
    
    private final String value;
    
    Language(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static Language fromValue(String value) {
        if (value == null) {
            return GENERAL; // Default
        }
        
        for (Language language : values()) {
            if (language.value.equals(value)) {
                return language;
            }
        }
        throw new IllegalArgumentException("Unknown language code: " + value);
    }
}