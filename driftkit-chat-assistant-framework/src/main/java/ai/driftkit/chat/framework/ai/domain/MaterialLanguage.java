package ai.driftkit.chat.framework.ai.domain;

public enum MaterialLanguage {
    ENGLISH("english"),
    SPANISH("spanish"),
    PORTUGUESE("portuguese"),
    GERMAN("german"),
    FRENCH("french"),
    ITALIAN("italian"),
    RUSSIAN("russian");

    private final String value;

    MaterialLanguage(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}