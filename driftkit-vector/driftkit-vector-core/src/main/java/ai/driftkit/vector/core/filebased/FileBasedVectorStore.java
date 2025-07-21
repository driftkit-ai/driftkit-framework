package ai.driftkit.vector.core.filebased;

import ai.driftkit.config.EtlConfig.VectorStoreConfig;
import ai.driftkit.vector.core.domain.Document;
import ai.driftkit.vector.core.domain.VectorStore;
import ai.driftkit.vector.core.inmemory.InMemoryVectorStore;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FileBasedVectorStore extends InMemoryVectorStore {

    private String configPath;

    public boolean supportsStoreName(String storeName) {
        return "filebased".equalsIgnoreCase(storeName);
    }

    @Override
    public void configure(VectorStoreConfig config) throws Exception {
        this.configPath = config.get("storageFile");
        loadFromDisk();
    }

    private void loadFromDisk() {
        File file = new File(configPath);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                documentMap = (ConcurrentHashMap<String, Map<String, Document>>) ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException("Failed to load vector store from disk", e);
            }
        } else {
            documentMap = new ConcurrentHashMap<>();
        }
    }

    private void saveToDisk() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(configPath))) {
            oos.writeObject(documentMap);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save vector store to disk", e);
        }
    }

    public List<String> addDocuments(String indexName, List<Document> documents) {
        List<String> ids = new ArrayList<>();
        for (Document doc : documents) {
            String id = doc.getId();
            if (id == null || id.isEmpty()) {
                id = UUID.randomUUID().toString();
                doc.setId(id);
            }

            Map<String, Document> index = getIndexOrCreate(indexName);

            index.put(id, doc);
            ids.add(id);
        }
        saveToDisk();
        return ids;
    }

    @NotNull
    private Map<String, Document> getIndexOrCreate(String indexName) {
        return documentMap.computeIfAbsent(indexName, e -> new ConcurrentHashMap<>());
    }
}
