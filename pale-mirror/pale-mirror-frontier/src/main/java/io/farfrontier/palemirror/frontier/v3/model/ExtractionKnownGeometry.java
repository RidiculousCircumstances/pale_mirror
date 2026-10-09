package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** The extraction owner contributes only retained geometry/depletion, not worker-specific obstacle policy. */
final class ExtractionKnownGeometry implements KnownSiteGeometry.Provider {
    @Override public KnownSiteGeometry.Owner owner() { return KnownSiteGeometry.Owner.EXTRACTION; }
    @Override public Object version(FrontierWorldState state) { return state.extractionSites().deposits(); }
    @Override public KnownSiteGeometry.View project(FrontierWorldState state) {
        var supports = new LinkedHashMap<TerrainColumn, SurfaceAnchor>();
        var obstacles = new HashSet<BlockPosition>();
        for (var deposit : state.extractionSites().deposits().values()) {
            var layout = deposit.site().layout();
            var floorBlocks = new HashSet<BlockPosition>();
            for (var surface : layout.accessSurfaces()) {
                floorBlocks.add(surface.support());
                if (supports.putIfAbsent(new TerrainColumn(surface.x(), surface.z()), surface) != null)
                    throw new IllegalArgumentException("extraction sites overlap their access geometry");
                var observed = deposit.geometry().get(surface.support());
                // A removed/replaced authored floor is not permission to reuse the bootstrap
                // datum beneath the pit. Until the exact floor is restored, exclude its stance.
                if (observed != null && !observed.block().equals(layout.fixedBlocks().get(surface.support())))
                    obstacles.add(surface.support().offset(0, 1, 0));
            }
            layout.fixedBlocks().forEach((position, block) -> {
                var observed = deposit.geometry().get(position);
                var actual = observed == null ? block : observed.block();
                if (!actual.kind().equals("minecraft:air") && !floorBlocks.contains(position)) obstacles.add(position);
            });
            for (var cell : layout.cells())
                if (!deposit.cells().get(cell.id()).knownBlock().kind().equals("minecraft:air")) obstacles.add(cell.source());
        }
        return new KnownSiteGeometry.View(supports, obstacles);
    }
}
