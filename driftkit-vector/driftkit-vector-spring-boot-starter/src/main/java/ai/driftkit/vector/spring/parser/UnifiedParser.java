package ai.driftkit.vector.spring.parser;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.clients.core.ModelClientFactory;
import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.VaultConfig;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage.ModelContentElement.ImageData;
import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.vector.spring.domain.ContentType;
import ai.driftkit.vector.spring.domain.ParsedContent;
import ai.driftkit.vector.spring.parser.TextContentParser.ParseResult;
import ai.driftkit.vector.spring.parser.YoutubeSubtitleParser.*;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UnifiedParser {
    public static final int MAX_INPUT_FILE_TO_STORE = 512_000;

    private DefaultYoutubeTranscriptApi youtubeParser;
    private ModelClient modelClient;
    private VaultConfig modelConfig;

    public UnifiedParser() {
    }

    @Autowired
    public UnifiedParser(EtlConfig etlConfig) {
        this.youtubeParser = (DefaultYoutubeTranscriptApi) TranscriptApiFactory.createDefault(etlConfig.getYoutubeProxy());

        this.modelConfig = etlConfig.getVault().getFirst();
        this.modelClient = ModelClientFactory.fromConfig(modelConfig);
    }

    public ParsedContent parse(ParserInput input) throws IOException {
        ParsedContent content = new ParsedContent();
        content.setId(UUID.randomUUID().toString());
        content.setInput(input);
        content.setParsingStatedTime(System.currentTimeMillis());

        ContentType contentType = input.getContentType();

        switch (contentType) {
            case JPG:
            case PNG:
                ModelTextResponse textResponse = modelClient.imageToText(
                        ModelTextRequest.builder()
                                .temperature(modelConfig.getTemperature())
                                .model(Optional.ofNullable(modelConfig.getModel()).orElseThrow(() -> new RuntimeException("Model not configured")))
                                .messages(List.of(ModelContentMessage.create(
                                        Role.user, "Please describe the image", new ImageData(
                                                (byte[]) input.getInput(), contentType.getMimeType()
                                        )
                                )))
                                .build()
                );
                content.setParsedContent(textResponse.getResponse());
                content.setParsingEndTime(System.currentTimeMillis());
                content.setCreatedTime(content.getParsingEndTime());
                content.setMetadata(textResponse);
                return content;
            case YOUTUBE_TRANSCRIPT:
                if (!(input instanceof YoutubeIdParserInput params)) {
                    throw new RuntimeException("Wrong type of ParserInput to parse Youtube Video");
                }

                String primaryLang = params.getPrimaryLang();

                String[] languages = params.getInput().toArray(new String[0]);
                Transcript transcript = youtubeParser.getTranscript(params.getVideoId(), primaryLang, languages);

                Dialog dialog = youtubeParser.mapToDialog(transcript);

                List<DialogTurn> turns = dialog.getTurns().stream()
                        .filter(e -> !e.getText().contains("<c>"))
                        .toList();

                StringBuilder builder = new StringBuilder();

                for (DialogTurn turn : turns) {
                    builder.append(turn.getSpeaker()).append(": ").append(turn.getText()).append('\n');
                }

                content.setParsedContent(builder.toString());
                content.setMetadata(dialog);

                content.setParsingEndTime(System.currentTimeMillis());
                content.setCreatedTime(content.getParsingEndTime());

                return content;
            case TEXT:
            case HTML:
            case XML:
                String text;

                if (input instanceof StringParserInput textInput) {
                    text = textInput.getInput();
                } else if (input instanceof ByteArrayParserInput baInput) {
                    text = new String(baInput.getInput());
                } else {
                    throw new RuntimeException("Wrong type of ParserInput to parse content %s".formatted(contentType));
                }


                if (contentType == ContentType.HTML) {
                    Document doc = Jsoup.parse(text);
                    text = doc.body().text();
                }

                content.setParsedContent(text);

                content.setParsingEndTime(System.currentTimeMillis());
                content.setCreatedTime(content.getParsingEndTime());

                if (text.length() > MAX_INPUT_FILE_TO_STORE) {
                    StringParserInput br = new StringParserInput(null, contentType);
                    content.setInput(br);
                }

                return content;
            case MICROSOFT_WORD:
            case MICROSOFT_EXCEL:
            case MICROSOFT_POWERPOINT:
            case PDF:
            case RTF:
            case ODF_TEXT:
            case ODF_SPREADSHEET:
            case ODF_PRESENTATION:
            case SQLITE:
            case ACCESS:
                if (!(input instanceof ByteArrayParserInput param)) {
                    throw new RuntimeException("Wrong type of ParserInput to parse content %s".formatted(contentType));
                }

                ParseResult parsingResult = TextContentParser.parse(param.getInput(), contentType);
                content.setParsedContent(parsingResult.getContent());
                content.setMetadata(parsingResult.getMetadata());

                content.setParsingEndTime(System.currentTimeMillis());
                content.setCreatedTime(content.getParsingEndTime());

                if (param.getInput().length > MAX_INPUT_FILE_TO_STORE) {
                    ByteArrayParserInput br = new ByteArrayParserInput(null, null, ContentType.PDF);
                    content.setInput(br);
                }

                return content;
        }

        throw new RuntimeException("Parser is not found for [%s]".formatted(input.contentType));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "contentType"
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = YoutubeIdParserInput.class, name = "YOUTUBE_TRANSCRIPT"),
            @JsonSubTypes.Type(value = ByteArrayParserInput.class, name = "MICROSOFT_WORD"),
            @JsonSubTypes.Type(value = ByteArrayParserInput.class, name = "MICROSOFT_EXCEL"),
            @JsonSubTypes.Type(value = ByteArrayParserInput.class, name = "MICROSOFT_POWERPOINT"),
            @JsonSubTypes.Type(value = ByteArrayParserInput.class, name = "PDF"),
            @JsonSubTypes.Type(value = ByteArrayParserInput.class, name = "RTF"),
            @JsonSubTypes.Type(value = StringParserInput.class, name = "TEXT"),
            @JsonSubTypes.Type(value = StringParserInput.class, name = "HTML"),
            @JsonSubTypes.Type(value = StringParserInput.class, name = "XML"),
            @JsonSubTypes.Type(value = ByteArrayParserInput.class, name = "ODF_TEXT"),
            @JsonSubTypes.Type(value = ByteArrayParserInput.class, name = "ODF_SPREADSHEET"),
            @JsonSubTypes.Type(value = ByteArrayParserInput.class, name = "ODF_PRESENTATION"),
            @JsonSubTypes.Type(value = ByteArrayParserInput.class, name = "SQLITE"),
            @JsonSubTypes.Type(value = ByteArrayParserInput.class, name = "PNG"),
            @JsonSubTypes.Type(value = ByteArrayParserInput.class, name = "JPG"),
            @JsonSubTypes.Type(value = ByteArrayParserInput.class, name = "ACCESS")
    })
    public static class ParserInput<T> {
        T input;
        ContentType contentType;
    }

    @Data
    @NoArgsConstructor
    public static class ByteArrayParserInput extends ParserInput<byte[]> {

        private String fileName;

        public ByteArrayParserInput(byte[] input, String fileName, ContentType contentType) {
            super(input, contentType);
            this.fileName = fileName;
        }

    }

    @Data
    @NoArgsConstructor
    public static class StringParserInput extends ParserInput<String> {

        public StringParserInput(String input, ContentType contentType) {
            super(input, contentType);
        }
    }

    @Data
    @NoArgsConstructor
    public static class StringListParserInput extends ParserInput<List<String>> {

        public StringListParserInput(List<String> input, ContentType contentType) {
            super(input, contentType);
        }
    }

    @Data
    @NoArgsConstructor
    public static class YoutubeIdParserInput extends ParserInput<List<String>> {
        String videoId;
        String primaryLang;

        public YoutubeIdParserInput(String videoId, List<String> languages) {
            super(languages, ContentType.YOUTUBE_TRANSCRIPT);
            this.videoId = videoId;
            this.primaryLang = languages.get(0);

            if (languages.size() > 1) {
                setInput(languages.subList(1, languages.size()));
            }
        }
    }
}