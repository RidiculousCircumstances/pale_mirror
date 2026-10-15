package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.util.*;

/** Current natural contact derived from the source owner's facts, not historical floor permission. */
final class ExtractionWorkingSurfaces {
    private final FrontierWorldState state;
    private final ExtractionDeposit deposit;
    private final Map<BlockPosition, ExtractionLayout.Cell> sources;

    ExtractionWorkingSurfaces(FrontierWorldState state, ExtractionDeposit deposit) {
        this.state = state; this.deposit = deposit;
        sources = deposit.site().layout().cells().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                ExtractionLayout.Cell::source, cell -> cell));
    }
    Optional<BlockExtraction.Block> at(BlockPosition position) {
        var cell = sources.get(position);
        if (cell != null) return Optional.of(deposit.cells().get(cell.id()).knownBlock());
        var layout = deposit.site().layout();
        var observed = deposit.geometry().get(position);
        if (observed != null) return Optional.of(observed.block());
        var fixed = layout.fixedBlocks().get(position);
        if (fixed != null) return Optional.of(fixed);
        if (state.extractionSites().geologicalExclusions().positions().contains(position)) return Optional.empty();
        var natural = layout.terrainSupports().get(position);
        return natural == null ? ExtractionTerrainFacts.at(state.bootstrap(), position).map(sample -> sample.block())
                : Optional.of(natural);
    }
    boolean standable(SurfaceAnchor surface) {
        return at(surface.support()).filter(block -> state.bootstrap().ruleset().extraction().definitionFor(block).isPresent())
                .isPresent()
                && clear(surface.support().offset(0, 1, 0)) && clear(surface.support().offset(0, 2, 0));
    }
    private boolean clear(BlockPosition position) {
        return at(position).filter(block -> io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockClearance
                .emptyCollision(block.kind())).isPresent();
    }
    Map<TerrainColumn, SurfaceAnchor> current() {
        var result = new LinkedHashMap<TerrainColumn, SurfaceAnchor>();
        for (var surface : deposit.site().layout().accessSurfaces()) {
            if (!standable(surface)) continue;
            var column = new TerrainColumn(surface.x(), surface.z());
            result.merge(column, surface, (left, right) -> left.y() >= right.y() ? left : right);
        }
        return Map.copyOf(result);
    }
    /** Navigation retains every independent level; mining front preference is a separate query. */
    List<SurfaceAnchor> allCurrent() {
        return deposit.site().layout().accessSurfaces().stream().filter(surface -> at(surface.support())
                .filter(block -> state.bootstrap().ruleset().extraction().definitionFor(block).isPresent()).isPresent())
                .sorted(Comparator.comparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z)
                        .thenComparingInt(SurfaceAnchor::y)).toList();
    }
}
