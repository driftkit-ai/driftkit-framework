package ai.driftkit.vector.spring.domain;

public enum ContentType {
    PNG("image/png"),
    JPG("image/jpeg"),
    YOUTUBE_TRANSCRIPT(null),
    MICROSOFT_WORD("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    MICROSOFT_EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    MICROSOFT_POWERPOINT("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    PDF("application/pdf"),
    RTF("application/rtf"),
    TEXT("text/plain"),
    HTML("text/html"),
    XML("application/xml"),
    ODF_TEXT("application/vnd.oasis.opendocument.text"),
    ODF_SPREADSHEET("application/vnd.oasis.opendocument.spreadsheet"),
    ODF_PRESENTATION("application/vnd.oasis.opendocument.presentation"),
    SQLITE("application/vnd.sqlite3"),
    ACCESS("application/vnd.ms-access");

    private final String mimeType;

    ContentType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }

    public static ContentType fromString(String mimeType) {
        for (ContentType type : ContentType.values()) {
            if (type.mimeType == null) {
                continue;
            }

            if (type.mimeType.equalsIgnoreCase(mimeType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported MIME type: " + mimeType);
    }
}