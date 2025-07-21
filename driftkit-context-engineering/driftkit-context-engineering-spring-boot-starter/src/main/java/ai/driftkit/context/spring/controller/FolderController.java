package ai.driftkit.context.spring.controller;

import ai.driftkit.context.spring.service.FolderService;
import ai.driftkit.context.spring.testsuite.domain.Folder;
import ai.driftkit.context.spring.testsuite.domain.FolderType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/data/v1.0/admin/test-sets/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @GetMapping
    public ResponseEntity<List<Folder>> getAllFolders() {
        List<Folder> folders = folderService.getFoldersByType(FolderType.TEST_SET);
        return ResponseEntity.ok(folders);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Folder> getFolderById(@PathVariable String id) {
        return folderService.getFolderById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Folder> createFolder(@RequestBody Folder folder) {
        folder.setType(FolderType.TEST_SET); // Ensure it's a TestSet folder
        return ResponseEntity.ok(folderService.createFolder(folder));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Folder> updateFolder(@PathVariable String id, @RequestBody Folder folder) {
        folder.setType(FolderType.TEST_SET); // Ensure it's a TestSet folder
        return folderService.updateFolder(id, folder)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable String id) {
        if (folderService.deleteFolder(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}