package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Composes immutable per-region facts without enumerating unrelated cells. A requested region
 * checks competing ownership before returning any facts; unchanged sources retain their identity. */
public final class WorksiteRegionIndex {
    private List<Map<Region, List<WorksiteBlock>>> sources = List.of();
    private Map<Region, List<WorksiteBlock>> regions = Map.of();
    public Map<Region, List<WorksiteBlock>> update(List<Map<Region, List<WorksiteBlock>>> current) {
        var changed = new HashSet<Region>();
        for (int i = 0; i < Math.max(current.size(), sources.size()); i++) {
            var before = i < sources.size() ? sources.get(i) : Map.<Region, List<WorksiteBlock>>of();
            var after = i < current.size() ? current.get(i) : Map.<Region, List<WorksiteBlock>>of();
            if (before == after) continue;
            before.forEach((region, cells) -> { if (after.get(region) != cells) changed.add(region); });
            after.forEach((region, cells) -> { if (before.get(region) != cells) changed.add(region); });
        }
        if (changed.isEmpty()) { sources = List.copyOf(current); return regions; }
        var next = new HashMap<>(regions);
        for (var region : changed) {
            var parts = current.stream().map(source -> source.get(region)).filter(Objects::nonNull).toList();
            if (parts.isEmpty()) next.remove(region);
            else if (parts.size() == 1) next.put(region, parts.getFirst());
            else next.put(region, new DeferredImmutableList<>(() -> merge(parts)));
        }
        sources = List.copyOf(current); return regions = Collections.unmodifiableMap(next);
    }
    private static List<WorksiteBlock> merge(List<List<WorksiteBlock>> parts) {
        var cells = new HashMap<BlockPosition, WorksiteBlock>();
        for (var part : parts) for (var cell : part)
            if (cells.putIfAbsent(cell.position(), cell) != null) throw new IllegalStateException("overlapping worksite declarations");
        return ordered(cells.values());
    }
    public static List<WorksiteBlock> ordered(Collection<WorksiteBlock> cells) {
        return cells.stream().sorted(Comparator.comparingInt((WorksiteBlock b) -> b.position().x())
                .thenComparingInt(b -> b.position().z()).thenComparingInt(b -> b.position().y())).toList();
    }
}
