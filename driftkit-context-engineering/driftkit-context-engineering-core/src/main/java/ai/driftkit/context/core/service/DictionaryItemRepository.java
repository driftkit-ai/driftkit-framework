package ai.driftkit.context.core.service;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.DictionaryItem;

import java.util.List;

/**
 * Repository interface for custom queries on dictionary items.
 * This interface contains only custom query methods, not CRUD operations.
 * CRUD operations are handled by the DictionaryItemService layer.
 */
public interface DictionaryItemRepository<T extends DictionaryItem> {

    /**
     * Finds dictionary items by language
     * 
     * @param language the language to filter by
     * @return a list of dictionary items matching the language
     */
    List<T> findDictionaryItemsByLanguage(Language language);

    /**
     * Finds dictionary items by group ID
     * 
     * @param groupId the group ID to filter by
     * @return a list of dictionary items belonging to the specified group
     */
    List<T> findDictionaryItemsByGroupId(String groupId);

}