package io.farfrontier.palemirror.internal.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualChunk;
import io.farfrontier.palemirror.api.VisualPoint;
import org.junit.jupiter.api.Test;

class RegionalDiscoveryRuntimeTest {
    private static final VisualBounds MINE = new VisualBounds(
            new VisualPoint(-17, 40, 31), new VisualPoint(34, 90, 80));

    @Test
    void authoredMineDiscoveryUsesEveryChunkIntersectingItsHorizontalFootprint() {
        assertTrue(MINE.intersectsChunk(new VisualChunk(-2, 1)));
        assertTrue(MINE.intersectsChunk(new VisualChunk(2, 5)));
        assertTrue(MINE.intersectsChunk(new VisualChunk(0, 3)));
    }

    @Test
    void authoredMineDiscoveryDoesNotExpandBeyondItsFootprintChunks() {
        assertFalse(MINE.intersectsChunk(new VisualChunk(-3, 1)));
        assertFalse(MINE.intersectsChunk(new VisualChunk(3, 5)));
        assertFalse(MINE.intersectsChunk(new VisualChunk(0, 6)));
    }
}
