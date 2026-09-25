package io.farfrontier.palemirror.frontier.v3.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Last-known owned field surface. An unknown or foreign block cannot be
 * represented as a known PM block; it needs separate exact observation and
 * must never be overwritten merely because this surface lacks a value.
 * Work, yield, inventory and growth authority belong to the canonical cycle.
 */
public final class ResourceFieldPhysicalSurface {
    public record Condition(ResourceFieldCycle.Soil soil, ResourceFieldCycle.Crop crop, int growthStage) {
        public Condition {
            new ResourceFieldCycle.CellState(soil, crop, growthStage, false, false);
            if (soil == ResourceFieldCycle.Soil.UNKNOWN || soil == ResourceFieldCycle.Soil.OBSTRUCTED
                    || crop == ResourceFieldCycle.Crop.UNKNOWN || crop == ResourceFieldCycle.Crop.OBSTRUCTED)
                throw new IllegalArgumentException("foreign or unobserved field blocks are not owned surface conditions");
        }
        public static Condition of(ResourceFieldCycle.CellState cell) {
            return new Condition(cell.soil(), cell.crop(), cell.growthStage());
        }
    }

    private final ResourceFieldLayout layout;
    private final Map<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, Condition>> byChunk;

    private ResourceFieldPhysicalSurface(ResourceFieldLayout layout,
                                         Map<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, Condition>> groups,
                                         boolean validateCells) {
        this.layout = Objects.requireNonNull(layout, "physical field layout");
        var copied = new LinkedHashMap<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, Condition>>();
        groups.forEach((chunk, cells) -> copied.put(Objects.requireNonNull(chunk),
                validateCells ? Map.copyOf(cells) : Objects.requireNonNull(cells)));
        this.byChunk = Map.copyOf(copied);
        if (validateCells) {
            int count = 0;
            for (ResourceFieldLayout.Cell cell : layout.cells()) {
                Condition condition = this.byChunk.getOrDefault(chunkOf(cell), Map.of()).get(cell.id());
                if (condition == null) throw new IllegalArgumentException("physical field claim omits a layout cell");
                count++;
            }
            if (this.byChunk.values().stream().mapToInt(Map::size).sum() != count)
                throw new IllegalArgumentException("physical field claim contains foreign cells");
        }
    }

    public static ResourceFieldPhysicalSurface fromCycle(ResourceFieldCycle cycle) {
        Objects.requireNonNull(cycle, "canonical field cycle");
        var groups = new LinkedHashMap<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, Condition>>();
        for (ResourceFieldLayout.Cell cell : cycle.layout().cells())
            groups.computeIfAbsent(chunkOf(cell), ignored -> new LinkedHashMap<>())
                    .put(cell.id(), Condition.of(cycle.cell(cell.id())));
        return new ResourceFieldPhysicalSurface(cycle.layout(), groups, true);
    }

    public static ResourceFieldPhysicalSurface restore(ResourceFieldLayout layout,
                                                        Map<ResourceFieldLayout.CellId, Condition> conditions) {
        Objects.requireNonNull(conditions, "physical field cells");
        if (conditions.size() != layout.cells().size()) throw new IllegalArgumentException("physical field cell count differs from layout");
        var groups = new LinkedHashMap<ResourceFieldLayout.ChunkColumn, Map<ResourceFieldLayout.CellId, Condition>>();
        for (ResourceFieldLayout.Cell cell : layout.cells()) {
            Condition condition = conditions.get(cell.id());
            if (condition == null) throw new IllegalArgumentException("physical field claim omits a layout cell");
            groups.computeIfAbsent(chunkOf(cell), ignored -> new LinkedHashMap<>()).put(cell.id(), condition);
        }
        return new ResourceFieldPhysicalSurface(layout, groups, true);
    }

    public ResourceFieldLayout layout() { return layout; }
    public Condition cell(ResourceFieldLayout.CellId id) {
        ResourceFieldLayout.Cell geometry = layout.requireCell(id);
        return byChunk.get(chunkOf(geometry)).get(id);
    }
    public List<ResourceFieldLayout.Cell> cellsIn(ResourceFieldLayout.ChunkColumn chunk) {
        return layout.cellsIn(chunk);
    }
    public ResourceFieldPhysicalSurface withCell(ResourceFieldLayout.CellId id, Condition next) {
        Objects.requireNonNull(next, "next physical condition");
        if (cell(id).equals(next)) return this;
        ResourceFieldLayout.ChunkColumn chunk = chunkOf(layout.requireCell(id));
        var groups = new LinkedHashMap<>(byChunk);
        var cells = new LinkedHashMap<>(groups.get(chunk));
        cells.put(id, next);
        groups.put(chunk, Map.copyOf(cells));
        return new ResourceFieldPhysicalSurface(layout, groups, false);
    }
    public boolean matches(ResourceFieldCycle cycle) {
        if (!layout.equals(cycle.layout())) return false;
        for (ResourceFieldLayout.Cell cell : layout.cells())
            if (!cell(cell.id()).equals(Condition.of(cycle.cell(cell.id())))) return false;
        return true;
    }

    private static ResourceFieldLayout.ChunkColumn chunkOf(ResourceFieldLayout.Cell cell) {
        return new ResourceFieldLayout.ChunkColumn(Math.floorDiv(cell.crop().x(), 16), Math.floorDiv(cell.crop().z(), 16));
    }

    @Override public boolean equals(Object other) {
        return other instanceof ResourceFieldPhysicalSurface surface
                && layout.equals(surface.layout) && byChunk.equals(surface.byChunk);
    }
    @Override public int hashCode() { return Objects.hash(layout, byChunk); }
}
