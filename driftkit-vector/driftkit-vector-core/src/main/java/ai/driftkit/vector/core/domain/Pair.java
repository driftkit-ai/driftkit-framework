package ai.driftkit.vector.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * A simple Pair utility class
 */
@Data
@AllArgsConstructor
public class Pair<L, R> {
    private L left;
    private R right;
}
