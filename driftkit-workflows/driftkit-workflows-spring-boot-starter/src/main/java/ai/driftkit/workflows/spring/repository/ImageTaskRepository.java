package ai.driftkit.workflows.spring.repository;

import ai.driftkit.workflows.spring.domain.ImageMessageTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImageTaskRepository extends MongoRepository<ImageMessageTaskEntity, String> {
    Page<ImageMessageTaskEntity> findByChatId(String chatId, Pageable pageable);
    
    List<ImageMessageTaskEntity> findAllByMessageIdIn(List<String> messageIds);
}