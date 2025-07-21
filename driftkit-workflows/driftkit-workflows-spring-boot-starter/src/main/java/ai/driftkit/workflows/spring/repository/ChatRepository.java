package ai.driftkit.workflows.spring.repository;

import ai.driftkit.common.domain.Chat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepository extends MongoRepository<Chat, String> {

    List<Chat> findChatsByHiddenIsFalse();

    List<Chat> findChatsByHiddenIsFalse(Pageable pageable);

}