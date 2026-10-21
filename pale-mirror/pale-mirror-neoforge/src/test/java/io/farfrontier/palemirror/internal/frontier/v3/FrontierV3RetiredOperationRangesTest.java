package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3RetiredOperationRangesTest {
    @Test void completedHistoryIsBoundedByPendingHolesRatherThanWorldAgeOrNativeBodies() {
        var stored = new HashMap<Long, Long>();
        var ranges = new FrontierV3RetiredOperationRanges(stored, List.of(), 1);
        for (long i = 1; i <= 10_000; i++) if (i != 2 && i != 49) ranges.add(i);
        assertEquals(3, stored.size()); assertFalse(ranges.contains(2)); assertFalse(ranges.contains(49));
        var recovered = new FrontierV3RetiredOperationRanges(stored, List.of(2L, 49L), 10_001);
        recovered.add(49); recovered.add(2);
        assertEquals(Map.of(1L, 10_000L), stored);
        assertThrows(IllegalArgumentException.class, () -> recovered.add(2));
        new FrontierV3RetiredOperationRanges(stored, List.of(), 10_001);
    }
    @Test void missingOverlappingAndFutureHistoryCannotAuthorizePruning() {
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3RetiredOperationRanges(Map.of(1L, 4L), List.of(3L), 5));
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3RetiredOperationRanges(Map.of(1L, 4L), List.of(), 6));
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3RetiredOperationRanges(Map.of(1L, 6L), List.of(), 6));
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3RetiredOperationRanges(Map.of(1L, 2L, 3L, 4L), List.of(), 5));
    }
}
