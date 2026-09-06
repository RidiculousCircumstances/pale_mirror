package io.farfrontier.palemirror.internal.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceGrayboxLabelHeightIndexTest {
    @Test
    void indexesTheHighestOverlappingClaimWithoutChangingTheGroundFallback() {
        SourceGrayboxLabelHeightIndex index = SourceGrayboxLabelHeightIndex.from(List.of(
                new SourceGrayboxLabelHeightIndex.Footprint(-20, 64, 10, 4, 4, 1),
                new SourceGrayboxLabelHeightIndex.Footprint(-19, 66, 11, 2, 2, 5)));

        assertEquals(ReferenceGrayboxLayout.GROUND_Y + 3, index.baseline(-20, 10, 3),
                "a one-block source claim keeps the board just above the local roof");
        assertEquals(73, index.baseline(-19, 11, 3),
                "a collocated taller source claim, not iteration order, decides the legal board floor");
        assertEquals(ReferenceGrayboxLayout.GROUND_Y + 3, index.baseline(-40, 10, 3),
                "a clear or out-of-footprint column retains the ordinary ground-level board floor");
    }

    @Test
    void preservesAHighRoofWithoutAnArbitraryByteRangeLimit() {
        SourceGrayboxLabelHeightIndex index = SourceGrayboxLabelHeightIndex.from(List.of(
                new SourceGrayboxLabelHeightIndex.Footprint(0, ReferenceGrayboxLayout.GROUND_Y + 200, 0, 1, 1, 1)));

        assertEquals(ReferenceGrayboxLayout.GROUND_Y + 203, index.baseline(0, 0, 3));
    }

    @Test
    void rejectsAnInvalidFootprintBeforeItCanSilentlyCorruptLabelPlacement() {
        assertThrows(IllegalArgumentException.class,
                () -> new SourceGrayboxLabelHeightIndex.Footprint(0, 64, 0, 0, 1, 1));
    }
}
