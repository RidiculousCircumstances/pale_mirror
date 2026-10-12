package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit;

/** Read-only preparation/opening evidence; an admitted support is not an opened landing. */
public record ExtractionTerraceView(int depthLimit, int plannedDepth, int openDepth, long preparationBlocks,
                                    java.util.Optional<SurfaceAnchor> lowerLanding) {
    public static ExtractionTerraceView read(FrontierWorldState state, ExtractionDeposit deposit) {
        var layout = deposit.site().layout();
        int root = layout.storagePort().y();
        int limit = state.bootstrap().ruleset().extraction().terraces().map(value -> value.maximumDepth()).orElse(0);
        var geometry = KnownPedestrianRouteKnowledge.forFrontier(state).geometry();
        int planned = layout.accessSurfaces().stream().mapToInt(surface -> Math.max(0, root - surface.y())).max().orElse(0);
        var remainingSources = new java.util.HashSet<BlockPosition>();
        for (var cell : layout.cells()) if (!deposit.cells().get(cell.id()).knownBlock().kind().equals("minecraft:air"))
            remainingSources.add(cell.source());
        var preparation = new java.util.HashSet<BlockPosition>();
        var openSurfaces = new ExtractionWorkingSurfaces(state, deposit).current().values().stream()
                .filter(surface -> !geometry.blocked(surface)).collect(java.util.stream.Collectors.toSet());
        for (var surface : layout.accessSurfaces()) {
            // Only body clearance opens a contact. Rock above the head is a legitimate
            // roof, not mandatory surface stripping or evidence that mining is pending.
            for (int y = surface.y() + 1; y <= surface.y() + 2; y++) {
                var point = new BlockPosition(surface.x(), y, surface.z());
                if (remainingSources.contains(point)) preparation.add(point);
            }
        }
        int open = openSurfaces.stream().mapToInt(surface -> Math.max(0, root - surface.y())).max().orElse(0);
        long pending = preparation.size();
        var landing = openSurfaces.stream().filter(surface -> surface.y() < root)
                .min(java.util.Comparator.comparingInt(SurfaceAnchor::y).thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z));
        return new ExtractionTerraceView(limit, planned, open, pending, landing);
    }
}
