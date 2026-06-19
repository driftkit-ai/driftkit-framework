package ai.driftkit.clients.gemini.client;

import ai.driftkit.clients.gemini.client.GeminiModelClient.ContentLoopDetector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the streamed-text repetition-loop detector (guards against
 * Gemini think-model degeneration that otherwise burns minutes / 65k tokens).
 *
 * Invariants under test:
 *  - degeneration loops (exact-block, sub-window, and chunked-delta) are detected;
 *  - legitimate non-repeating output never trips the detector;
 *  - empty/null deltas are inert;
 *  - history trimming neither loses an in-progress loop nor false-triggers.
 */
class ContentLoopDetectorTest {

    private static final int CHUNK = ContentLoopDetector.CHUNK;          // 50
    private static final int THRESHOLD = ContentLoopDetector.THRESHOLD;  // 10

    @Test
    void emptyAndNullDeltasAreInert() {
        ContentLoopDetector detector = new ContentLoopDetector();
        assertFalse(detector.append(null));
        assertFalse(detector.append(""));
        // still inert after no-op deltas: a short legitimate delta does not trip
        assertFalse(detector.append("hello world"));
    }

    @Test
    void exactBlockRepetitionIsDetected() {
        ContentLoopDetector detector = new ContentLoopDetector();
        String block = "X".repeat(CHUNK); // a full-window block the model repeats verbatim

        boolean detected = false;
        int iterations = 0;
        // THRESHOLD repeats of the same window must trip it; allow a small margin
        for (int i = 0; i < THRESHOLD + 2 && !detected; i++) {
            detected = detector.append(block);
            iterations++;
        }
        assertTrue(detected, "verbatim block repetition must be detected");
        assertTrue(iterations <= THRESHOLD + 1,
                "should trip within ~THRESHOLD repeats, took " + iterations);
    }

    @Test
    void subWindowRepetitionIsDetected() {
        // The model loops a tiny fragment ("ha") — the same 50-char window recurs
        // every 2 chars, so the threshold is reached quickly.
        ContentLoopDetector detector = new ContentLoopDetector();

        boolean detected = false;
        for (int i = 0; i < 2000 && !detected; i++) {
            detected = detector.append("ha");
        }
        assertTrue(detected, "repeating short fragment must be detected as a loop");
    }

    @Test
    void detectionWorksAcrossArbitraryDeltaBoundaries() {
        // Real SSE case: the repeating text arrives split into uneven deltas.
        // Detection must not depend on delta alignment with the window size.
        ContentLoopDetector detector = new ContentLoopDetector();
        String unit = "The same sentence over and over. "; // 33 chars, != CHUNK
        String loop = unit.repeat(40);

        boolean detected = false;
        int pos = 0;
        int[] deltaSizes = {1, 7, 50, 3, 100, 13}; // irregular streaming chunks
        int k = 0;
        while (pos < loop.length() && !detected) {
            int size = deltaSizes[k++ % deltaSizes.length];
            int end = Math.min(pos + size, loop.length());
            detected = detector.append(loop.substring(pos, end));
            pos = end;
        }
        assertTrue(detected, "loop must be detected regardless of delta boundaries");
    }

    @Test
    void legitimateNonRepeatingOutputDoesNotTrigger() {
        // A long, varied response: every token is distinct, so no 50-char window
        // repeats THRESHOLD times. Length far exceeds the trim trigger (6000).
        ContentLoopDetector detector = new ContentLoopDetector();
        boolean tripped = false;
        for (int i = 0; i < 2000 && !tripped; i++) {
            tripped = detector.append(String.format("token-%05d ", i)); // unique each time
        }
        assertFalse(tripped, "non-repeating output must never be flagged as a loop");
    }

    @Test
    void structuredJsonLikeOutputDoesNotFalseTrigger() {
        // Structured output has repetitive punctuation but distinct values — must pass.
        ContentLoopDetector detector = new ContentLoopDetector();
        StringBuilder json = new StringBuilder("{\"items\":[");
        for (int i = 0; i < 300; i++) {
            json.append("{\"id\":").append(i).append(",\"name\":\"item_").append(i).append("\"},");
        }
        json.append("]}");
        assertFalse(detector.append(json.toString()),
                "legitimate structured output must not be flagged");
    }

    @Test
    void historyTrimDoesNotLoseAnOngoingLoop() {
        // Feed a long non-repeating prefix (crossing the trim threshold), THEN start
        // looping — detection must still fire after the trim/reset.
        ContentLoopDetector detector = new ContentLoopDetector();
        for (int i = 0; i < 1000; i++) {
            assertFalse(detector.append(String.format("uniq-%05d ", i)));
        }
        String block = "Y".repeat(CHUNK);
        boolean detected = false;
        for (int i = 0; i < THRESHOLD + 2 && !detected; i++) {
            detected = detector.append(block);
        }
        assertTrue(detected, "a loop starting after a history trim must still be detected");
    }
}
