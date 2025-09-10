package ai.driftkit.context.services.repository;

import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.Language;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromptRepository extends MongoRepository<Prompt, String> {

    List<Prompt> findByMethod(String method);

    List<Prompt> findByMethodIsIn(List<String> method);

    List<Prompt> findByMethodIsInAndState(List<String> method, Prompt.State state);

    List<Prompt> findByState(Prompt.State state);

    List<Prompt> findByMethodAndState(String method, Prompt.State state);

    Optional<Prompt> findByMethodAndLanguageAndState(String method, Language language, Prompt.State state);
}