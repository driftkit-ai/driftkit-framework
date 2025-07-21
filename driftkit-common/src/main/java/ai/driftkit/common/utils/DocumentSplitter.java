package ai.driftkit.common.utils;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DocumentSplitter {

    public static List<String> splitDocumentIntoShingles(String text, int chunkSize, int overlap) {
        // Validate inputs
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap cannot be negative");
        }
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be less than chunkSize");
        }
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> chunks = new ArrayList<>();

        // Try to split text into sentences first
        List<String> tokens = splitTextIntoSentences(text);

        // If no sentences found, split into words
        if (tokens.isEmpty()) {
            tokens = splitTextIntoWords(text);
        }

        // Now group tokens into chunks
        int index = 0;
        while (index < tokens.size()) {
            int currentSize = 0;
            int startIdx = index;

            List<String> chunkTokens = new ArrayList<>();

            while (index < tokens.size() && currentSize + tokens.get(index).length() <= chunkSize) {
                String token = tokens.get(index);
                chunkTokens.add(token);
                currentSize += token.length() + 1; // +1 for space or punctuation
                index++;
            }

            // If no tokens were added (token is longer than chunkSize), add it partially
            if (chunkTokens.isEmpty()) {
                String token = tokens.get(index);
                String partialToken = token.substring(0, Math.min(chunkSize, token.length()));
                chunks.add(partialToken);
                index++;
            } else {
                // Build the chunk string
                String chunk = String.join(" ", chunkTokens).trim();
                if (!chunk.isEmpty()) {
                    chunks.add(chunk);
                }
            }

            // Handle overlap
            index = startIdx + Math.max(1, chunkTokens.size() - overlap);
        }

        return chunks;
    }

    private static List<String> splitTextIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        BreakIterator sentenceIterator = BreakIterator.getSentenceInstance(Locale.getDefault());
        sentenceIterator.setText(text);
        int start = sentenceIterator.first();
        int end = sentenceIterator.next();

        while (end != BreakIterator.DONE) {
            String sentence = text.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
            start = end;
            end = sentenceIterator.next();
        }

        return sentences;
    }

    private static List<String> splitTextIntoWords(String text) {
        List<String> words = new ArrayList<>();
        BreakIterator wordIterator = BreakIterator.getWordInstance(Locale.getDefault());
        wordIterator.setText(text);
        int start = wordIterator.first();
        int end = wordIterator.next();

        while (end != BreakIterator.DONE) {
            String word = text.substring(start, end).trim();
            if (!word.isEmpty() && Character.isLetterOrDigit(word.codePointAt(0))) {
                words.add(word);
            }
            start = end;
            end = wordIterator.next();
        }

        return words;
    }
}