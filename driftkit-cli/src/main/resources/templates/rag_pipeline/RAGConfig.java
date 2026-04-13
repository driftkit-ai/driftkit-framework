package /*PACKAGE_NAME*/.config;

import ai.driftkit.common.service.DocumentSplitter;
import ai.driftkit.rag.retriever.VectorStoreRetriever;
import ai.driftkit.vector.VectorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import lombok.Data;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;

@Configuration
@EnableConfigurationProperties(RAGConfig.DocumentStorageProperties.class)
@RequiredArgsConstructor
public class RAGConfig {
    
    private final DocumentStorageProperties documentStorageProperties;
    
    @Bean
    public DocumentSplitter documentSplitter() {
        return new DocumentSplitter();
    }
    
    @Bean
    public VectorStoreRetriever vectorStoreRetriever(VectorStore vectorStore) {
        return new VectorStoreRetriever(vectorStore);
    }
    
    @Bean
    public Path documentStoragePath() throws IOException {
        Path storagePath = Paths.get(documentStorageProperties.getPath());
        if (documentStorageProperties.isCreateIfMissing() && !Files.exists(storagePath)) {
            Files.createDirectories(storagePath);
        }
        return storagePath;
    }
    
    @Data
    @ConfigurationProperties(prefix = "document.storage")
    public static class DocumentStorageProperties {
        private String path = "./documents";
        private boolean createIfMissing = true;
    }
}