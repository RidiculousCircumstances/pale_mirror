package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.*;

/** Retained observation overlay, never a second world writer. Unknown entries fence stale bootstrap facts. */
public record NavigationGeometryState(long revision, Map<BlockPosition, NavigationObservation> cells,
        Map<NavigationBlockState, NavigationObservation> definitions) {
    public static final int MAX_CELLS = 262_144;
    public static final int MAX_CHANGE = 512;
    public static final int MAX_DEFINITIONS = 4_096;
    public NavigationGeometryState(long revision, Map<BlockPosition, NavigationObservation> cells) { this(revision, cells, Map.of()); }
    public NavigationGeometryState {
        if (revision < 0 || cells.size() > MAX_CELLS || definitions.size() > MAX_DEFINITIONS) throw new IllegalArgumentException("unbounded navigation knowledge");
        cells = RegionalBlockMap.copyOf(cells);
        definitions = Map.copyOf(definitions);
        definitions.forEach((block, observation) -> {
            if (!observation.dependencies().isEmpty()) throw new IllegalArgumentException("a global block definition cannot depend on a position");
            var cell = observation.cell();
            if (cell.isPresent() && !cell.orElseThrow().observedState().filter(block::equals).isPresent())
                throw new IllegalArgumentException("geometry definition differs from its block declaration");
        });
    }
    public static NavigationGeometryState empty() { return new NavigationGeometryState(0, Map.of()); }
    public void requireWithin(io.farfrontier.palemirror.frontier.v3.model.WorldBounds bounds) {
        var indexed = RegionalBlockMap.copyOf(cells);
        for (var region : indexed.regions()) {
            int x = region.x() << 4, z = region.z() << 4;
            if (bounds.contains(new BlockPosition(x, 0, z)) && bounds.contains(new BlockPosition(x + 15, 0, z + 15))) continue;
            if (indexed.in(region).keySet().stream().anyMatch(position -> !bounds.contains(position)))
                throw new IllegalArgumentException("navigation knowledge crosses world bounds");
        }
    }
    public NavigationGeometryState observe(long expected, Map<BlockPosition, NavigationObservation> observations) {
        return observe(expected, observations, Map.of());
    }
    public NavigationGeometryState observe(long expected, Map<BlockPosition, NavigationObservation> observations,
            Map<NavigationBlockState, NavigationObservation> declarations) {
        if (expected != revision) throw new IllegalArgumentException("stale navigation observation revision");
        if (observations.isEmpty() && declarations.isEmpty() || observations.size() + declarations.size() > MAX_CHANGE)
            throw new IllegalArgumentException("unbounded navigation observation batch");
        observations.forEach((position, observation) -> observation.requireAt(position));
        var next = RegionalBlockMap.copyOf(cells).with(observations);
        var catalog = new HashMap<>(definitions); catalog.putAll(declarations);
        if (next == cells && catalog.equals(definitions)) return this;
        return new NavigationGeometryState(Math.addExact(revision, 1), next, catalog.equals(definitions) ? definitions : catalog);
    }
}
