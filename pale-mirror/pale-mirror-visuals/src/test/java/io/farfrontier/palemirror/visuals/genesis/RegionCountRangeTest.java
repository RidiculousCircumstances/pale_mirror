package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RegionCountRangeTest {
    @Test void validatesOrderedBounds() {
        RegionCountRange range = new RegionCountRange(3, 5, 6);
        assertEquals(3, range.minimum());
        assertEquals(5, range.target());
        assertEquals(6, range.maximum());
    }

    @Test void rejectsInvertedBounds() {
        assertThrows(IllegalArgumentException.class, () -> new RegionCountRange(3, 2, 6));
        assertThrows(IllegalArgumentException.class, () -> new RegionCountRange(3, 7, 6));
    }
}
