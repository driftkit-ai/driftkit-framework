package ai.driftkit.context.spring.testsuite.controller;

import ai.driftkit.context.spring.testsuite.domain.TestSet;
import ai.driftkit.context.spring.testsuite.domain.TestSetItem;
import ai.driftkit.context.spring.testsuite.service.TestSetService;
import ai.driftkit.workflows.spring.domain.ModelRequestTrace;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/data/v1.0/admin/test-sets")
@RequiredArgsConstructor
public class TestSetController {

    private final TestSetService testSetService;

    @GetMapping
    public ResponseEntity<List<TestSet>> getAllTestSets() {
        List<TestSet> testSets = testSetService.getAllTestSets();
        return ResponseEntity.ok(testSets);
    }

    @GetMapping("/folder/{folderId}")
    public ResponseEntity<List<TestSet>> getTestSetsByFolder(@PathVariable String folderId) {
        List<TestSet> testSets = testSetService.getTestSetsByFolder(folderId);
        return ResponseEntity.ok(testSets);
    }

    @GetMapping("/folder")
    public ResponseEntity<List<TestSet>> getTestSetsWithoutFolder() {
        List<TestSet> testSets = testSetService.getTestSetsByFolder(null);
        return ResponseEntity.ok(testSets);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TestSet> getTestSetById(@PathVariable String id) {
        Optional<TestSet> testSet = testSetService.getTestSetById(id);
        return testSet.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/items")
    public ResponseEntity<List<TestSetItem>> getTestSetItems(@PathVariable String id) {
        List<TestSetItem> items = testSetService.getTestSetItems(id);
        return ResponseEntity.ok(items);
    }

    @PostMapping
    public ResponseEntity<TestSet> createTestSet(@RequestBody TestSet testSet) {
        TestSet created = testSetService.createTestSet(testSet);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TestSet> updateTestSet(@PathVariable String id, @RequestBody TestSet testSet) {
        Optional<TestSet> updated = testSetService.updateTestSet(id, testSet);
        return updated.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTestSet(@PathVariable String id) {
        if (testSetService.deleteTestSet(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<TestSetService.AddItemsResult> addItemsToTestSet(
            @PathVariable String id, 
            @RequestBody AddItemsRequest request) {
        
        TestSetService.AddItemsResult result = testSetService.addItemsToTestSet(
                request.getMessageTaskIds(),
                request.getTraceSteps() != null ? 
                    request.getTraceSteps().stream()
                        .map(step -> {
                            TestSetService.TraceStep traceStep = new TestSetService.TraceStep();
                            traceStep.setTraceId(step.getTraceId());
                            return traceStep;
                        }).toList() : null,
                request.getImageTaskIds(),
                id
        );
        
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{testSetId}/items/{itemId}")
    public ResponseEntity<Void> deleteTestSetItem(
            @PathVariable String testSetId, 
            @PathVariable String itemId) {
        
        if (testSetService.deleteTestSetItem(testSetId, itemId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/move-to-folder")
    public ResponseEntity<String> moveTestSetsToFolder(@RequestBody MoveToFolderRequest request) {
        boolean success = testSetService.moveTestSetsToFolder(request.getTestSetIds(), request.getFolderId());
        
        if (success) {
            return ResponseEntity.ok("TestSets moved successfully");
        } else {
            return ResponseEntity.badRequest().body("Failed to move TestSets");
        }
    }

    @Data
    public static class AddItemsRequest {
        private List<String> messageTaskIds;
        private List<TraceStep> traceSteps;
        private List<String> imageTaskIds;
    }

    @Data
    public static class TraceStep {
        private String traceId;
    }

    @Data
    public static class MoveToFolderRequest {
        private List<String> testSetIds;
        private String folderId;
    }
}