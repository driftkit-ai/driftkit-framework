package ai.driftkit.context.services;

import ai.driftkit.common.domain.DictionaryGroup;
import ai.driftkit.common.domain.Language;
import ai.driftkit.context.core.service.DictionaryGroupService;
import ai.driftkit.context.services.domain.DictionaryGroupDocument;
import ai.driftkit.context.services.repository.SpringDictionaryGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Spring implementation of DictionaryGroupService.
 * This service acts as an adapter between the core business layer
 * and the Spring Data MongoDB repository layer.
 */
@Service
@RequiredArgsConstructor
public class SpringDictionaryGroupService implements DictionaryGroupService {

    private final SpringDictionaryGroupRepository repository;

    @Override
    public Optional<DictionaryGroup> findById(String id) {
        return repository.findById(id).map(doc -> (DictionaryGroup) doc);
    }

    @Override
    public List<DictionaryGroup> findByLanguage(Language language) {
        return repository.findDictionaryGroupsByLanguage(language)
                .stream()
                .map(doc -> (DictionaryGroup) doc)
                .collect(Collectors.toList());
    }

    @Override
    public DictionaryGroup save(DictionaryGroup group) {
        DictionaryGroupDocument document = convertToDocument(group);
        DictionaryGroupDocument saved = repository.save(document);
        return saved;
    }

    @Override
    public List<DictionaryGroup> saveAll(List<DictionaryGroup> groups) {
        List<DictionaryGroupDocument> documents = groups.stream()
                .map(this::convertToDocument)
                .collect(Collectors.toList());
        
        List<DictionaryGroupDocument> saved = repository.saveAll(documents);
        
        return saved.stream()
                .map(doc -> (DictionaryGroup) doc)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }

    @Override
    public boolean existsById(String id) {
        return repository.existsById(id);
    }

    @Override
    public List<DictionaryGroup> findAll() {
        return repository.findAll()
                .stream()
                .map(doc -> (DictionaryGroup) doc)
                .collect(Collectors.toList());
    }

    private DictionaryGroupDocument convertToDocument(DictionaryGroup group) {
        if (group instanceof DictionaryGroupDocument) {
            return (DictionaryGroupDocument) group;
        }
        
        // If it's a plain DictionaryGroup, we need to convert it to a document
        DictionaryGroupDocument document = new DictionaryGroupDocument();
        document.setId(group.getId());
        document.setName(group.getName());
        document.setLanguage(group.getLanguage());
        document.setCreatedAt(group.getCreatedAt());
        document.setUpdatedAt(group.getUpdatedAt());
        
        return document;
    }
}