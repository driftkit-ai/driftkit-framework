package ai.driftkit.workflows.spring.repository;

import ai.driftkit.common.domain.ImageMessageTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImageTaskRepository extends MongoRepository<ImageMessageTask, String> {
    Page<ImageMessageTask> findByChatId(String chatId, Pageable pageable);
    
    List<ImageMessageTask> findAllByMessageIdIn(List<String> messageIds);
}