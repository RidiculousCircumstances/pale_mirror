package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockMap;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Shared derived current-cell index; an unrelated change preserves each chunk's immutable version. */
final class FrontierV3WorksiteCurrentIndex {
    private List<RegionalBlockMap<WorksiteBlock>> sources = List.of();
    private Map<Long, List<WorksiteBlock>> chunks = Map.of();
    Map<Long, List<WorksiteBlock>> update(List<RegionalBlockMap<WorksiteBlock>> current) {
        var changed = new HashSet<Region>();
        for (int i = 0; i < Math.max(current.size(), sources.size()); i++) {
            var before = i < sources.size() ? sources.get(i) : RegionalBlockMap.<WorksiteBlock>copyOf(Map.of());
            var after = i < current.size() ? current.get(i) : RegionalBlockMap.<WorksiteBlock>copyOf(Map.of());
            changed.addAll(after.changesFrom(before));
        }
        if (changed.isEmpty()) { sources = current; return chunks; }
        var next = new TreeMap<>(chunks);
        for (var region : changed) {
            var points = new HashMap<BlockPosition, WorksiteBlock>();
            for (var source : current) source.in(region).forEach((position, block) -> {
                if (points.putIfAbsent(position, block) != null) throw new IllegalStateException("overlapping current worksites");
            });
            long key = ChunkPos.asLong(region.x(), region.z());
            if (points.isEmpty()) next.remove(key);
            else next.put(key, points.values().stream().sorted(Comparator
                    .comparingInt((WorksiteBlock b) -> b.position().x()).thenComparingInt(b -> b.position().z())
                    .thenComparingInt(b -> b.position().y())).toList());
        }
        sources = current; return chunks = Collections.unmodifiableMap(next);
    }
}
