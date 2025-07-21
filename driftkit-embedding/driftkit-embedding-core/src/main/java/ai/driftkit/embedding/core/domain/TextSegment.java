package ai.driftkit.embedding.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TextSegment {

    private final String text;
    private final Metadata metadata;

    public static TextSegment from(String text) {
        return new TextSegment(text, new Metadata());
    }

    public static TextSegment from(String text, Metadata metadata) {
        return new TextSegment(text, metadata);
    }

    public String text() {
        return text;
    }
}
