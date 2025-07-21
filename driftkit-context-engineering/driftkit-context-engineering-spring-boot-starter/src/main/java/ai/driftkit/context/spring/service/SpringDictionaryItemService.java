package ai.driftkit.context.spring.service;

import ai.driftkit.common.domain.DictionaryItem;
import ai.driftkit.common.domain.Language;
import ai.driftkit.context.core.service.DictionaryItemService;
import ai.driftkit.context.spring.domain.DictionaryItemDocument;
import ai.driftkit.context.spring.repository.SpringDictionaryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Spring implementation of DictionaryItemService.
 * This service acts as an adapter between the core business layer
 * and the Spring Data MongoDB repository layer.
 */
@Service
@RequiredArgsConstructor
public class SpringDictionaryItemService implements DictionaryItemService {

    private final SpringDictionaryItemRepository repository;

    @Override
    public Optional<DictionaryItem> findById(String id) {
        return repository.findById(id).map(doc -> (DictionaryItem) doc);
    }

    @Override
    public List<DictionaryItem> findByLanguage(Language language) {
        return repository.findDictionaryItemsByLanguage(language)
                .stream()
                .map(doc -> (DictionaryItem) doc)
                .collect(Collectors.toList());
    }

    @Override
    public List<DictionaryItem> findByGroupId(String groupId) {
        return repository.findDictionaryItemsByGroupId(groupId)
                .stream()
                .map(doc -> (DictionaryItem) doc)
                .collect(Collectors.toList());
    }

    @Override
    public DictionaryItem save(DictionaryItem item) {
        DictionaryItemDocument document = convertToDocument(item);
        DictionaryItemDocument saved = repository.save(document);
        return saved;
    }

    @Override
    public List<DictionaryItem> saveAll(List<DictionaryItem> items) {
        List<DictionaryItemDocument> documents = items.stream()
                .map(this::convertToDocument)
                .collect(Collectors.toList());
        
        List<DictionaryItemDocument> saved = repository.saveAll(documents);
        
        return saved.stream()
                .map(doc -> (DictionaryItem) doc)
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
    public List<DictionaryItem> findAll() {
        return repository.findAll()
                .stream()
                .map(doc -> (DictionaryItem) doc)
                .collect(Collectors.toList());
    }

    private DictionaryItemDocument convertToDocument(DictionaryItem item) {
        if (item instanceof DictionaryItemDocument) {
            return (DictionaryItemDocument) item;
        }
        
        // If it's a plain DictionaryItem, we need to convert it to a document
        DictionaryItemDocument document = new DictionaryItemDocument();
        document.setId(item.getId());
        document.setGroupId(item.getGroupId());
        document.setLanguage(item.getLanguage());
        document.setSamples(item.getSamples());
        document.setMarkers(item.getMarkers());
        document.setCreatedAt(item.getCreatedAt());
        document.setUpdatedAt(item.getUpdatedAt());
        
        return document;
    }
}