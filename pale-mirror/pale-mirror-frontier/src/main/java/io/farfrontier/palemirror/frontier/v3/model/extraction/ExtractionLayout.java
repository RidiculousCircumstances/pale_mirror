package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;

/** A finite authored 3D deposit. Dependencies open safe fronts, not a per-actor route cursor. */
public final class ExtractionLayout {
    private final List<Cell> cells;
    private final Map<BlockPosition, BlockExtraction.Block> fixedBlocks;
    private final List<SurfaceAnchor> accessSurfaces;
    private final SurfaceAnchor entrance;
    private final BlockPosition container;
    private final SurfaceAnchor storagePort;
    private final Map<BlockPosition, Long> infrastructureIds;
    // Derived once from this immutable declaration; not durable state or a history cache.
    private final Map<Long, Cell> cellsById;
    public static final int MAX_CELLS = 8_192;
    public ExtractionLayout(List<Cell> cells, Map<BlockPosition, BlockExtraction.Block> fixedBlocks,
            List<SurfaceAnchor> accessSurfaces, SurfaceAnchor entrance, BlockPosition container, SurfaceAnchor storagePort) {
        this(cells, fixedBlocks, accessSurfaces, entrance, container, storagePort, initialInfrastructureIds(fixedBlocks));
    }
    private static Map<BlockPosition, Long> initialInfrastructureIds(Map<BlockPosition, BlockExtraction.Block> blocks) {
        var ids = new LinkedHashMap<BlockPosition, Long>();
        for (var position : blocks.keySet().stream().sorted(Comparator.comparingInt(BlockPosition::x)
                .thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z)).toList()) ids.put(position, ids.size() + 1L);
        return Map.copyOf(ids);
    }
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
    public ExtractionLayout(List<Cell> cells, Map<BlockPosition, BlockExtraction.Block> fixedBlocks,
                            List<SurfaceAnchor> accessSurfaces, SurfaceAnchor entrance,
                            BlockPosition container, SurfaceAnchor storagePort,
                            Map<BlockPosition, Long> infrastructureIds) {
        cells = List.copyOf(cells); fixedBlocks = Map.copyOf(fixedBlocks);
        infrastructureIds = Map.copyOf(infrastructureIds);
        accessSurfaces = List.copyOf(accessSurfaces);
        Objects.requireNonNull(entrance); Objects.requireNonNull(container); Objects.requireNonNull(storagePort);
        if (!infrastructureIds.keySet().equals(fixedBlocks.keySet())
                || infrastructureIds.values().stream().distinct().count() != infrastructureIds.size()
                || infrastructureIds.values().stream().anyMatch(id -> id < 1))
            throw new IllegalArgumentException("infrastructure requires exact stable unique declaration identities");
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
        this.cells = cells; this.fixedBlocks = fixedBlocks; this.accessSurfaces = accessSurfaces;
        this.entrance = entrance; this.container = container; this.storagePort = storagePort;
        this.infrastructureIds = infrastructureIds;
        this.cellsById = cells.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Cell::id, cell -> cell));
    }
    public Cell require(long id) {
        var cell = cellsById.get(id);
        if (cell == null) throw new IllegalArgumentException("unknown extraction cell: " + id);
        return cell;
    }
    public List<Cell> cells() { return cells; }
    public Map<BlockPosition, BlockExtraction.Block> fixedBlocks() { return fixedBlocks; }
    public List<SurfaceAnchor> accessSurfaces() { return accessSurfaces; }
    public SurfaceAnchor entrance() { return entrance; }
    public BlockPosition container() { return container; }
    public SurfaceAnchor storagePort() { return storagePort; }
    public Map<BlockPosition, Long> infrastructureIds() { return infrastructureIds; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof ExtractionLayout layout && cells.equals(layout.cells)
                && fixedBlocks.equals(layout.fixedBlocks) && accessSurfaces.equals(layout.accessSurfaces)
                && entrance.equals(layout.entrance) && container.equals(layout.container)
                && storagePort.equals(layout.storagePort) && infrastructureIds.equals(layout.infrastructureIds);
    }
    @Override public int hashCode() {
        return Objects.hash(cells, fixedBlocks, accessSurfaces, entrance, container, storagePort, infrastructureIds);
    }
    /** Append-only declaration admission: no old source, station, infrastructure or port may change. */
    public void requireExtensionOf(ExtractionLayout prior) {
        if (!entrance.equals(prior.entrance()) || !container.equals(prior.container()) || !storagePort.equals(prior.storagePort())
                || cells.size() <= prior.cells().size() || !cells.subList(0, prior.cells().size()).equals(prior.cells())
                || !accessSurfaces.containsAll(prior.accessSurfaces())
                || !fixedBlocks.entrySet().containsAll(prior.fixedBlocks().entrySet())
                || !infrastructureIds.entrySet().containsAll(prior.infrastructureIds().entrySet()))
            throw new IllegalArgumentException("development changed an existing declaration");
    }
}
