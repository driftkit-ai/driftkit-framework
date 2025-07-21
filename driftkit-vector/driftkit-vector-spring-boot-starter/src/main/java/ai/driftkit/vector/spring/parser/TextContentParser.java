package ai.driftkit.vector.spring.parser;

import ai.driftkit.vector.spring.domain.ContentType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor
public class TextContentParser {

    public static class ParseResult {
        private final String content;
        private final Map<String, String> metadata;

        public ParseResult(String content, Map<String, String> metadata) {
            this.content = content;
            this.metadata = metadata;
        }

        public String getContent() {
            return content;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }
    }

    public static ParseResult parse(byte[] inputBytes, ContentType mimeType) {
        InputStream inputStream = new ByteArrayInputStream(inputBytes);
        BodyContentHandler handler = new BodyContentHandler(-1); // No content length limit
        Metadata metadata = new Metadata();

        metadata.set(Metadata.CONTENT_TYPE, mimeType.getMimeType());

        AutoDetectParser parser = new AutoDetectParser();
        try {
            parser.parse(inputStream, handler, metadata);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing the document: %s".formatted(e.getMessage()), e);
        }

        Map<String, String> metadataMap = new HashMap<>();
        for (String name : metadata.names()) {
            metadataMap.put(name, metadata.get(name));
        }

        return new ParseResult(handler.toString(), metadataMap);
    }

}