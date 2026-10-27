package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionLayout;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;

/** Immutable worksite declaration projections shared by land-development readers.
 * Local/foreign permissions stay with their respective consumers. */
final class ExtractionLandProtection {
    record Part(RegionalColumnSet developmentColumns, RegionalColumnSet worksiteColumns,
                RegionalBlockSet fixedBlocks, RegionalBlockSet declaredBlocks) { }
    private static final ImmutableResultView<String, Part> PARTS = new ImmutableResultView<>(SimulationCapacity.MAX_EXTRACTION_SITES * 2);
    static Part of(ExtractionLayout layout) {
        return PARTS.get(List.of(layout), "declaration", () -> {
            var positions = new HashSet<BlockPosition>(layout.fixedBlocks().keySet());
            layout.cells().forEach(cell -> positions.add(cell.source()));
            layout.accessSurfaces().forEach(surface -> positions.add(surface.support()));
            var worksite = RegionalColumnSet.fromPositions(positions);
            positions.addAll(layout.terrainSupports().keySet());
            var development = RegionalColumnSet.fromPositions(positions);
            var declared = new HashSet<BlockPosition>(layout.fixedBlocks().keySet());
            layout.cells().forEach(cell -> declared.add(cell.source())); declared.addAll(layout.terrainSupports().keySet());
            return new Part(development, worksite, RegionalBlockSet.copyOf(layout.fixedBlocks().keySet()), RegionalBlockSet.copyOf(declared));
        });
    }
    private ExtractionLandProtection() { }
}
