package ai.driftkit.context.core.service;

import ai.driftkit.common.domain.DictionaryGroup;
import ai.driftkit.common.domain.Language;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing dictionary groups.
 * This interface defines business operations for dictionary groups,
 * independent of the underlying storage implementation.
 */
public interface DictionaryGroupService {

    /**
     * Finds a dictionary group by its ID
     * 
     * @param id the ID of the dictionary group
     * @return an Optional containing the dictionary group if found, empty otherwise
     */
    Optional<DictionaryGroup> findById(String id);

    /**
     * Finds dictionary groups by language
     * 
     * @param language the language to filter by
     * @return a list of dictionary groups matching the language
     */
    List<DictionaryGroup> findByLanguage(Language language);

    /**
     * Saves a dictionary group
     * 
     * @param group the dictionary group to save
     * @return the saved dictionary group
     */
    DictionaryGroup save(DictionaryGroup group);

    /**
     * Saves multiple dictionary groups
     * 
     * @param groups the dictionary groups to save
     * @return the saved dictionary groups
     */
    List<DictionaryGroup> saveAll(List<DictionaryGroup> groups);

    /**
     * Deletes a dictionary group by ID
     * 
     * @param id the ID of the dictionary group to delete
     */
    void deleteById(String id);

    /**
     * Checks if a dictionary group exists by ID
     * 
     * @param id the ID to check
     * @return true if the group exists, false otherwise
     */
    boolean existsById(String id);

    /**
     * Finds all dictionary groups
     * 
     * @return a list of all dictionary groups
     */
    List<DictionaryGroup> findAll();
}