package ai.driftkit.context.spring.repository;

import ai.driftkit.common.domain.Language;
import ai.driftkit.context.core.service.DictionaryItemRepository;
import ai.driftkit.context.spring.domain.DictionaryItemDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDictionaryItemRepository extends MongoRepository<DictionaryItemDocument, String>, DictionaryItemRepository<DictionaryItemDocument> {
    
    @Override
    List<DictionaryItemDocument> findDictionaryItemsByLanguage(Language language);
    
    @Override
    List<DictionaryItemDocument> findDictionaryItemsByGroupId(String groupId);
}