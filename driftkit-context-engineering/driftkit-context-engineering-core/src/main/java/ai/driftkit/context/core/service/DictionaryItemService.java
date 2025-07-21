package ai.driftkit.context.core.service;

import ai.driftkit.common.domain.DictionaryItem;
import ai.driftkit.common.domain.Language;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing dictionary items.
 * This interface defines business operations for dictionary items,
 * independent of the underlying storage implementation.
 */
public interface DictionaryItemService {

    /**
     * Finds a dictionary item by its ID
     * 
     * @param id the ID of the dictionary item
     * @return an Optional containing the dictionary item if found, empty otherwise
     */
    Optional<DictionaryItem> findById(String id);

    /**
     * Finds dictionary items by language
     * 
     * @param language the language to filter by
     * @return a list of dictionary items matching the language
     */
    List<DictionaryItem> findByLanguage(Language language);

    /**
     * Finds dictionary items by group ID
     * 
     * @param groupId the group ID to filter by
     * @return a list of dictionary items belonging to the specified group
     */
    List<DictionaryItem> findByGroupId(String groupId);

    /**
     * Saves a dictionary item
     * 
     * @param item the dictionary item to save
     * @return the saved dictionary item
     */
    DictionaryItem save(DictionaryItem item);

    /**
     * Saves multiple dictionary items
     * 
     * @param items the dictionary items to save
     * @return the saved dictionary items
     */
    List<DictionaryItem> saveAll(List<DictionaryItem> items);

    /**
     * Deletes a dictionary item by ID
     * 
     * @param id the ID of the dictionary item to delete
     */
    void deleteById(String id);

    /**
     * Checks if a dictionary item exists by ID
     * 
     * @param id the ID to check
     * @return true if the item exists, false otherwise
     */
    boolean existsById(String id);

    /**
     * Finds all dictionary items
     * 
     * @return a list of all dictionary items
     */
    List<DictionaryItem> findAll();
}