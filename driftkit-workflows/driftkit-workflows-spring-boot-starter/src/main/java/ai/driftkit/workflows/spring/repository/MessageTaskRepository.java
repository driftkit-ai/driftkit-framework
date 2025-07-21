package ai.driftkit.workflows.spring.repository;

import ai.driftkit.common.domain.MessageTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageTaskRepository extends MongoRepository<MessageTask, String> {
    @Query("{ 'checkerResponse.fixes': { $exists: true, $not: { $size: 0 } } }")
    List<MessageTask> findMessageTasksWithFixes(Pageable pageable);

    Page<MessageTask> findByChatId(String chatId, Pageable pageable);
    
    List<MessageTask> findAllByMessageIdIn(List<String> messageIds);
}