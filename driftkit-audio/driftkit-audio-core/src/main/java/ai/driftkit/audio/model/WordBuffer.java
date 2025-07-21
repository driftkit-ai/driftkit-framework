package ai.driftkit.audio.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Buffer for managing and deduplicating words from streaming transcription results.
 * Handles overlapping word updates and maintains a consistent timeline.
 */
@Slf4j
public class WordBuffer {
    
    private final Map<String, WordEntry> wordMap = new ConcurrentHashMap<>();
    private final List<WordEntry> currentSegmentWords = new ArrayList<>();
    private final Object lock = new Object();
    private double lastFinalEndTime = 0.0;
    
    /**
     * Add or update words from a new transcription result.
     * Handles deduplication and merging of overlapping words.
     * Returns the current segment transcript (since last final).
     */
    public SegmentResult updateWords(List<WordInfo> newWords, boolean isFinal) {
        synchronized (lock) {
            // If this is a final result, first clean up old words
            if (isFinal) {
                cleanupOldWords();
            }
            
            for (WordInfo newWord : newWords) {
                // Only process words that are after the last final end time
                if (newWord.getEnd() <= lastFinalEndTime) {
                    continue;
                }
                
                String key = generateWordKey(newWord);
                WordEntry existing = wordMap.get(key);
                
                if (existing != null) {
                    // Update existing word if new version has better confidence or is final
                    if (isFinal || newWord.getConfidence() > existing.getConfidence()) {
                        existing.update(newWord, isFinal);
                        log.trace("Updated word: {} at {}-{} (confidence: {})", 
                            newWord.getWord(), newWord.getStart(), newWord.getEnd(), newWord.getConfidence());
                    }
                } else {
                    // Add new word
                    WordEntry entry = new WordEntry(newWord, isFinal);
                    wordMap.put(key, entry);
                    insertWordInOrder(entry);
                    log.trace("Added new word: {} at {}-{}", 
                        newWord.getWord(), newWord.getStart(), newWord.getEnd());
                }
            }
            
            // Clean up overlapping words if this is a final result
            if (isFinal) {
                cleanupOverlaps();
                // Update final state
                String currentSegmentText = getCurrentSegmentTranscript();
                if (!currentSegmentWords.isEmpty()) {
                    lastFinalEndTime = currentSegmentWords.stream()
                            .mapToDouble(WordEntry::getEnd)
                            .max().orElse(lastFinalEndTime);
                }
                return new SegmentResult(currentSegmentText, getOverallConfidence(), true, getCurrentWords());
            } else {
                // Return interim result
                String currentSegmentText = getCurrentSegmentTranscript();
                return new SegmentResult(currentSegmentText, getOverallConfidence(), false, getCurrentWords());
            }
        }
    }
    
    /**
     * Get the current segment transcript (since last final).
     */
    public String getCurrentSegmentTranscript() {
        synchronized (lock) {
            return currentSegmentWords.stream()
                    .map(w -> w.getPunctuatedWord() != null ? w.getPunctuatedWord() : w.getWord())
                    .collect(Collectors.joining(" "));
        }
    }
    
    /**
     * Get the current segment transcript using smart punctuation spacing.
     */
    public String getCurrentSegmentPunctuatedTranscript() {
        synchronized (lock) {
            StringBuilder sb = new StringBuilder();
            String lastPunctuation = "";
            
            for (WordEntry word : currentSegmentWords) {
                String punctuated = word.getPunctuatedWord() != null ? word.getPunctuatedWord() : word.getWord();
                
                // Smart spacing: no space after opening quotes/brackets or before closing punctuation
                if (!sb.isEmpty() && !lastPunctuation.matches("[(\\[\"'«]") 
                        && !punctuated.matches("^[.,;:!?)\\]\"'»].*")) {
                    sb.append(" ");
                }
                
                sb.append(punctuated);
                
                // Track last character for smart spacing
                if (!punctuated.isEmpty()) {
                    lastPunctuation = punctuated.substring(punctuated.length() - 1);
                }
            }
            
            return sb.toString();
        }
    }
    
    /**
     * Get current segment words in chronological order.
     */
    public List<WordInfo> getCurrentWords() {
        synchronized (lock) {
            return currentSegmentWords.stream()
                    .map(WordEntry::toWordInfo)
                    .collect(Collectors.toList());
        }
    }
    
    /**
     * Get the overall confidence of the current segment.
     */
    public double getOverallConfidence() {
        synchronized (lock) {
            if (currentSegmentWords.isEmpty()) return 0.0;
            
            double sum = currentSegmentWords.stream()
                    .mapToDouble(WordEntry::getConfidence)
                    .sum();
            return sum / currentSegmentWords.size();
        }
    }
    
    /**
     * Clear all words from the buffer.
     */
    public void clear() {
        synchronized (lock) {
            wordMap.clear();
            currentSegmentWords.clear();
            lastFinalEndTime = 0.0;
        }
    }
    
    /**
     * Clean up words that are before the last final result.
     */
    private void cleanupOldWords() {
        // Remove words that ended before the last final time
        currentSegmentWords.removeIf(word -> word.getEnd() <= lastFinalEndTime);
        wordMap.entrySet().removeIf(entry -> entry.getValue().getEnd() <= lastFinalEndTime);
    }
    
    private String generateWordKey(WordInfo word) {
        // Key based on approximate time position and word text
        // This allows for small time adjustments while identifying the same word
        long timeSlot = Math.round(word.getStart() * 10); // 100ms slots
        return timeSlot + "_" + word.getWord().toLowerCase();
    }
    
    private void insertWordInOrder(WordEntry entry) {
        // Binary search to find insertion point
        int index = Collections.binarySearch(currentSegmentWords, entry, 
            Comparator.comparing(WordEntry::getStart));
        
        if (index < 0) {
            index = -index - 1;
        }
        currentSegmentWords.add(index, entry);
    }
    
    private void cleanupOverlaps() {
        // Remove words that significantly overlap with higher confidence words
        List<WordEntry> toRemove = new ArrayList<>();
        
        for (int i = 0; i < currentSegmentWords.size() - 1; i++) {
            WordEntry current = currentSegmentWords.get(i);
            WordEntry next = currentSegmentWords.get(i + 1);
            
            // Check for significant overlap
            if (current.getEnd() > next.getStart() + 0.1) { // 100ms overlap threshold
                // Keep the word with higher confidence or the final one
                if (next.isFinal() && !current.isFinal()) {
                    toRemove.add(current);
                } else if (current.isFinal() && !next.isFinal()) {
                    toRemove.add(next);
                } else if (next.getConfidence() > current.getConfidence()) {
                    toRemove.add(current);
                } else {
                    toRemove.add(next);
                }
            }
        }
        
        // Remove overlapping words
        for (WordEntry entry : toRemove) {
            currentSegmentWords.remove(entry);
            wordMap.values().removeIf(e -> e.equals(entry));
        }
    }
    
    @Data
    private static class WordEntry {
        private String word;
        private String punctuatedWord;
        private double start;
        private double end;
        private double confidence;
        private String language;
        private boolean isFinal;
        private long lastUpdated;
        
        WordEntry(WordInfo info, boolean isFinal) {
            update(info, isFinal);
        }
        
        void update(WordInfo info, boolean isFinal) {
            this.word = info.getWord();
            this.punctuatedWord = info.getPunctuatedWord();
            this.start = info.getStart();
            this.end = info.getEnd();
            this.confidence = info.getConfidence();
            this.language = info.getLanguage();
            this.isFinal = this.isFinal || isFinal;
            this.lastUpdated = System.currentTimeMillis();
        }
        
        WordInfo toWordInfo() {
            return WordInfo.builder()
                    .word(word)
                    .punctuatedWord(punctuatedWord)
                    .start(start)
                    .end(end)
                    .confidence(confidence)
                    .language(language)
                    .build();
        }
    }
}