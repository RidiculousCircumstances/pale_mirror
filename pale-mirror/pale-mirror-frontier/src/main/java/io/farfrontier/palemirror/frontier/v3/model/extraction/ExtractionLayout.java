package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;

/** A finite authored 3D deposit. Dependencies open safe fronts, not a per-actor route cursor. */
public record ExtractionLayout(List<Cell> cells, Map<BlockPosition, BlockExtraction.Block> fixedBlocks,
                               List<SurfaceAnchor> accessSurfaces, SurfaceAnchor entrance,
                               BlockPosition container, SurfaceAnchor storagePort) {
    public static final int MAX_CELLS = 8_192;
    public record Cell(long id, BlockPosition source, SurfaceAnchor workstation,
                       BlockExtraction.Definition definition, Set<Long> prerequisites) {
        public Cell {
            Objects.requireNonNull(source); Objects.requireNonNull(workstation); Objects.requireNonNull(definition);
            prerequisites = Set.copyOf(prerequisites);
            if (id < 1 || prerequisites.size() > 16 || prerequisites.stream().anyMatch(value -> value < 1 || value >= id))
                throw new IllegalArgumentException("extraction dependencies must name earlier declared source cells");
            BlockPosition feet = workstation.support().offset(0, 1, 0);
            if (Math.abs(source.x() - feet.x()) + Math.abs(source.z() - feet.z()) > 1
                    || Math.abs(source.y() - feet.y()) > 1 || source.equals(workstation.support()))
                throw new IllegalArgumentException("extraction source has no adjacent supported work position");
        }
    }
    public ExtractionLayout {
        cells = List.copyOf(cells); fixedBlocks = Map.copyOf(fixedBlocks);
        accessSurfaces = List.copyOf(accessSurfaces);
        Objects.requireNonNull(entrance); Objects.requireNonNull(container); Objects.requireNonNull(storagePort);
        if (cells.isEmpty() || cells.size() > MAX_CELLS || fixedBlocks.size() > MAX_CELLS * 4
                || cells.stream().map(Cell::id).distinct().count() != cells.size()
                || cells.stream().map(Cell::source).distinct().count() != cells.size()
                || accessSurfaces.isEmpty() || accessSurfaces.size() > MAX_CELLS * 4
                || accessSurfaces.stream().map(surface -> new io.farfrontier.palemirror.frontier.v3.model.TerrainColumn(surface.x(), surface.z()))
                    .distinct().count() != accessSurfaces.size())
            throw new IllegalArgumentException("extraction layout requires bounded unique source cells");
        Set<Long> ids = cells.stream().map(Cell::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
        var protectedBlocks = fixedBlocks;
        var sourcesByPosition = cells.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Cell::source, cell -> cell));
        var sources = sourcesByPosition.keySet();
        var surfaces = Set.copyOf(accessSurfaces);
        if (cells.stream().anyMatch(cell -> !ids.containsAll(cell.prerequisites())
                    || protectedBlocks.containsKey(cell.source()) || sources.contains(cell.workstation().support()))
                || sources.contains(container) || !fixedBlocks.containsKey(container)
                || !surfaces.contains(entrance) || !surfaces.contains(storagePort)
                || cells.stream().anyMatch(cell -> !surfaces.contains(cell.workstation()))
                || accessSurfaces.stream().anyMatch(surface -> !protectedBlocks.containsKey(surface.support())
                    || protectedBlocks.get(surface.support()).kind().equals("minecraft:air")))
            throw new IllegalArgumentException("extraction source competes with protected access or storage geometry");
        for (var cell : cells) {
            BlockPosition feet = cell.workstation().support().offset(0, 1, 0);
            for (BlockPosition clearance : List.of(feet, feet.offset(0, 1, 0))) {
                var predecessor = sourcesByPosition.get(clearance);
                if (predecessor != null && (!cell.prerequisites().contains(predecessor.id())
                        || !predecessor.definition().after().kind().equals("minecraft:air")))
                    throw new IllegalArgumentException("extraction front can open before its standing clearance");
                var fixed = protectedBlocks.get(clearance);
                if (fixed != null && !fixed.kind().equals("minecraft:air"))
                    throw new IllegalArgumentException("extraction workstation has a fixed body obstruction");
            }
        }
    }
    public Cell require(long id) {
        return cells.stream().filter(cell -> cell.id() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown extraction cell: " + id));
    }
}
