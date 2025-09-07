package ai.driftkit.rag.spring.service;

import ai.driftkit.rag.core.domain.LoadedDocument;
import ai.driftkit.rag.core.loader.DocumentLoader;
import ai.driftkit.rag.core.loader.FileSystemLoader;
import ai.driftkit.rag.core.loader.UrlLoader;
import ai.driftkit.vector.spring.parser.UnifiedParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Factory for creating DocumentLoader instances at runtime.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentLoaderFactory {
    
    private final UnifiedParser parser;
    
    /**
     * Create a FileSystemLoader for the given path.
     */
    public FileSystemLoader fileSystemLoader(String path) {
        return fileSystemLoader(Paths.get(path));
    }
    
    /**
     * Create a FileSystemLoader with custom configuration.
     */
    public FileSystemLoader fileSystemLoader(Path path) {
        log.debug("Creating FileSystemLoader for path: {}", path);
        
        return FileSystemLoader.builder()
            .rootPath(path)
            .parser(parser)
            .build();
    }
    
    /**
     * Create a FileSystemLoader with full configuration.
     */
    public FileSystemLoader fileSystemLoader(
            Path path,
            boolean recursive,
            Set<String> extensions,
            Set<String> excludePatterns) {
        
        log.debug("Creating configured FileSystemLoader for path: {}", path);
        
        return FileSystemLoader.builder()
            .rootPath(path)
            .parser(parser)
            .recursive(recursive)
            .extensions(extensions)
            .excludePatterns(excludePatterns)
            .build();
    }
    
    /**
     * Create a UrlLoader for a single URL.
     */
    public UrlLoader urlLoader(String url) {
        return urlLoader(List.of(url));
    }
    
    /**
     * Create a UrlLoader for multiple URLs.
     */
    public UrlLoader urlLoader(List<String> urls) {
        log.debug("Creating UrlLoader for {} URLs", urls.size());
        
        return UrlLoader.builder()
            .urls(urls)
            .parser(parser)
            .build();
    }
    
    /**
     * Create a UrlLoader with custom configuration.
     */
    public UrlLoader urlLoader(
            List<String> urls,
            Map<String, String> headers,
            int timeoutSeconds) {
        
        log.debug("Creating configured UrlLoader for {} URLs", urls.size());
        
        return UrlLoader.builder()
            .urls(urls)
            .parser(parser)
            .headers(headers)
            .timeoutSeconds(timeoutSeconds)
            .build();
    }
    
    /**
     * Create a composite loader that combines multiple loaders.
     */
    public DocumentLoader compositeLoader(DocumentLoader... loaders) {
        return new CompositeDocumentLoader(List.of(loaders));
    }
    
    /**
     * Composite loader that aggregates multiple loaders.
     */
    @RequiredArgsConstructor
    private static class CompositeDocumentLoader implements DocumentLoader {
        private final List<DocumentLoader> loaders;
        
        @Override
        public List<LoadedDocument> load() throws Exception {
            List<LoadedDocument> allDocs = new ArrayList<>();
            for (DocumentLoader loader : loaders) {
                allDocs.addAll(loader.load());
            }
            return allDocs;
        }
        
        @Override
        public Stream<LoadedDocument> loadStream() throws Exception {
            return loaders.stream()
                .flatMap(loader -> {
                    try {
                        return loader.loadStream();
                    } catch (Exception e) {
                        log.error("Error loading from loader", e);
                        return Stream.empty();
                    }
                });
        }
        
        @Override
        public boolean supportsStreaming() {
            return loaders.stream().allMatch(DocumentLoader::supportsStreaming);
        }
    }
}