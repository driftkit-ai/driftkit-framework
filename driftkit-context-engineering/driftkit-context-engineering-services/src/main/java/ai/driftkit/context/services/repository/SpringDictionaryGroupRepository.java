package ai.driftkit.context.services.repository;

import ai.driftkit.common.domain.DictionaryGroup;
import ai.driftkit.common.domain.Language;
import ai.driftkit.context.core.service.DictionaryGroupRepository;
import ai.driftkit.context.services.domain.DictionaryGroupDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDictionaryGroupRepository extends MongoRepository<DictionaryGroupDocument, String>, DictionaryGroupRepository<DictionaryGroupDocument> {
    
    @Override
    List<DictionaryGroupDocument> findDictionaryGroupsByLanguage(Language language);
}