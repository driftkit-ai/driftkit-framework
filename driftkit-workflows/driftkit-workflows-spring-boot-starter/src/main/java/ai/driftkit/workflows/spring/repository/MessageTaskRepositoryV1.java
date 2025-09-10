package ai.driftkit.workflows.spring.repository;

import ai.driftkit.workflows.spring.domain.MessageTaskEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Primary
public interface MessageTaskRepositoryV1 extends MongoRepository<MessageTaskEntity, String> {
    @Query("{ 'checkerResponse.fixes': { $exists: true, $not: { $size: 0 } } }")
    List<MessageTaskEntity> findMessageTasksWithFixes(Pageable pageable);

    Page<MessageTaskEntity> findByChatId(String chatId, Pageable pageable);
    
    List<MessageTaskEntity> findAllByMessageIdIn(List<String> messageIds);
}