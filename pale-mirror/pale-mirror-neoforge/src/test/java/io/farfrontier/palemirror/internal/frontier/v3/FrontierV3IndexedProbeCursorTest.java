package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3IndexedProbeCursorTest {
    @Test void boundedReadsEventuallyVisitEveryAvailableCellWithoutTouchingUnavailableSlices() {
        var index = Map.of(1L, List.of(1, 2, 3), 2L, List.of(4, 5), 3L, List.of(6));
        var cursor = new FrontierV3IndexedProbeCursor<Integer>();
        var seen = new HashSet<Integer>();
        for (int turn = 0; turn < 4; turn++) {
            var reads = new ArrayList<Integer>();
            cursor.probes(index, key -> key != 3L, 2).forEach(reads::add);
            assertEquals(2, reads.size()); seen.addAll(reads);
        }
        assertEquals(Set.of(1, 2, 3, 4, 5), seen);
    }
    @Test void writeBudgetYieldDoesNotSkipTheUnprocessedRemainder() {
        var index = Map.of(1L, List.of(1, 2, 3, 4));
        var cursor = new FrontierV3IndexedProbeCursor<Integer>();
        assertEquals(1, cursor.probes(index, key -> true, 4).iterator().next());
        var rest = new ArrayList<Integer>(); cursor.probes(index, key -> true, 3).forEach(rest::add);
        assertEquals(List.of(2, 3, 4), rest);
        assertFalse(cursor.probes(index, key -> false, 4).iterator().hasNext());
        assertEquals(1, cursor.probes(index, key -> true, 1).iterator().next());
    }
    @Test void replacementOfTheImmutableIndexStartsASeparateReadCycle() {
        var cursor = new FrontierV3IndexedProbeCursor<Integer>();
        var old = Map.of(1L, List.of(1, 2));
        assertEquals(1, cursor.probes(old, key -> true, 1).iterator().next());
        var next = Map.of(1L, List.of(3));
        assertEquals(3, cursor.probes(next, key -> true, 1).iterator().next());
    }
}
