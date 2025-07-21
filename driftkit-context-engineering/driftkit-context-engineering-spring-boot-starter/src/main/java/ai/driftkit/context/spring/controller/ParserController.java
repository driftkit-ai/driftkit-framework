package ai.driftkit.context.spring.controller;

import ai.driftkit.vector.spring.domain.ContentType;
import ai.driftkit.vector.spring.domain.ParsedContent;
import ai.driftkit.vector.spring.parser.UnifiedParser.ByteArrayParserInput;
import ai.driftkit.vector.spring.parser.UnifiedParser.YoutubeIdParserInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ai.driftkit.vector.spring.service.ParserService;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Controller
@RequestMapping(path = "/data/v1.0/admin/parse/")
public class ParserController {

    @Autowired
    private ParserService parserService;

    @Operation(summary = "Upload a file")
    @PostMapping(value = "/file", consumes = { "multipart/form-data" })
    public ResponseEntity<ParsedContent> uploadFile(
            @Parameter(description = "File to be uploaded", required = true)
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Boolean save,
            @RequestParam(required = false) Boolean metadata
    ) throws IOException {
        ByteArrayParserInput input = new ByteArrayParserInput(
                file.getBytes(),
                Optional.ofNullable(file.getOriginalFilename()).orElse(file.getName()),
                ContentType.fromString(file.getContentType())
        );
        ParsedContent result = parserService.parse(
                input,
                BooleanUtils.isNotFalse(save)
        );

        if (BooleanUtils.isNotTrue(metadata)) {
            result.setMetadata(null);
        }

        return ResponseEntity.ofNullable(result);
    }

    @PostMapping(value = "/youtube")
    public ResponseEntity<ParsedContent> youtube(
            @RequestBody YoutubeInput input,
            @RequestParam(required = false) Boolean save
    ) throws IOException {
        List<String> languages = input.getLanguages()
                .stream()
                    .flatMap(e -> Stream.of(e.split(",")))
                    .map(String::trim)
                    .collect(Collectors.toList());

        String primaryLanguage = languages.remove(0);

        YoutubeIdParserInput youtube = new YoutubeIdParserInput();
        youtube.setContentType(ContentType.YOUTUBE_TRANSCRIPT);

        youtube.setInput(languages);
        youtube.setVideoId(input.getVideoId());
        youtube.setPrimaryLang(primaryLanguage);

        ParsedContent result = parserService.parse(
                youtube,
                BooleanUtils.isNotFalse(save)
        );

        if (BooleanUtils.isNotTrue(input.getMetadata())) {
            result.setMetadata(null);
        }

        return ResponseEntity.ofNullable(result);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class YoutubeInput {
        List<String> languages;
        String videoId;
        Boolean metadata;
    }
}