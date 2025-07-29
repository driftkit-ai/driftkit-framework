package ai.driftkit.workflows.spring.repository;

import ai.driftkit.workflows.spring.domain.ChatEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepository extends MongoRepository<ChatEntity, String> {

    List<ChatEntity> findChatsByHiddenIsFalse();

    List<ChatEntity> findChatsByHiddenIsFalse(Pageable pageable);

}