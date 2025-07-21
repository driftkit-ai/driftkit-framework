package ai.driftkit.context.core.service;

import ai.driftkit.common.domain.DictionaryGroup;
import ai.driftkit.common.domain.Language;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for custom queries on dictionary groups.
 * This interface contains only custom query methods, not CRUD operations.
 * CRUD operations are handled by the DictionaryGroupService layer.
 */
public interface DictionaryGroupRepository<T extends DictionaryGroup> {

    /**
     * Finds dictionary groups by language
     * 
     * @param language the language to filter by
     * @return a list of dictionary groups matching the language
     */
    List<T> findDictionaryGroupsByLanguage(Language language);

}