package ai.driftkit.rag.core.loader;

import ai.driftkit.rag.core.domain.LoadedDocument;
import ai.driftkit.vector.spring.parser.UnifiedParser;
import ai.driftkit.vector.spring.parser.UnifiedParser.ByteArrayParserInput;
import ai.driftkit.vector.spring.parser.UnifiedParser.StringParserInput;
import ai.driftkit.vector.spring.parser.UnifiedParser.ParserInput;
import ai.driftkit.vector.spring.domain.ContentType;
import ai.driftkit.vector.spring.domain.ParsedContent;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Document loader that fetches content from URLs.
 * Supports web pages, PDFs, and other content accessible via HTTP/HTTPS.
 */
@Slf4j
@Builder
@RequiredArgsConstructor
public class UrlLoader implements DocumentLoader {
    
    @NonNull
    private final List<String> urls;
    
    @NonNull
    private final UnifiedParser parser;
    
    @Builder.Default
    private final boolean followRedirects = true;
    
    @Builder.Default
    private final int timeoutSeconds = 30;
    
    @Builder.Default
    private final int maxContentSizeBytes = 50 * 1024 * 1024; // 50MB default
    
    @Builder.Default
    private final Map<String, String> headers = Map.of(
        "User-Agent", "DriftKit-RAG/1.0"
    );
    
    // Thread-safe HttpClient
    private static final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    
    /**
     * Custom ParserInput for URLs
     */
    @Data
    @NoArgsConstructor
    @JsonTypeName("URL")
    public static class UrlParserInput extends ParserInput<byte[]> {
        private String url;
        private String detectedMimeType;
        private Map<String, String> responseHeaders;
        
        public UrlParserInput(byte[] content, String url, ContentType contentType, 
                              String detectedMimeType, Map<String, String> responseHeaders) {
            super(content, contentType);
            this.url = url;
            this.detectedMimeType = detectedMimeType;
            this.responseHeaders = responseHeaders;
        }
    }
    
    /**
     * Load all documents from the configured URLs.
     */
    @Override
    public List<LoadedDocument> load() throws Exception {
        log.info("Loading {} URLs", urls.size());
        
        return urls.stream()
            .map(this::loadUrl)
            .toList();
    }
    
    /**
     * Load documents as a stream for memory-efficient processing.
     */
    @Override
    public Stream<LoadedDocument> loadStream() throws Exception {
        return urls.stream()
            .map(this::loadUrl);
    }
    
    /**
     * This loader supports streaming.
     */
    @Override
    public boolean supportsStreaming() {
        return true;
    }
    
    /**
     * Load content from a single URL.
     */
    private LoadedDocument loadUrl(String urlString) {
        try {
            log.debug("Loading URL: {}", urlString);
            
            // Build HTTP request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET();
            
            // Add custom headers
            headers.forEach(requestBuilder::header);
            
            HttpRequest request = requestBuilder.build();
            
            // Send request
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // Get content type from response
                String contentTypeHeader = response.headers().firstValue("Content-Type")
                    .orElse("text/html");
                
                // Extract MIME type (remove charset and other parameters)
                String mimeType = contentTypeHeader.split(";")[0].trim().toLowerCase();
                
                // Map response headers
                Map<String, String> responseHeaders = new HashMap<>();
                response.headers().map().forEach((key, values) -> {
                    if (!values.isEmpty()) {
                        responseHeaders.put(key, values.get(0));
                    }
                });
                
                byte[] content = response.body();
                
                // Check content size
                if (content.length > maxContentSizeBytes) {
                    log.warn("Content from {} exceeds size limit: {} bytes", urlString, content.length);
                    return createErrorDocument(urlString, response.statusCode(), 
                        new IOException("Content size exceeds limit: " + content.length + " bytes"));
                }
                
                // Determine ContentType enum based on MIME type
                ContentType contentType = determineContentType(mimeType);
                
                // Create appropriate parser input
                ParserInput<?> parserInput;
                
                if (contentType == ContentType.HTML || contentType == ContentType.TEXT || 
                    contentType == ContentType.XML) {
                    // For text-based content, convert to string
                    String textContent = new String(content, getCharset(contentTypeHeader));
                    parserInput = new StringParserInput(textContent, contentType);
                } else {
                    // For binary content (PDF, images, etc.)
                    parserInput = new ByteArrayParserInput(content, urlString, contentType);
                }
                
                // Parse using UnifiedParser
                ParsedContent parsed = parser.parse(parserInput);
                
                // Build metadata
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("url", urlString);
                metadata.put("httpStatus", response.statusCode());
                metadata.put("contentLength", content.length);
                metadata.put("contentType", contentTypeHeader);
                metadata.put("mimeType", mimeType);
                metadata.put("responseHeaders", responseHeaders);
                
                // Add parsing metadata if available
                if (parsed.getMetadata() != null) {
                    metadata.put("parsingMetadata", parsed.getMetadata());
                }
                metadata.put("parsingTime", parsed.getParsingEndTime() - parsed.getParsingStatedTime());
                
                return LoadedDocument.builder()
                    .id(parsed.getId())
                    .content(parsed.getParsedContent())
                    .source(urlString)
                    .mimeType(mimeType)
                    .metadata(metadata)
                    .state(LoadedDocument.State.LOADED)
                    .build();
                    
            } else {
                log.error("Failed to load URL: {} - HTTP {}", urlString, response.statusCode());
                return createErrorDocument(urlString, response.statusCode(), 
                    new IOException("HTTP error: " + response.statusCode()));
            }
            
        } catch (Exception e) {
            log.error("Failed to load URL: {}", urlString, e);
            return createErrorDocument(urlString, -1, e);
        }
    }
    
    /**
     * Determine ContentType enum from MIME type.
     */
    private ContentType determineContentType(String mimeType) {
        return switch (mimeType) {
            case "text/html", "application/xhtml+xml" -> ContentType.HTML;
            case "text/plain" -> ContentType.TEXT;
            case "application/xml", "text/xml" -> ContentType.XML;
            case "application/pdf" -> ContentType.PDF;
            case "image/jpeg", "image/jpg" -> ContentType.JPG;
            case "image/png" -> ContentType.PNG;
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/msword" -> ContentType.MICROSOFT_WORD;
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.ms-excel" -> ContentType.MICROSOFT_EXCEL;
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                 "application/vnd.ms-powerpoint" -> ContentType.MICROSOFT_POWERPOINT;
            default -> ContentType.TEXT; // Default fallback
        };
    }
    
    /**
     * Extract charset from Content-Type header.
     */
    private String getCharset(String contentType) {
        String[] parts = contentType.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("charset=")) {
                return trimmed.substring(8).replace("\"", "");
            }
        }
        return "UTF-8"; // Default charset
    }
    
    /**
     * Create a loader for a single URL.
     */
    public static UrlLoader fromUrl(String url, UnifiedParser parser) {
        return UrlLoader.builder()
            .urls(List.of(url))
            .parser(parser)
            .build();
    }
    
    /**
     * Create a loader for multiple URLs.
     */
    public static UrlLoader fromUrls(List<String> urls, UnifiedParser parser) {
        return UrlLoader.builder()
            .urls(urls)
            .parser(parser)
            .build();
    }
    
    /**
     * Create an error document when URL loading fails.
     * This allows tracking which URLs failed to load and why.
     */
    private LoadedDocument createErrorDocument(String urlString, int httpStatus, Exception error) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("url", urlString);
        if (httpStatus > 0) {
            metadata.put("httpStatus", httpStatus);
        }
        metadata.put("errorMessage", error.getMessage());
        metadata.put("errorType", error.getClass().getName());
        
        return LoadedDocument.builder()
            .id("error-" + urlString.hashCode())
            .content(null) // No content for error documents
            .source(urlString)
            .mimeType("application/octet-stream") // Unknown mime type
            .metadata(metadata)
            .state(LoadedDocument.State.ERROR)
            .build();
    }
}