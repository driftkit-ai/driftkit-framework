package ai.driftkit.context.spring.testsuite.repository;

import ai.driftkit.context.spring.testsuite.domain.Folder;
import ai.driftkit.context.spring.testsuite.domain.FolderType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FolderRepository extends MongoRepository<Folder, String> {
    List<Folder> findByTypeOrderByCreatedAtDesc(FolderType type);
    List<Folder> findAllByOrderByCreatedAtDesc();
}