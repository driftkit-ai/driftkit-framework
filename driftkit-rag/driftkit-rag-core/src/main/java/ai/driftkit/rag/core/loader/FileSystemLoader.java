package ai.driftkit.rag.core.loader;

import ai.driftkit.rag.core.domain.LoadedDocument;
import ai.driftkit.vector.spring.parser.UnifiedParser;
import ai.driftkit.vector.spring.parser.UnifiedParser.ByteArrayParserInput;
import ai.driftkit.vector.spring.domain.ContentType;
import ai.driftkit.vector.spring.domain.ParsedContent;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Document loader that reads files from the file system.
 * Supports various file types including text, PDF, images, etc. via UnifiedParser.
 */
@Slf4j
@Builder
@RequiredArgsConstructor
public class FileSystemLoader implements DocumentLoader {
    
    @NonNull
    private final Path rootPath;
    
    @NonNull
    private final UnifiedParser parser;
    
    @Builder.Default
    private final boolean recursive = true;
    
    @Builder.Default
    private final Set<String> extensions = Set.of(
        // Text formats
        "txt", "md", "json", "xml", "html", "csv",
        // Document formats
        "pdf", "doc", "docx", "odt",
        // Image formats
        "jpg", "jpeg", "png", "gif", "bmp",
        // Audio/Video formats (if transcription is needed)
        "mp3", "wav", "mp4", "avi"
    );
    
    @Builder.Default
    private final Set<String> excludePatterns = Set.of();
    
    @Builder.Default
    private final long maxFileSizeBytes = 50 * 1024 * 1024; // 50MB default
    
    @Builder.Default
    private final boolean includeHidden = false;
    
    /**
     * Map file extensions to ContentType.
     */
    private static final Map<String, ContentType> EXTENSION_TO_CONTENT_TYPE = Map.ofEntries(
        // Text formats
        Map.entry("txt", ContentType.TEXT),
        Map.entry("md", ContentType.TEXT),
        Map.entry("json", ContentType.TEXT),
        Map.entry("xml", ContentType.XML),
        Map.entry("html", ContentType.HTML),
        Map.entry("csv", ContentType.TEXT),
        // Document formats
        Map.entry("pdf", ContentType.PDF),
        Map.entry("doc", ContentType.MICROSOFT_WORD),
        Map.entry("docx", ContentType.MICROSOFT_WORD),
        Map.entry("odt", ContentType.ODF_TEXT),
        // Image formats
        Map.entry("jpg", ContentType.JPG),
        Map.entry("jpeg", ContentType.JPG),
        Map.entry("png", ContentType.PNG),
        Map.entry("gif", ContentType.PNG), // UnifiedParser will handle as image
        Map.entry("bmp", ContentType.PNG)  // UnifiedParser will handle as image
    );
    
    /**
     * Load all documents from the file system.
     */
    @Override
    public List<LoadedDocument> load() throws Exception {
        log.info("Loading documents from: {}", rootPath);
        
        List<LoadedDocument> documents = new ArrayList<>();
        
        try (Stream<LoadedDocument> stream = loadStream()) {
            stream.forEach(documents::add);
        }
        
        log.info("Loaded {} documents", documents.size());
        return documents;
    }
    
    /**
     * Load documents as a stream for memory-efficient processing.
     */
    @Override
    public Stream<LoadedDocument> loadStream() throws Exception {
        if (!Files.exists(rootPath)) {
            throw new IOException("Path does not exist: " + rootPath);
        }
        
        if (Files.isRegularFile(rootPath)) {
            // Single file
            return Stream.of(loadFile(rootPath));
        } else if (Files.isDirectory(rootPath)) {
            // Directory
            return loadDirectory();
        } else {
            throw new IOException("Path is neither file nor directory: " + rootPath);
        }
    }
    
    /**
     * This loader supports streaming.
     */
    @Override
    public boolean supportsStreaming() {
        return true;
    }
    
    /**
     * Load documents from a directory.
     */
    private Stream<LoadedDocument> loadDirectory() throws IOException {
        int maxDepth = recursive ? Integer.MAX_VALUE : 1;
        
        return Files.walk(rootPath, maxDepth)
            .filter(Files::isRegularFile)
            .filter(this::shouldIncludeFile)
            .map(path -> {
                try {
                    return loadFile(path);
                } catch (Exception e) {
                    log.error("Failed to load file: {}", path, e);
                    return null;
                }
            })
            .filter(Objects::nonNull);
    }
    
    /**
     * Check if a file should be included based on filters.
     */
    private boolean shouldIncludeFile(Path path) {
        String fileName = path.getFileName().toString();
        
        // Check hidden files
        if (!includeHidden && fileName.startsWith(".")) {
            return false;
        }
        
        // Check extension
        String extension = FilenameUtils.getExtension(fileName).toLowerCase();
        if (!extensions.isEmpty() && !extensions.contains(extension)) {
            return false;
        }
        
        // Check exclude patterns
        String pathStr = path.toString();
        for (String pattern : excludePatterns) {
            if (pathStr.contains(pattern)) {
                return false;
            }
        }
        
        // Check file size
        try {
            long size = Files.size(path);
            if (size > maxFileSizeBytes) {
                log.warn("Skipping file {} - size {} exceeds limit {}", path, size, maxFileSizeBytes);
                return false;
            }
        } catch (IOException e) {
            log.warn("Failed to check file size: {}", path, e);
            return false;
        }
        
        return true;
    }
    
    /**
     * Load a single file using UnifiedParser.
     */
    private LoadedDocument loadFile(Path path) throws IOException {
        log.trace("Loading file: {}", path);
        
        String fileName = path.getFileName().toString();
        String extension = FilenameUtils.getExtension(fileName).toLowerCase();
        
        // Determine content type
        ContentType contentType = EXTENSION_TO_CONTENT_TYPE.getOrDefault(extension, ContentType.TEXT);
        
        // Read file as bytes
        byte[] fileBytes = Files.readAllBytes(path);
        
        // Create parser input
        ByteArrayParserInput parserInput = new ByteArrayParserInput(fileBytes, fileName, contentType);
        
        // Parse using UnifiedParser
        ParsedContent parsed = parser.parse(parserInput);
        
        // Build metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", fileName);
        metadata.put("filePath", path.toString());
        metadata.put("fileSize", Files.size(path));
        metadata.put("lastModified", Files.getLastModifiedTime(path).toMillis());
        metadata.put("extension", extension);
        metadata.put("contentType", contentType.name());
        
        // Add parsing metadata if available
        if (parsed.getMetadata() != null) {
            metadata.put("parsingMetadata", parsed.getMetadata());
        }
        metadata.put("parsingTime", parsed.getParsingEndTime() - parsed.getParsingStatedTime());
        
        return LoadedDocument.builder()
            .id(parsed.getId())
            .content(parsed.getParsedContent())
            .source(path.toString())
            .mimeType(contentType.getMimeType())
            .metadata(metadata)
            .build();
    }
}