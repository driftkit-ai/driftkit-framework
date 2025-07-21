package ai.driftkit.vector.spring.controller;

import ai.driftkit.vector.spring.domain.ContentType;
import ai.driftkit.vector.spring.domain.Index;
import ai.driftkit.vector.spring.domain.IndexTask;
import ai.driftkit.vector.spring.domain.IndexTask.TaskStatus;
import ai.driftkit.vector.spring.parser.UnifiedParser.ByteArrayParserInput;
import ai.driftkit.vector.spring.parser.UnifiedParser.ParserInput;
import ai.driftkit.vector.spring.parser.UnifiedParser.StringParserInput;
import ai.driftkit.vector.spring.parser.UnifiedParser.YoutubeIdParserInput;
import ai.driftkit.vector.spring.service.IndexService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping(path = "/data/v1.0/admin/index")
public class IndexController {

    @Autowired
    private IndexService indexService;

    @PostMapping(value = "/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IndexResponse> indexJson(@RequestBody IndexRequest indexRequest) {
        try {
            // No file here, purely JSON scenario
            String index = indexRequest.getIndex();
            if (index == null || index.isEmpty()) {
                return ResponseEntity.badRequest().body(null);
            }

            ParserInput parserInput;
            if (indexRequest.getVideoId() != null) {
                YoutubeIdParserInput youtube = new YoutubeIdParserInput();
                youtube.setContentType(ContentType.YOUTUBE_TRANSCRIPT);
                youtube.setInput(indexRequest.getInput());
                youtube.setVideoId(indexRequest.getVideoId());
                youtube.setPrimaryLang(indexRequest.getPrimaryLang());
                parserInput = youtube;
            } else {
                // fallback to text
                parserInput = new StringParserInput(indexRequest.getText(), ContentType.TEXT);
            }

            String taskId = indexService.submitIndexingTask(index, parserInput);
            return ResponseEntity.ok(new IndexResponse(taskId));

        } catch (Exception e) {
            log.error("Error submitting indexing task: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(null);
        }
    }

    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IndexResponse> indexMultipart(
            @RequestParam(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "index", required = false) String indexId
    ) {
        try {
            if (file == null || file.isEmpty() || indexId == null) {
                return ResponseEntity.badRequest().body(null);
            }

            ParserInput parserInput = new ByteArrayParserInput(
                    file.getBytes(),
                    Optional.ofNullable(file.getOriginalFilename()).orElse(file.getName()),
                    ContentType.fromString(file.getContentType())
            );

            String taskId = indexService.submitIndexingTask(indexId, parserInput);
            return ResponseEntity.ok(new IndexResponse(taskId));
        } catch (Exception e) {
            log.error("Error submitting indexing task: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping("/indexed/list")
    public ResponseEntity<List<IndexTask>> getIndexedIndexes(@RequestParam(required = false) Integer page) {
        Page<IndexTask> tasks = indexService.getTasks(Optional.ofNullable(page).orElse(0), 100);
        return ResponseEntity.ok(tasks.getContent());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Index> deleteIndex(@PathVariable String id) {
        indexService.deleteIndex(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/")
    public ResponseEntity<Index> saveIndex(@RequestBody Index index) {
        return ResponseEntity.ok(indexService.save(index));
    }

    @GetMapping("/list")
    public ResponseEntity<List<Index>> getIndexes() {
        return ResponseEntity.ok(indexService.getIndexList());
    }

    @GetMapping("/status/{taskId}")
    public ResponseEntity<TaskStatusResponse> getStatus(@PathVariable String taskId) {
        IndexTask task = indexService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new TaskStatusResponse(taskId, task.getStatus()));
    }

    @GetMapping("/result/{taskId}")
    public ResponseEntity<IndexTask> getResult(@PathVariable String taskId) {
        IndexTask task = indexService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(task);
    }

    @Data
    public static class IndexRequest {
        private String text;
        private String videoId;
        private String primaryLang;
        private List<String> input;
        private String index;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndexResponse {
        private String taskId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskStatusResponse {
        private String taskId;
        private TaskStatus status;
    }
}