package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import java.util.IdentityHashMap;

/** Reuses only a proven immutable layout/bounds check; inventory and work are never cached. */
final class ExtractionLandValidation {
    private static WorldBounds bounds;
    private static final IdentityHashMap<ExtractionLayout, Boolean> VALIDATED = new IdentityHashMap<>();
    private ExtractionLandValidation() { }
    static synchronized void require(ExtractionLayout layout, WorldBounds currentBounds) {
        if (bounds != currentBounds) { VALIDATED.clear(); bounds = currentBounds; }
        if (VALIDATED.containsKey(layout)) return;
        if (layout.cells().stream().anyMatch(cell -> !currentBounds.contains(cell.source()))
                || layout.fixedBlocks().keySet().stream().anyMatch(position -> !currentBounds.contains(position)))
            throw new IllegalArgumentException("extraction layout leaves declared world bounds");
        if (VALIDATED.size() == 64) VALIDATED.clear();
        VALIDATED.put(layout, Boolean.TRUE);
    }
}
