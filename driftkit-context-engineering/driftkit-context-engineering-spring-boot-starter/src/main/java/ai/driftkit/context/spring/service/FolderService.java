package ai.driftkit.context.spring.service;

import ai.driftkit.context.spring.testsuite.domain.Folder;
import ai.driftkit.context.spring.testsuite.domain.FolderType;
import ai.driftkit.context.spring.testsuite.domain.TestSet;
import ai.driftkit.context.spring.testsuite.repository.FolderRepository;
import ai.driftkit.context.spring.testsuite.repository.TestSetRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final TestSetRepository testSetRepository;

    public List<Folder> getAllFolders() {
        return folderRepository.findAllByOrderByCreatedAtDesc();
    }
    
    public List<Folder> getFoldersByType(FolderType type) {
        return folderRepository.findByTypeOrderByCreatedAtDesc(type);
    }
    
    public Optional<Folder> getFolderById(String id) {
        return folderRepository.findById(id);
    }
    
    public Folder createFolder(Folder folder) {
        folder.setId(null);
        folder.setCreatedAt(System.currentTimeMillis());
        return folderRepository.save(folder);
    }
    
    public Optional<Folder> updateFolder(String id, Folder folder) {
        if (!folderRepository.existsById(id)) {
            return Optional.empty();
        }
        folder.setId(id);
        return Optional.of(folderRepository.save(folder));
    }
    
    public boolean deleteFolder(String id) {
        if (!folderRepository.existsById(id)) {
            return false;
        }
        // Remove folder reference from all test sets in this folder
        List<TestSet> testSets = testSetRepository.findAll();
        for (TestSet testSet : testSets) {
            if (id.equals(testSet.getFolderId())) {
                testSet.setFolderId(null);
                testSetRepository.save(testSet);
            }
        }
        folderRepository.deleteById(id);
        return true;
    }
    
    // This method has been moved to TestSetService for better service separation
    // Method removed
}